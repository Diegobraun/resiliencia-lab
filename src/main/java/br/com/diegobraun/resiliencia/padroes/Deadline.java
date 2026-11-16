package br.com.diegobraun.resiliencia.padroes;

public record Deadline(long expiraEmEpochMs) {

    public static Deadline daquiA(long ms) {
        return new Deadline(System.currentTimeMillis() + ms);
    }

    public long restanteMs() {
        return Math.max(0, expiraEmEpochMs - System.currentTimeMillis());
    }

    public boolean expirou() {
        return restanteMs() == 0;
    }
}
