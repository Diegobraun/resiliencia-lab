package br.com.diegobraun.resiliencia.padroes;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

public final class DeadlineContext {

    private static final ThreadLocal<Deadline> ATUAL = new ThreadLocal<>();

    private DeadlineContext() {
    }

    public static Optional<Deadline> atual() {
        return Optional.ofNullable(ATUAL.get());
    }

    public static OptionalLong restanteMs() {
        Deadline d = ATUAL.get();
        return d == null ? OptionalLong.empty() : OptionalLong.of(d.restanteMs());
    }

    public static void definir(Deadline deadline) {
        ATUAL.set(deadline);
    }

    public static void limpar() {
        ATUAL.remove();
    }

    public static <T> T com(Deadline deadline, Supplier<T> operacao) {
        Deadline anterior = ATUAL.get();
        ATUAL.set(deadline);
        try {
            return operacao.get();
        } finally {
            if (anterior == null) {
                ATUAL.remove();
            } else {
                ATUAL.set(anterior);
            }
        }
    }
}
