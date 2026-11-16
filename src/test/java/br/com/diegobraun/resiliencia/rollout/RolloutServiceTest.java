package br.com.diegobraun.resiliencia.rollout;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolloutServiceTest {

    private final AtomicLong agora = new AtomicLong();
    private final RolloutService rollout = new RolloutService(List.of(10, 50, 100), 1000, 5, 0.05, agora::get);

    @Test
    void avancaEstagiosQuandoCanarioSaudavel() {
        rollout.iniciar();
        for (int estagio = 0; estagio < 3; estagio++) {
            registrar(Versao.V1, 20, 0);
            registrar(Versao.V2, 10, 0);
            agora.addAndGet(1000);
            rollout.avaliar();
        }
        assertThat(rollout.estado().percentualV2()).isEqualTo(100);
        assertThat(rollout.estado().ativo()).isFalse();
    }

    @Test
    void fazRollbackAutomaticoQuandoCanarioErraMais() {
        rollout.iniciar();
        registrar(Versao.V1, 50, 1);
        registrar(Versao.V2, 10, 5);
        rollout.avaliar();
        assertThat(rollout.estado().percentualV2()).isZero();
        assertThat(rollout.estado().ativo()).isFalse();
        assertThat(rollout.estado().eventos().getFirst()).contains("rollback automático");
    }

    @Test
    void clienteFicaSempreNaMesmaVersao() {
        rollout.iniciar();
        Versao primeira = rollout.versaoPara("cliente-7");
        for (int i = 0; i < 10; i++) {
            assertThat(rollout.versaoPara("cliente-7")).isEqualTo(primeira);
        }
    }

    private void registrar(Versao versao, int total, int falhas) {
        for (int i = 0; i < total; i++) {
            rollout.registrar(versao, i >= falhas);
        }
    }
}
