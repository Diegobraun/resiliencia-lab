package br.com.diegobraun.resiliencia.core;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class CoreSimulado {

    public enum Resultado { APLICADO, JA_APLICADO, CONTA_INEXISTENTE, SALDO_INSUFICIENTE, CONFLITO }

    private final Map<String, BigDecimal> saldos = new ConcurrentHashMap<>();
    private final Map<String, LancamentoCore> lancamentos = new ConcurrentHashMap<>();
    private final AtomicLong descartadosPorDeadline = new AtomicLong();
    private final AtomicLong replaysIdempotentes = new AtomicLong();

    public CoreSimulado() {
        resetar();
    }

    public synchronized Resultado aplicar(LancamentoCore lancamento) {
        LancamentoCore existente = lancamentos.get(lancamento.operacaoId());
        if (existente != null) {
            if (!existente.mesmoConteudo(lancamento)) {
                return Resultado.CONFLITO;
            }
            replaysIdempotentes.incrementAndGet();
            return Resultado.JA_APLICADO;
        }
        BigDecimal saldo = saldos.get(lancamento.conta());
        if (saldo == null) {
            return Resultado.CONTA_INEXISTENTE;
        }
        if (lancamento.tipo() == TipoLancamento.DEBITO && saldo.compareTo(lancamento.valor()) < 0) {
            return Resultado.SALDO_INSUFICIENTE;
        }
        BigDecimal novo = lancamento.tipo() == TipoLancamento.DEBITO
                ? saldo.subtract(lancamento.valor())
                : saldo.add(lancamento.valor());
        saldos.put(lancamento.conta(), novo);
        lancamentos.put(lancamento.operacaoId(), lancamento);
        return Resultado.APLICADO;
    }

    public Optional<LancamentoCore> lancamento(String operacaoId) {
        return Optional.ofNullable(lancamentos.get(operacaoId));
    }

    public Optional<BigDecimal> saldo(String conta) {
        return Optional.ofNullable(saldos.get(conta));
    }

    public void registrarDescartePorDeadline() {
        descartadosPorDeadline.incrementAndGet();
    }

    public synchronized void resetar() {
        saldos.clear();
        lancamentos.clear();
        for (int i = 1; i <= 5; i++) {
            saldos.put("conta-" + i, new BigDecimal("1000000.00"));
        }
        saldos.put("conta-pobre", new BigDecimal("10.00"));
    }

    public Snapshot snapshot() {
        return new Snapshot(lancamentos.size(), descartadosPorDeadline.get(), replaysIdempotentes.get());
    }

    public record Snapshot(int lancamentos, long descartadosPorDeadline, long replaysIdempotentes) {}
}
