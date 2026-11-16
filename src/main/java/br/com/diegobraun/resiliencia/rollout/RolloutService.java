package br.com.diegobraun.resiliencia.rollout;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;

import br.com.diegobraun.resiliencia.compartilhado.Hashes;
import br.com.diegobraun.resiliencia.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RolloutService {

    private final List<Integer> estagios;
    private final long duracaoEstagioMs;
    private final int minimoAmostras;
    private final double margemErro;
    private final LongSupplier relogio;

    private boolean ativo;
    private int indice = -1;
    private int percentual;
    private long inicioEstagio;
    private final Metricas v1 = new Metricas();
    private final Metricas v2 = new Metricas();
    private final Deque<String> eventos = new ArrayDeque<>();

    @Autowired
    public RolloutService(AppProperties props, Clock clock) {
        this(props.rollout().estagios(), props.rollout().duracaoEstagioMs(), props.rollout().minimoAmostras(),
                props.rollout().margemErro(), clock::millis);
    }

    public RolloutService(List<Integer> estagios, long duracaoEstagioMs, int minimoAmostras, double margemErro,
                          LongSupplier relogio) {
        this.estagios = List.copyOf(estagios);
        this.duracaoEstagioMs = duracaoEstagioMs;
        this.minimoAmostras = minimoAmostras;
        this.margemErro = margemErro;
        this.relogio = relogio;
    }

    public synchronized EstadoRollout iniciar() {
        ativo = true;
        indice = 0;
        mudarPercentual(estagios.get(0), "rollout iniciado");
        return estado();
    }

    public synchronized EstadoRollout abortar(String motivo) {
        ativo = false;
        mudarPercentual(0, "rollout abortado: " + motivo);
        return estado();
    }

    public synchronized Versao versaoPara(String cliente) {
        return Hashes.bucket(cliente + ":rollout", 100) < percentual ? Versao.V2 : Versao.V1;
    }

    public synchronized void registrar(Versao versao, boolean sucesso) {
        (versao == Versao.V2 ? v2 : v1).registrar(sucesso);
    }

    @Scheduled(fixedDelay = 1000)
    public synchronized void avaliar() {
        if (!ativo) {
            return;
        }
        if (v2.total >= minimoAmostras && v2.taxaErro() > v1.taxaErro() + margemErro) {
            ativo = false;
            mudarPercentual(0, String.format("rollback automático: erro v2=%.0f%% vs v1=%.0f%%",
                    v2.taxaErro() * 100, v1.taxaErro() * 100));
            return;
        }
        boolean tempoCumprido = relogio.getAsLong() - inicioEstagio >= duracaoEstagioMs;
        if (tempoCumprido && v2.total >= minimoAmostras) {
            if (indice == estagios.size() - 1) {
                ativo = false;
                registrarEvento("rollout concluído em " + percentual + "%");
                return;
            }
            indice++;
            mudarPercentual(estagios.get(indice), String.format("estágio aprovado (erro v2=%.0f%%, v1=%.0f%%)",
                    v2.taxaErro() * 100, v1.taxaErro() * 100));
        }
    }

    private void mudarPercentual(int novo, String motivo) {
        percentual = novo;
        inicioEstagio = relogio.getAsLong();
        v1.zerar();
        v2.zerar();
        registrarEvento(motivo + " -> " + novo + "% na v2");
    }

    private void registrarEvento(String evento) {
        eventos.addFirst(Instant.ofEpochMilli(relogio.getAsLong()) + " " + evento);
        while (eventos.size() > 20) {
            eventos.removeLast();
        }
    }

    public synchronized EstadoRollout estado() {
        return new EstadoRollout(ativo, percentual, estagios, v1.snapshot(), v2.snapshot(), List.copyOf(eventos));
    }

    private static final class Metricas {
        long total;
        long falhas;

        void registrar(boolean sucesso) {
            total++;
            if (!sucesso) {
                falhas++;
            }
        }

        double taxaErro() {
            return total == 0 ? 0 : (double) falhas / total;
        }

        void zerar() {
            total = 0;
            falhas = 0;
        }

        EstadoRollout.Metricas snapshot() {
            return new EstadoRollout.Metricas(total, falhas, Math.round(taxaErro() * 1000) / 1000.0);
        }
    }
}
