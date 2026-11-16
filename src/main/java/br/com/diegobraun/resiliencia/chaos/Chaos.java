package br.com.diegobraun.resiliencia.chaos;

import java.util.Set;

public record Chaos(
        long latenciaMs,
        double taxaErro,
        double taxaFalhaAposAplicar,
        boolean coreForaDoAr,
        Set<Integer> celulasAfetadas,
        boolean v2Quebrada,
        boolean controlPlaneForaDoAr,
        long consumidorLentoMs,
        double taxaFalhaRelay) {

    public static Chaos desligado() {
        return new Chaos(0, 0, 0, false, Set.of(), false, false, 0, 0);
    }

    public boolean afeta(Integer celula) {
        if (celulasAfetadas == null || celulasAfetadas.isEmpty()) {
            return true;
        }
        return celula != null && celulasAfetadas.contains(celula);
    }
}
