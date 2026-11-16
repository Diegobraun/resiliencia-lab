package br.com.diegobraun.resiliencia.idempotencia;

public class ChaveReutilizadaException extends RuntimeException {

    public ChaveReutilizadaException(String chave) {
        super("Idempotency-Key '" + chave + "' já foi usada com outro conteúdo");
    }
}
