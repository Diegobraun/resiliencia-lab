package br.com.diegobraun.resiliencia.padroes;

import java.util.function.LongSupplier;

public class CircuitBreaker {

    public enum Estado { FECHADO, ABERTO, MEIO_ABERTO }

    private final String nome;
    private final int janela;
    private final int minimoChamadas;
    private final double taxaFalha;
    private final long tempoAbertoMs;
    private final int chamadasMeioAberto;
    private final LongSupplier relogio;

    private final boolean[] resultados;
    private int posicao;
    private int preenchidos;
    private int falhas;

    private Estado estado = Estado.FECHADO;
    private long abertoEm;
    private int permitidasMeioAberto;
    private int sucessosMeioAberto;
    private long rejeitadas;
    private long aberturas;

    public CircuitBreaker(String nome, int janela, int minimoChamadas, double taxaFalha,
                          long tempoAbertoMs, int chamadasMeioAberto, LongSupplier relogio) {
        this.nome = nome;
        this.janela = janela;
        this.minimoChamadas = minimoChamadas;
        this.taxaFalha = taxaFalha;
        this.tempoAbertoMs = tempoAbertoMs;
        this.chamadasMeioAberto = chamadasMeioAberto;
        this.relogio = relogio;
        this.resultados = new boolean[janela];
    }

    public synchronized boolean permitir() {
        if (estado == Estado.ABERTO) {
            if (relogio.getAsLong() - abertoEm < tempoAbertoMs) {
                rejeitadas++;
                return false;
            }
            estado = Estado.MEIO_ABERTO;
            permitidasMeioAberto = 0;
            sucessosMeioAberto = 0;
        }
        if (estado == Estado.MEIO_ABERTO) {
            if (permitidasMeioAberto < chamadasMeioAberto) {
                permitidasMeioAberto++;
                return true;
            }
            rejeitadas++;
            return false;
        }
        return true;
    }

    public synchronized void registrarSucesso() {
        if (estado == Estado.MEIO_ABERTO) {
            sucessosMeioAberto++;
            if (sucessosMeioAberto >= chamadasMeioAberto) {
                fechar();
            }
            return;
        }
        if (estado == Estado.FECHADO) {
            registrar(false);
        }
    }

    public synchronized void registrarFalha() {
        if (estado == Estado.MEIO_ABERTO) {
            abrir();
            return;
        }
        if (estado == Estado.FECHADO) {
            registrar(true);
            if (preenchidos >= minimoChamadas && (double) falhas / preenchidos >= taxaFalha) {
                abrir();
            }
        }
    }

    public synchronized void cancelar() {
        if (estado == Estado.MEIO_ABERTO && permitidasMeioAberto > 0) {
            permitidasMeioAberto--;
        }
    }

    private void registrar(boolean falhou) {
        if (preenchidos == janela) {
            if (resultados[posicao]) {
                falhas--;
            }
        } else {
            preenchidos++;
        }
        resultados[posicao] = falhou;
        if (falhou) {
            falhas++;
        }
        posicao = (posicao + 1) % janela;
    }

    private void abrir() {
        estado = Estado.ABERTO;
        abertoEm = relogio.getAsLong();
        aberturas++;
        limparJanela();
    }

    private void fechar() {
        estado = Estado.FECHADO;
        limparJanela();
    }

    private void limparJanela() {
        posicao = 0;
        preenchidos = 0;
        falhas = 0;
    }

    public synchronized Estado estado() {
        if (estado == Estado.ABERTO && relogio.getAsLong() - abertoEm >= tempoAbertoMs) {
            return Estado.MEIO_ABERTO;
        }
        return estado;
    }

    public synchronized Snapshot snapshot() {
        double taxa = preenchidos == 0 ? 0 : (double) falhas / preenchidos;
        return new Snapshot(nome, estado(), preenchidos, Math.round(taxa * 100) / 100.0, rejeitadas, aberturas);
    }

    public record Snapshot(String nome, Estado estado, int chamadasNaJanela, double taxaFalha,
                           long rejeitadas, long aberturas) {}
}
