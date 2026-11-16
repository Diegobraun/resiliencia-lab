package br.com.diegobraun.resiliencia.transferencia;

public enum StatusTransferencia {
    INICIADA(202),
    DEBITO_INCERTO(202),
    DEBITADA(202),
    CREDITO_INCERTO(202),
    CONCLUIDA(201),
    REJEITADA(422),
    FALHOU(503),
    COMPENSANDO(202),
    COMPENSACAO_PENDENTE(202),
    COMPENSADA(422),
    COMPENSACAO_FALHOU(500);

    private final int statusHttp;

    StatusTransferencia(int statusHttp) {
        this.statusHttp = statusHttp;
    }

    public int statusHttp() {
        return statusHttp;
    }
}
