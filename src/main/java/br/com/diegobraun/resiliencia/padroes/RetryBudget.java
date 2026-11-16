package br.com.diegobraun.resiliencia.padroes;

public class RetryBudget {

    private final double razao;
    private final double maximo;
    private double saldo;
    private long gastos;
    private long negados;

    public RetryBudget(double razao, double maximo) {
        this.razao = razao;
        this.maximo = maximo;
        this.saldo = maximo;
    }

    public synchronized void registrarRequisicao() {
        saldo = Math.min(maximo, saldo + razao);
    }

    public synchronized boolean tentarGastar() {
        if (saldo >= 1) {
            saldo -= 1;
            gastos++;
            return true;
        }
        negados++;
        return false;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(Math.round(saldo * 100) / 100.0, maximo, razao, gastos, negados);
    }

    public record Snapshot(double saldo, double maximo, double razao, long retriesPermitidos, long retriesNegados) {}
}
