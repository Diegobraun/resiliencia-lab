package br.com.diegobraun.resiliencia.padroes;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

public class RetryPolicy {

    private final int maxTentativas;
    private final long baseMs;
    private final long tetoMs;
    private final RetryBudget budget;
    private final LongUnaryOperator aleatorio;
    private final Dormidor dormidor;

    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong abortadosPorBudget = new AtomicLong();
    private final AtomicLong abortadosPorDeadline = new AtomicLong();

    public RetryPolicy(int maxTentativas, long baseMs, long tetoMs, RetryBudget budget,
                       LongUnaryOperator aleatorio, Dormidor dormidor) {
        this.maxTentativas = maxTentativas;
        this.baseMs = baseMs;
        this.tetoMs = tetoMs;
        this.budget = budget;
        this.aleatorio = aleatorio;
        this.dormidor = dormidor;
    }

    public <T> T executar(Operacao<T> operacao, Predicate<RuntimeException> retentavel) {
        budget.registrarRequisicao();
        int tentativa = 1;
        while (true) {
            try {
                return operacao.executar(tentativa);
            } catch (RuntimeException e) {
                if (!retentavel.test(e) || tentativa >= maxTentativas) {
                    throw e;
                }
                long espera = backoff(tentativa);
                if (!cabeNoDeadline(espera)) {
                    abortadosPorDeadline.incrementAndGet();
                    throw e;
                }
                if (!budget.tentarGastar()) {
                    abortadosPorBudget.incrementAndGet();
                    throw e;
                }
                retries.incrementAndGet();
                dormidor.dormir(espera);
                tentativa++;
            }
        }
    }

    public long backoff(int tentativa) {
        long exponencial = baseMs * (1L << Math.min(tentativa - 1, 30));
        long teto = Math.min(tetoMs, exponencial);
        return aleatorio.applyAsLong(teto + 1);
    }

    private boolean cabeNoDeadline(long espera) {
        return DeadlineContext.restanteMs().stream().allMatch(restante -> restante > espera + baseMs);
    }

    public Snapshot snapshot() {
        return new Snapshot(maxTentativas, baseMs, tetoMs, retries.get(), abortadosPorBudget.get(),
                abortadosPorDeadline.get(), budget.snapshot());
    }

    @FunctionalInterface
    public interface Operacao<T> {
        T executar(int tentativa);
    }

    public record Snapshot(int maxTentativas, long backoffBaseMs, long backoffTetoMs, long retries,
                           long abortadosPorBudget, long abortadosPorDeadline, RetryBudget.Snapshot budget) {}
}
