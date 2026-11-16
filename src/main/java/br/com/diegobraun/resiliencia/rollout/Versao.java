package br.com.diegobraun.resiliencia.rollout;

public enum Versao {
    V1("/core/lancamentos"),
    V2("/core/v2/lancamentos");

    private final String caminho;

    Versao(String caminho) {
        this.caminho = caminho;
    }

    public String caminho() {
        return caminho;
    }
}
