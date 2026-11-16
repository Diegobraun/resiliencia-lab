package br.com.diegobraun.resiliencia.reconciliacao;

import java.time.Instant;
import java.util.List;

public record RelatorioReconciliacao(Instant executadaEm, int analisadas, int resolvidas, int semMudanca,
                                     List<String> acoes) {}
