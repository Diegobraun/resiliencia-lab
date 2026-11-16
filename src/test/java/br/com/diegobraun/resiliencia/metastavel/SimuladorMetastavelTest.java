package br.com.diegobraun.resiliencia.metastavel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimuladorMetastavelTest {

    private final SimuladorMetastavel simulador = new SimuladorMetastavel();

    @Test
    void sistemaIngenuoNaoSeRecuperaDepoisQueOGatilhoSome() {
        ResultadoSimulacao r = simulador.simular(ParametrosSimulacao.padrao(false));
        assertThat(r.goodputMedioAntesDoGatilho()).isEqualTo(80.0);
        assertThat(r.recuperou()).isFalse();
        assertThat(r.goodputMedioUltimos20Ticks()).isLessThan(10);
        assertThat(r.amplificacaoMaximaDeCarga()).isGreaterThan(2);
    }

    @Test
    void sistemaMitigadoSeRecupera() {
        ResultadoSimulacao r = simulador.simular(ParametrosSimulacao.padrao(true));
        assertThat(r.recuperou()).isTrue();
        assertThat(r.ticksAteRecuperarAposGatilho()).isLessThan(30);
        assertThat(r.goodputMedioUltimos20Ticks()).isGreaterThanOrEqualTo(78);
        assertThat(r.amplificacaoMaximaDeCarga()).isLessThan(1.2);
    }

    @Test
    void semGatilhoNadaAcontece() {
        ParametrosSimulacao p = new ParametrosSimulacao(100, 80, 100, 100, 50, 70, 10, 3, false, 500, 0.1);
        ResultadoSimulacao r = simulador.simular(p);
        assertThat(r.filaMaxima()).isZero();
        assertThat(r.trabalhoDesperdicado()).isZero();
    }
}
