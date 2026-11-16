package br.com.diegobraun.resiliencia.shuffle;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShuffleShardingTest {

    private final ShuffleSharding sharding = new ShuffleSharding(8, 2, 1);

    @Test
    void shardEhDeterministicoESemRepeticao() {
        int[] primeiro = sharding.shardDe("cliente-42");
        assertThat(sharding.shardDe("cliente-42")).isEqualTo(primeiro);
        assertThat(Arrays.stream(primeiro).distinct().count()).isEqualTo(2);
    }

    @Test
    void clienteBarulhentoSaturaSoOProprioShard() {
        String barulhento = "cliente-barulhento";
        while (sharding.adquirir(barulhento).isPresent()) {
        }
        String vizinhoParcial = null;
        for (int i = 0; i < 1000 && vizinhoParcial == null; i++) {
            String c = "cliente-" + i;
            long comum = Arrays.stream(sharding.shardDe(c))
                    .filter(w -> Arrays.stream(sharding.shardDe(barulhento)).anyMatch(b -> b == w)).count();
            if (comum == 1) {
                vizinhoParcial = c;
            }
        }
        assertThat(vizinhoParcial).isNotNull();
        assertThat(sharding.adquirir(vizinhoParcial)).isPresent();
    }

    @Test
    void poucosClientesFicamTotalmenteAfetados() {
        AnaliseShuffleSharding analise = sharding.analisar(5000, "cliente-barulhento");
        assertThat(analise.combinacoesPossiveis()).isEqualTo(28);
        assertThat(analise.percentualTotalmenteAfetados()).isBetween(1.5, 6.0);
        assertThat(analise.percentualAfetadosComShardingSimples()).isBetween(20.0, 30.0);
    }
}
