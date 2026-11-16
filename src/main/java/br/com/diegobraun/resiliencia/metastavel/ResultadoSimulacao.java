package br.com.diegobraun.resiliencia.metastavel;

import java.util.List;

public record ResultadoSimulacao(
        ParametrosSimulacao parametros,
        boolean recuperou,
        Integer tickRecuperacao,
        Integer ticksAteRecuperarAposGatilho,
        int filaMaxima,
        double goodputMedioAntesDoGatilho,
        double goodputMedioUltimos20Ticks,
        double amplificacaoMaximaDeCarga,
        long trabalhoDesperdicado,
        String graficoGoodput,
        String graficoFila,
        List<Ponto> pontos) {

    public record Ponto(int tick, int capacidade, int chegadasNovas, int retries, int fila, int goodput,
                        int desperdicio, int descartadosSemCusto, int rejeitadas) {}
}
