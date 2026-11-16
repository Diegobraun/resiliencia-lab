package br.com.diegobraun.resiliencia.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(
        Core core,
        Deadline deadline,
        Retry retry,
        CircuitBreaker circuitBreaker,
        Bulkhead bulkhead,
        LoadShedding loadShedding,
        ShuffleSharding shuffleSharding,
        Celulas celulas,
        Idempotencia idempotencia,
        Outbox outbox,
        Broker broker,
        Reconciliacao reconciliacao,
        ControlPlane controlPlane,
        Rollout rollout) {

    public record Core(String url, long timeoutPorChamadaMs, long connectTimeoutMs) {}

    public record Deadline(long padraoMs, long maximoMs, long minimoParaAceitarMs) {}

    public record Retry(int maxTentativas, long backoffBaseMs, long backoffTetoMs, double budgetRazao, double budgetMaximo) {}

    public record CircuitBreaker(int janela, int minimoChamadas, double taxaFalha, long tempoAbertoMs, int chamadasMeioAberto) {}

    public record Bulkhead(int maximoConcorrente) {}

    public record LoadShedding(int limiteConcorrencia) {}

    public record ShuffleSharding(int workers, int tamanhoShard, int capacidadePorWorker) {}

    public record Celulas(int quantidade) {}

    public record Idempotencia(long expiracaoEmAndamentoMs) {}

    public record Outbox(int lote, long intervaloMs) {}

    public record Broker(int capacidadeFila) {}

    public record Reconciliacao(long intervaloMs, long idadeMinimaMs, long deadlineMs) {}

    public record ControlPlane(String url, long intervaloMs, long timeoutMs, String cacheArquivo, BigDecimal valorMaximoPadrao) {}

    public record Rollout(List<Integer> estagios, long duracaoEstagioMs, int minimoAmostras, double margemErro) {}
}
