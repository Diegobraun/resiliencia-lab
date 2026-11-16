package br.com.diegobraun.resiliencia.core;

import java.util.Map;

import br.com.diegobraun.resiliencia.chaos.Chaos;
import br.com.diegobraun.resiliencia.chaos.ChaosService;
import br.com.diegobraun.resiliencia.padroes.Dormidor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/core")
public class CoreSimuladoController {

    private final CoreSimulado core;
    private final ChaosService chaos;
    private final Dormidor dormidor = Dormidor.real();

    public CoreSimuladoController(CoreSimulado core, ChaosService chaos) {
        this.core = core;
        this.chaos = chaos;
    }

    @PostMapping({"/lancamentos", "/v2/lancamentos"})
    public ResponseEntity<Map<String, Object>> lancar(
            @RequestBody LancamentoCore lancamento,
            @RequestHeader(value = "X-Deadline-Restante-Ms", required = false) Long restanteMs,
            @RequestHeader(value = "X-Celula", required = false) Integer celula,
            HttpServletRequest request) {

        long expiraEm = restanteMs == null ? Long.MAX_VALUE : System.currentTimeMillis() + restanteMs;
        Chaos c = chaos.atual();
        boolean afetado = c.afeta(celula);

        if (request.getRequestURI().contains("/v2/") && c.v2Quebrada()) {
            return erro(HttpStatus.INTERNAL_SERVER_ERROR, "v2 quebrada: NullPointerException no mapeamento", false);
        }
        if (afetado && c.coreForaDoAr()) {
            return erro(HttpStatus.SERVICE_UNAVAILABLE, "core fora do ar", false);
        }
        if (afetado && c.latenciaMs() > 0) {
            dormidor.dormir(c.latenciaMs());
        }
        if (System.currentTimeMillis() >= expiraEm) {
            core.registrarDescartePorDeadline();
            return erro(HttpStatus.GATEWAY_TIMEOUT, "deadline do chamador expirou, nada foi aplicado", false);
        }
        if (afetado && chaos.sortear(c.taxaErro())) {
            return erro(HttpStatus.INTERNAL_SERVER_ERROR, "erro interno do core", false);
        }

        CoreSimulado.Resultado resultado = core.aplicar(lancamento);
        return switch (resultado) {
            case APLICADO, JA_APLICADO -> {
                if (afetado && chaos.sortear(c.taxaFalhaAposAplicar())) {
                    yield erro(HttpStatus.INTERNAL_SERVER_ERROR, "erro após aplicar: resposta perdida", true);
                }
                yield ResponseEntity.ok(Map.of(
                        "operacaoId", lancamento.operacaoId(),
                        "resultado", resultado.name(),
                        "saldo", core.saldo(lancamento.conta()).orElseThrow()));
            }
            case CONTA_INEXISTENTE -> erro(HttpStatus.UNPROCESSABLE_ENTITY, "conta inexistente: " + lancamento.conta(), false);
            case SALDO_INSUFICIENTE -> erro(HttpStatus.UNPROCESSABLE_ENTITY, "saldo insuficiente", false);
            case CONFLITO -> erro(HttpStatus.CONFLICT, "operacaoId já usada com outro conteúdo", false);
        };
    }

    @GetMapping("/lancamentos/{operacaoId}")
    public ResponseEntity<?> consultar(@PathVariable String operacaoId) {
        if (chaos.atual().coreForaDoAr()) {
            return erro(HttpStatus.SERVICE_UNAVAILABLE, "core fora do ar", false);
        }
        return core.lancamento(operacaoId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/contas/{conta}")
    public ResponseEntity<?> saldo(@PathVariable String conta) {
        return core.saldo(conta)
                .<ResponseEntity<?>>map(s -> ResponseEntity.ok(Map.of("conta", conta, "saldo", s)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetar() {
        core.resetar();
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Map<String, Object>> erro(HttpStatus status, String mensagem, boolean aplicado) {
        return ResponseEntity.status(status).body(Map.of("erro", mensagem, "aplicado", aplicado));
    }
}
