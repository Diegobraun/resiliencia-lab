package br.com.diegobraun.resiliencia.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import br.com.diegobraun.resiliencia.compartilhado.Json;
import br.com.diegobraun.resiliencia.shuffle.ShuffleSharding;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(3)
public class ShuffleShardFilter extends OncePerRequestFilter {

    private final ShuffleSharding sharding;
    private final Json json;

    public ShuffleShardFilter(ShuffleSharding sharding, Json json) {
        this.sharding = sharding;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && request.getRequestURI().equals("/api/transferencias"))
                || request.getHeader("X-Cliente") == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cliente = request.getHeader("X-Cliente");
        Optional<Integer> worker = sharding.adquirir(cliente);
        if (worker.isEmpty()) {
            response.setHeader("Retry-After", "1");
            RespostaErro.escrever(response, json, 429, "shard do cliente saturado",
                    Map.of("cliente", cliente, "workers", Arrays.stream(sharding.shardDe(cliente)).boxed().toList()));
            return;
        }
        response.setHeader("X-Worker", String.valueOf(worker.get()));
        try {
            chain.doFilter(request, response);
        } finally {
            sharding.liberar(worker.get());
        }
    }
}
