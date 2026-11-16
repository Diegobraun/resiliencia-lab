package br.com.diegobraun.resiliencia.gateway;

import br.com.diegobraun.resiliencia.rollout.Versao;

public record ResultadoLancamento(Tipo tipo, int celula, Versao versao, String motivo) {

    public enum Tipo { SUCESSO, REJEITADO, NAO_ENVIADO, INCERTO }
}
