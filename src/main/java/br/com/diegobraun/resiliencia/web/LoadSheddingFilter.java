package br.com.diegobraun.resiliencia.web;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import br.com.diegobraun.resiliencia.compartilhado.Json;
import br.com.diegobraun.resiliencia.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(2)
public class LoadSheddingFilter extends OncePerRequestFilter {

    public enum Prioridade {
        BAIXA(0.5), NORMAL(0.85), CRITICA(1.0);

        final double fracaoDoLimite;

        Prioridade(double fracaoDoLimite) {
            this.fracaoDoLimite = fracaoDoLimite;
        }
    }

    private final int limite;
    private final Json json;
    private final AtomicInteger emVoo = new AtomicInteger();
    private final Map<Prioridade, AtomicLong> rejeitadas = new EnumMap<>(Prioridade.class);
    private final AtomicInteger picoEmVoo = new AtomicInteger();

    public LoadSheddingFilter(AppProperties props, Json json) {
        this.limite = props.loadShedding().limiteConcorrencia();
        this.json = json;
        for (Prioridade p : Prioridade.values()) {
            rejeitadas.put(p, new AtomicLong());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/api/status");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Prioridade prioridade = prioridade(request.getHeader("X-Prioridade"));
        int atual = emVoo.incrementAndGet();
        if (atual > limite * prioridade.fracaoDoLimite) {
            emVoo.decrementAndGet();
            rejeitadas.get(prioridade).incrementAndGet();
            response.setHeader("Retry-After", "1");
            RespostaErro.escrever(response, json, 503, "sobrecarga: requisição descartada",
                    Map.of("prioridade", prioridade, "emVoo", atual - 1, "limite", limite));
            return;
        }
        picoEmVoo.accumulateAndGet(atual, Math::max);
        try {
            chain.doFilter(request, response);
        } finally {
            emVoo.decrementAndGet();
        }
    }

    private Prioridade prioridade(String valor) {
        if (valor == null) {
            return Prioridade.NORMAL;
        }
        try {
            return Prioridade.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Prioridade.NORMAL;
        }
    }

    public Snapshot snapshot() {
        Map<Prioridade, Long> porPrioridade = new EnumMap<>(Prioridade.class);
        rejeitadas.forEach((p, n) -> porPrioridade.put(p, n.get()));
        return new Snapshot(limite, emVoo.get(), picoEmVoo.get(), porPrioridade);
    }

    public record Snapshot(int limite, int emVoo, int picoEmVoo, Map<Prioridade, Long> rejeitadasPorPrioridade) {}
}
