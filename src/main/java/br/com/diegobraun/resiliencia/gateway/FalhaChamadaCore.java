package br.com.diegobraun.resiliencia.gateway;

public abstract sealed class FalhaChamadaCore extends RuntimeException
        permits FalhaChamadaCore.Transitoria, FalhaChamadaCore.CircuitoAberto,
                FalhaChamadaCore.BulkheadCheio, FalhaChamadaCore.DeadlineEsgotado {

    protected FalhaChamadaCore(String mensagem) {
        super(mensagem);
    }

    public static final class Transitoria extends FalhaChamadaCore {
        public Transitoria(String mensagem) {
            super(mensagem);
        }
    }

    public static final class CircuitoAberto extends FalhaChamadaCore {
        public CircuitoAberto(int celula) {
            super("circuit breaker da célula " + celula + " aberto");
        }
    }

    public static final class BulkheadCheio extends FalhaChamadaCore {
        public BulkheadCheio(int celula) {
            super("bulkhead da célula " + celula + " cheio");
        }
    }

    public static final class DeadlineEsgotado extends FalhaChamadaCore {
        public DeadlineEsgotado() {
            super("deadline da requisição esgotado antes da chamada");
        }
    }
}
