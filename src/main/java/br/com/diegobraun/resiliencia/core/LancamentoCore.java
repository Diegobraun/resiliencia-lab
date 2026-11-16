package br.com.diegobraun.resiliencia.core;

import java.math.BigDecimal;

public record LancamentoCore(String operacaoId, String conta, TipoLancamento tipo, BigDecimal valor) {

    public boolean mesmoConteudo(LancamentoCore outro) {
        return conta.equals(outro.conta) && tipo == outro.tipo && valor.compareTo(outro.valor) == 0;
    }
}
