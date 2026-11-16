package br.com.diegobraun.resiliencia.padroes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    private final List<Long> esperas = new ArrayList<>();

    @Test
    void backoffExponencialComTetoEJitterTotal() {
        RetryPolicy policy = new RetryPolicy(10, 50, 400, new RetryBudget(1, 100), limite -> limite - 1, esperas::add);
        assertThat(policy.backoff(1)).isEqualTo(50);
        assertThat(policy.backoff(2)).isEqualTo(100);
        assertThat(policy.backoff(3)).isEqualTo(200);
        assertThat(policy.backoff(4)).isEqualTo(400);
        assertThat(policy.backoff(8)).isEqualTo(400);

        RetryPolicy semSorte = new RetryPolicy(10, 50, 400, new RetryBudget(1, 100), limite -> 0, esperas::add);
        assertThat(semSorte.backoff(4)).isZero();
    }

    @Test
    void repeteAteSucesso() {
        AtomicInteger chamadas = new AtomicInteger();
        RetryPolicy policy = new RetryPolicy(3, 10, 100, new RetryBudget(1, 10), limite -> 1, esperas::add);
        String resultado = policy.executar(t -> {
            if (chamadas.incrementAndGet() < 3) {
                throw new IllegalStateException("transitória");
            }
            return "ok";
        }, e -> true);
        assertThat(resultado).isEqualTo("ok");
        assertThat(esperas).hasSize(2);
    }

    @Test
    void naoRepeteErroNaoRetentavel() {
        AtomicInteger chamadas = new AtomicInteger();
        RetryPolicy policy = new RetryPolicy(3, 10, 100, new RetryBudget(1, 10), limite -> 1, esperas::add);
        assertThatThrownBy(() -> policy.executar(t -> {
            chamadas.incrementAndGet();
            throw new IllegalArgumentException("negócio");
        }, e -> e instanceof IllegalStateException)).isInstanceOf(IllegalArgumentException.class);
        assertThat(chamadas).hasValue(1);
    }

    @Test
    void budgetVazioCortaRetries() {
        RetryBudget budget = new RetryBudget(0.1, 1);
        RetryPolicy policy = new RetryPolicy(5, 10, 100, budget, limite -> 1, esperas::add);
        AtomicInteger chamadas = new AtomicInteger();
        assertThatThrownBy(() -> policy.executar(t -> {
            chamadas.incrementAndGet();
            throw new IllegalStateException("falha");
        }, e -> true)).isInstanceOf(IllegalStateException.class);
        assertThat(chamadas).hasValue(2);
        assertThat(policy.snapshot().abortadosPorBudget()).isEqualTo(1);
    }

    @Test
    void naoDormeAlemDoDeadline() {
        RetryPolicy policy = new RetryPolicy(5, 100, 1000, new RetryBudget(1, 10), limite -> limite - 1, esperas::add);
        AtomicInteger chamadas = new AtomicInteger();
        assertThatThrownBy(() -> DeadlineContext.com(Deadline.daquiA(150), () -> policy.executar(t -> {
            chamadas.incrementAndGet();
            throw new IllegalStateException("falha");
        }, e -> true))).isInstanceOf(IllegalStateException.class);
        assertThat(chamadas).hasValue(1);
        assertThat(policy.snapshot().abortadosPorDeadline()).isEqualTo(1);
    }
}
