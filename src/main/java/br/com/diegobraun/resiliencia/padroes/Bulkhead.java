package br.com.diegobraun.resiliencia.padroes;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class Bulkhead {

    private final String nome;
    private final int maximo;
    private final Semaphore permissoes;
    private final AtomicLong rejeitadas = new AtomicLong();

    public Bulkhead(String nome, int maximo) {
        this.nome = nome;
        this.maximo = maximo;
        this.permissoes = new Semaphore(maximo);
    }

    public boolean tentarEntrar() {
        if (permissoes.tryAcquire()) {
            return true;
        }
        rejeitadas.incrementAndGet();
        return false;
    }

    public void sair() {
        permissoes.release();
    }

    public Snapshot snapshot() {
        return new Snapshot(nome, maximo, maximo - permissoes.availablePermits(), rejeitadas.get());
    }

    public record Snapshot(String nome, int maximo, int emUso, long rejeitadas) {}
}
