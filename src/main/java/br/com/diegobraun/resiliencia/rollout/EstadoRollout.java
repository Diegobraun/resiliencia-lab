package br.com.diegobraun.resiliencia.rollout;

import java.util.List;

public record EstadoRollout(boolean ativo, int percentualV2, List<Integer> estagios, Metricas v1, Metricas v2,
                            List<String> eventos) {

    public record Metricas(long chamadas, long falhas, double taxaErro) {}
}
