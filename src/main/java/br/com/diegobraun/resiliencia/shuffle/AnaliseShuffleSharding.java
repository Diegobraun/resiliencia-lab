package br.com.diegobraun.resiliencia.shuffle;

import java.util.List;

public record AnaliseShuffleSharding(
        int workers,
        int tamanhoShard,
        long combinacoesPossiveis,
        String clienteBarulhento,
        List<Integer> shardDoBarulhento,
        int outrosClientes,
        double percentualTotalmenteAfetados,
        double percentualParcialmenteAfetados,
        double percentualAfetadosComShardingSimples) {}
