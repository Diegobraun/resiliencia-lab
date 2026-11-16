package br.com.diegobraun.resiliencia.transferencia;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferenciaResposta(String id, StatusTransferencia status, String motivo, String contaOrigem,
                                    String contaDestino, BigDecimal valor, int celula, Instant criadoEm,
                                    Instant atualizadoEm, String acompanhar) {

    public static TransferenciaResposta de(Transferencia t) {
        return new TransferenciaResposta(t.id(), t.status(), t.motivo(), t.contaOrigem(), t.contaDestino(), t.valor(),
                t.celula(), t.criadoEm(), t.atualizadoEm(), "/api/transferencias/" + t.id());
    }
}
