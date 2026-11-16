package br.com.diegobraun.resiliencia.padroes;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    private final AtomicLong agora = new AtomicLong();
    private final CircuitBreaker breaker = new CircuitBreaker("teste", 10, 5, 0.5, 1000, 2, agora::get);

    @Test
    void naoAbreAntesDoMinimoDeChamadas() {
        for (int i = 0; i < 4; i++) {
            breaker.registrarFalha();
        }
        assertThat(breaker.estado()).isEqualTo(CircuitBreaker.Estado.FECHADO);
    }

    @Test
    void abreQuandoTaxaDeFalhaAtingeLimite() {
        breaker.registrarSucesso();
        breaker.registrarSucesso();
        breaker.registrarFalha();
        breaker.registrarFalha();
        breaker.registrarFalha();
        assertThat(breaker.estado()).isEqualTo(CircuitBreaker.Estado.ABERTO);
        assertThat(breaker.permitir()).isFalse();
    }

    @Test
    void meioAbertoFechaAposSucessosDeTeste() {
        abrir();
        agora.addAndGet(1000);
        assertThat(breaker.permitir()).isTrue();
        assertThat(breaker.permitir()).isTrue();
        assertThat(breaker.permitir()).isFalse();
        breaker.registrarSucesso();
        breaker.registrarSucesso();
        assertThat(breaker.estado()).isEqualTo(CircuitBreaker.Estado.FECHADO);
    }

    @Test
    void meioAbertoReabreNaPrimeiraFalha() {
        abrir();
        agora.addAndGet(1000);
        assertThat(breaker.permitir()).isTrue();
        breaker.registrarFalha();
        assertThat(breaker.estado()).isEqualTo(CircuitBreaker.Estado.ABERTO);
        assertThat(breaker.permitir()).isFalse();
    }

    @Test
    void janelaDeslizanteEsqueceFalhasAntigas() {
        for (int i = 0; i < 4; i++) {
            breaker.registrarFalha();
        }
        for (int i = 0; i < 10; i++) {
            breaker.registrarSucesso();
        }
        breaker.registrarFalha();
        assertThat(breaker.estado()).isEqualTo(CircuitBreaker.Estado.FECHADO);
    }

    private void abrir() {
        for (int i = 0; i < 5; i++) {
            breaker.registrarFalha();
        }
        assertThat(breaker.estado()).isEqualTo(CircuitBreaker.Estado.ABERTO);
    }
}
