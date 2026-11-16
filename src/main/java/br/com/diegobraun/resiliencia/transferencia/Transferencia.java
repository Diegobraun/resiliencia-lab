package br.com.diegobraun.resiliencia.transferencia;

import java.math.BigDecimal;
import java.time.Instant;

public record Transferencia(
        String id,
        String cliente,
        String contaOrigem,
        String contaDestino,
        BigDecimal valor,
        StatusTransferencia status,
        String motivo,
        int celula,
        Instant criadoEm,
        Instant atualizadoEm) {

    public Transferencia com(StatusTransferencia novo, String novoMotivo) {
        return new Transferencia(id, cliente, contaOrigem, contaDestino, valor, novo, novoMotivo, celula, criadoEm, Instant.now());
    }

    public String operacaoDebito() {
        return id + ":D";
    }

    public String operacaoCredito() {
        return id + ":C";
    }

    public String operacaoEstorno() {
        return id + ":E";
    }
}
