package br.com.diegobraun.resiliencia.padroes;

@FunctionalInterface
public interface Dormidor {

    void dormir(long ms);

    static Dormidor real() {
        return ms -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
