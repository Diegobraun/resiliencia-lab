package br.com.diegobraun.resiliencia.gateway;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import br.com.diegobraun.resiliencia.celulas.CelulaRouter;
import br.com.diegobraun.resiliencia.compartilhado.Json;
import br.com.diegobraun.resiliencia.compartilhado.UrlBase;
import br.com.diegobraun.resiliencia.config.AppProperties;
import br.com.diegobraun.resiliencia.core.LancamentoCore;
import br.com.diegobraun.resiliencia.padroes.Bulkhead;
import br.com.diegobraun.resiliencia.padroes.CircuitBreaker;
import br.com.diegobraun.resiliencia.padroes.DeadlineContext;
import br.com.diegobraun.resiliencia.padroes.RetryPolicy;
import br.com.diegobraun.resiliencia.rollout.RolloutService;
import br.com.diegobraun.resiliencia.rollout.Versao;
import org.springframework.stereotype.Component;

@Component
public class CoreGateway {

    public enum Consulta { EXISTE, NAO_EXISTE, DESCONHECIDO }

    private final HttpJson http;
    private final UrlBase urlBase;
    private final AppProperties props;
    private final Json json;
    private final RetryPolicy retry;
    private final CelulaRouter celulas;
    private final RolloutService rollout;
    private final Map<Integer, CircuitBreaker> breakers = new TreeMap<>();
    private final Map<Integer, Bulkhead> bulkheads = new TreeMap<>();

    public CoreGateway(HttpJson http, UrlBase urlBase, AppProperties props, Json json, RetryPolicy retry,
                       CelulaRouter celulas, RolloutService rollout, Clock clock) {
        this.http = http;
        this.urlBase = urlBase;
        this.props = props;
        this.json = json;
        this.retry = retry;
        this.celulas = celulas;
        this.rollout = rollout;
        AppProperties.CircuitBreaker cb = props.circuitBreaker();
        for (int celula = 0; celula < props.celulas().quantidade(); celula++) {
            breakers.put(celula, new CircuitBreaker("celula-" + celula, cb.janela(), cb.minimoChamadas(),
                    cb.taxaFalha(), cb.tempoAbertoMs(), cb.chamadasMeioAberto(), clock::millis));
            bulkheads.put(celula, new Bulkhead("celula-" + celula, props.bulkhead().maximoConcorrente()));
        }
    }

    public ResultadoLancamento lancar(String cliente, LancamentoCore lancamento) {
        int celula = celulas.celulaDe(cliente);
        Versao versao = rollout.versaoPara(cliente);
        AtomicBoolean enviado = new AtomicBoolean();
        ResultadoLancamento resultado;
        try {
            HttpResposta resposta = retry.executar(
                    tentativa -> tentar(celula, versao, lancamento, enviado),
                    e -> e instanceof FalhaChamadaCore.Transitoria);
            resultado = resposta.status() == 200
                    ? new ResultadoLancamento(ResultadoLancamento.Tipo.SUCESSO, celula, versao, null)
                    : new ResultadoLancamento(ResultadoLancamento.Tipo.REJEITADO, celula, versao, motivo(resposta));
        } catch (FalhaChamadaCore e) {
            ResultadoLancamento.Tipo tipo = enviado.get()
                    ? ResultadoLancamento.Tipo.INCERTO
                    : ResultadoLancamento.Tipo.NAO_ENVIADO;
            resultado = new ResultadoLancamento(tipo, celula, versao, e.getMessage());
        }
        if (resultado.tipo() != ResultadoLancamento.Tipo.NAO_ENVIADO) {
            rollout.registrar(versao, resultado.tipo() != ResultadoLancamento.Tipo.INCERTO);
        }
        return resultado;
    }

    private HttpResposta tentar(int celula, Versao versao, LancamentoCore lancamento, AtomicBoolean enviado) {
        CircuitBreaker breaker = breakers.get(celula);
        Bulkhead bulkhead = bulkheads.get(celula);
        if (!breaker.permitir()) {
            throw new FalhaChamadaCore.CircuitoAberto(celula);
        }
        if (!bulkhead.tentarEntrar()) {
            breaker.cancelar();
            throw new FalhaChamadaCore.BulkheadCheio(celula);
        }
        try {
            long timeout = timeoutDaChamada();
            if (timeout <= 0) {
                breaker.cancelar();
                throw new FalhaChamadaCore.DeadlineEsgotado();
            }
            enviado.set(true);
            HttpResposta resposta = http.post(
                    urlBase.resolver(props.core().url()) + versao.caminho(),
                    json.escrever(lancamento),
                    headers(celula, timeout),
                    Duration.ofMillis(timeout));
            if (resposta.status() >= 500) {
                breaker.registrarFalha();
                throw new FalhaChamadaCore.Transitoria("core respondeu " + resposta.status() + ": " + motivo(resposta));
            }
            breaker.registrarSucesso();
            return resposta;
        } catch (HttpJson.FalhaHttp e) {
            breaker.registrarFalha();
            throw new FalhaChamadaCore.Transitoria(e.getMessage());
        } finally {
            bulkhead.sair();
        }
    }

    public Consulta consultar(String cliente, String operacaoId) {
        int celula = celulas.celulaDe(cliente);
        CircuitBreaker breaker = breakers.get(celula);
        if (!breaker.permitir()) {
            return Consulta.DESCONHECIDO;
        }
        try {
            long timeout = Math.max(1, timeoutDaChamada());
            HttpResposta resposta = http.get(
                    urlBase.resolver(props.core().url()) + "/core/lancamentos/" + operacaoId,
                    headers(celula, timeout),
                    Duration.ofMillis(timeout));
            if (resposta.status() == 200) {
                breaker.registrarSucesso();
                return Consulta.EXISTE;
            }
            if (resposta.status() == 404) {
                breaker.registrarSucesso();
                return Consulta.NAO_EXISTE;
            }
            breaker.registrarFalha();
            return Consulta.DESCONHECIDO;
        } catch (HttpJson.FalhaHttp e) {
            breaker.registrarFalha();
            return Consulta.DESCONHECIDO;
        }
    }

    private long timeoutDaChamada() {
        long porChamada = props.core().timeoutPorChamadaMs();
        return Math.min(porChamada, DeadlineContext.restanteMs().orElse(porChamada));
    }

    private Map<String, String> headers(int celula, long timeoutMs) {
        return Map.of("X-Celula", String.valueOf(celula), "X-Deadline-Restante-Ms", String.valueOf(timeoutMs));
    }

    private String motivo(HttpResposta resposta) {
        String erro = json.campo(resposta.corpo(), "erro");
        return erro != null ? erro : "HTTP " + resposta.status();
    }

    public List<CircuitBreaker.Snapshot> breakers() {
        return breakers.values().stream().map(CircuitBreaker::snapshot).toList();
    }

    public List<Bulkhead.Snapshot> bulkheads() {
        return bulkheads.values().stream().map(Bulkhead::snapshot).toList();
    }
}
