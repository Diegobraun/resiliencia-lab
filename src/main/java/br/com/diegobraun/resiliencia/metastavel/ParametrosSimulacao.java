package br.com.diegobraun.resiliencia.metastavel;

public record ParametrosSimulacao(
        int ticks,
        int chegadasPorTick,
        int capacidade,
        int capacidadeDuranteGatilho,
        int inicioGatilho,
        int fimGatilho,
        int timeoutTicks,
        int maxTentativas,
        boolean mitigado,
        int limiteFila,
        double razaoBudget) {

    public static ParametrosSimulacao padrao(boolean mitigado) {
        return new ParametrosSimulacao(200, 80, 100, 30, 50, 70, 10, 3, mitigado, 500, 0.1);
    }
}
