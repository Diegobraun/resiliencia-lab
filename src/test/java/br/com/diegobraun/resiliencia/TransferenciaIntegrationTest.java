package br.com.diegobraun.resiliencia;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import br.com.diegobraun.resiliencia.celulas.CelulaRouter;
import br.com.diegobraun.resiliencia.chaos.ChaosService;
import br.com.diegobraun.resiliencia.core.CoreSimulado;
import br.com.diegobraun.resiliencia.reconciliacao.ReconciliacaoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.control-plane.cache-arquivo=target/test-config-cache.json")
class TransferenciaIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    ChaosService chaos;

    @Autowired
    CoreSimulado core;

    @Autowired
    CelulaRouter celulas;

    @Autowired
    ReconciliacaoService reconciliacao;

    @Autowired
    ObjectMapper mapper;

    @AfterEach
    void limpar() {
        chaos.resetar();
        core.resetar();
    }

    @Test
    void mesmaChaveIdempotenteDebitaUmaVezSo() throws Exception {
        String chave = UUID.randomUUID().toString();
        ResponseEntity<String> primeira = transferir("cliente-1", chave, "conta-1", "conta-2", "100.00");
        ResponseEntity<String> segunda = transferir("cliente-1", chave, "conta-1", "conta-2", "100");

        assertThat(primeira.getStatusCode().value()).isEqualTo(201);
        assertThat(segunda.getStatusCode().value()).isEqualTo(201);
        assertThat(segunda.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(json(segunda).get("id").asText()).isEqualTo(json(primeira).get("id").asText());
        assertThat(core.saldo("conta-1")).contains(new BigDecimal("999900.00"));
        assertThat(core.saldo("conta-2")).contains(new BigDecimal("1000100.00"));
    }

    @Test
    void mesmaChaveComOutroConteudoEhRecusada() {
        String chave = UUID.randomUUID().toString();
        transferir("cliente-1", chave, "conta-1", "conta-2", "100");
        ResponseEntity<String> outra = transferir("cliente-1", chave, "conta-1", "conta-2", "999");
        assertThat(outra.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void creditoRecusadoDisparaCompensacao() throws Exception {
        ResponseEntity<String> r = transferir("cliente-1", UUID.randomUUID().toString(), "conta-1", "conta-inexistente", "50");
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(json(r).get("status").asText()).isEqualTo("COMPENSADA");
        assertThat(core.saldo("conta-1")).contains(new BigDecimal("1000000.00"));
    }

    @Test
    void saldoInsuficienteRejeita() throws Exception {
        ResponseEntity<String> r = transferir("cliente-1", UUID.randomUUID().toString(), "conta-pobre", "conta-2", "50");
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(json(r).get("status").asText()).isEqualTo("REJEITADA");
    }

    @Test
    void limiteDaConfigOperacionalEhRespeitado() {
        ResponseEntity<String> r = transferir("cliente-1", UUID.randomUUID().toString(), "conta-1", "conta-2", "999999");
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(r.getBody()).contains("acima do limite");
    }

    @Test
    void deadlineCurtoDemaisEhRecusadoNaEntrada() {
        HttpHeaders headers = headers("cliente-1", UUID.randomUUID().toString());
        headers.set("X-Request-Timeout-Ms", "10");
        ResponseEntity<String> r = http.postForEntity("/api/transferencias",
                new HttpEntity<>(corpo("conta-1", "conta-2", "10"), headers), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(504);
    }

    @Test
    void respostaPerdidaViraIncertaEReconciliacaoResolve() throws Exception {
        chaos.aplicar(Map.of("taxaFalhaAposAplicar", 1.0));
        ResponseEntity<String> r = transferir("cliente-1", UUID.randomUUID().toString(), "conta-1", "conta-2", "70");
        assertThat(r.getStatusCode().value()).isEqualTo(202);
        assertThat(json(r).get("status").asText()).isEqualTo("DEBITO_INCERTO");
        assertThat(core.saldo("conta-1")).contains(new BigDecimal("999930.00"));

        chaos.resetar();
        reconciliacao.executar(0);

        String id = json(r).get("id").asText();
        JsonNode depois = json(http.getForEntity("/api/transferencias/" + id, String.class));
        assertThat(depois.get("status").asText()).isEqualTo("CONCLUIDA");
        assertThat(core.saldo("conta-2")).contains(new BigDecimal("1000070.00"));
    }

    @Test
    @DirtiesContext
    void falhaEmUmaCelulaNaoAfetaAsOutras() throws Exception {
        String clienteAfetado = clienteDaCelula(0);
        String clienteSaudavel = clienteDaCelula(1);
        chaos.aplicar(Map.of("coreForaDoAr", true, "celulasAfetadas", java.util.List.of(0)));

        for (int i = 0; i < 6; i++) {
            ResponseEntity<String> afetada = transferir(clienteAfetado, UUID.randomUUID().toString(), "conta-1", "conta-2", "1");
            assertThat(json(afetada).get("status").asText()).isIn("DEBITO_INCERTO", "FALHOU");
        }
        ResponseEntity<String> saudavel = transferir(clienteSaudavel, UUID.randomUUID().toString(), "conta-3", "conta-4", "1");
        assertThat(json(saudavel).get("status").asText()).isEqualTo("CONCLUIDA");

        JsonNode status = json(http.getForEntity("/api/status", String.class));
        assertThat(status.get("circuitBreakers").get(0).get("estado").asText()).isEqualTo("ABERTO");
        assertThat(status.get("circuitBreakers").get(1).get("estado").asText()).isEqualTo("FECHADO");
    }

    private String clienteDaCelula(int celula) {
        for (int i = 0; ; i++) {
            if (celulas.celulaDe("cliente-" + i) == celula) {
                return "cliente-" + i;
            }
        }
    }

    private ResponseEntity<String> transferir(String cliente, String chave, String origem, String destino, String valor) {
        return http.postForEntity("/api/transferencias",
                new HttpEntity<>(corpo(origem, destino, valor), headers(cliente, chave)), String.class);
    }

    private HttpHeaders headers(String cliente, String chave) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Cliente", cliente);
        headers.set("Idempotency-Key", chave);
        return headers;
    }

    private String corpo(String origem, String destino, String valor) {
        return "{\"contaOrigem\":\"" + origem + "\",\"contaDestino\":\"" + destino + "\",\"valor\":" + valor + "}";
    }

    private JsonNode json(ResponseEntity<String> r) throws Exception {
        return mapper.readTree(r.getBody());
    }
}
