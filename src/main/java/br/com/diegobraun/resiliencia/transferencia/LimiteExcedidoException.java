package br.com.diegobraun.resiliencia.transferencia;

import java.math.BigDecimal;

public class LimiteExcedidoException extends RuntimeException {

    public LimiteExcedidoException(BigDecimal valor, BigDecimal limite, long versaoConfig) {
        super("valor " + valor.toPlainString() + " acima do limite " + limite.toPlainString()
                + " (config operacional versão " + versaoConfig + ")");
    }
}
