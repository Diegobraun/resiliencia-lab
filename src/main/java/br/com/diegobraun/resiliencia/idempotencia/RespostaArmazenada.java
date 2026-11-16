package br.com.diegobraun.resiliencia.idempotencia;

public record RespostaArmazenada(int status, String corpo, boolean armazenar, boolean replay) {

    public static RespostaArmazenada nova(int status, String corpo, boolean armazenar) {
        return new RespostaArmazenada(status, corpo, armazenar, false);
    }
}
