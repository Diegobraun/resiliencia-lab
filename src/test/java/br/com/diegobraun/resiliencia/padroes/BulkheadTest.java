package br.com.diegobraun.resiliencia.padroes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BulkheadTest {

    @Test
    void rejeitaAlemDaCapacidadeELiberaAoSair() {
        Bulkhead bulkhead = new Bulkhead("teste", 2);
        assertThat(bulkhead.tentarEntrar()).isTrue();
        assertThat(bulkhead.tentarEntrar()).isTrue();
        assertThat(bulkhead.tentarEntrar()).isFalse();
        bulkhead.sair();
        assertThat(bulkhead.tentarEntrar()).isTrue();
        assertThat(bulkhead.snapshot().rejeitadas()).isEqualTo(1);
        assertThat(bulkhead.snapshot().emUso()).isEqualTo(2);
    }
}
