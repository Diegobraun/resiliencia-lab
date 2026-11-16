package br.com.diegobraun.resiliencia.web;

import java.io.IOException;
import java.util.Map;

import br.com.diegobraun.resiliencia.compartilhado.Json;
import br.com.diegobraun.resiliencia.config.AppProperties;
import br.com.diegobraun.resiliencia.padroes.Deadline;
import br.com.diegobraun.resiliencia.padroes.DeadlineContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class DeadlineFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Timeout-Ms";

    private final AppProperties.Deadline props;
    private final Json json;

    public DeadlineFilter(AppProperties props, Json json) {
        this.props = props.deadline();
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long timeout = lerTimeout(request.getHeader(HEADER));
        if (timeout < props.minimoParaAceitarMs()) {
            RespostaErro.escrever(response, json, 504, "tempo restante insuficiente para atender",
                    Map.of("timeoutMs", timeout, "minimoMs", props.minimoParaAceitarMs()));
            return;
        }
        DeadlineContext.definir(Deadline.daquiA(timeout));
        try {
            chain.doFilter(request, response);
        } finally {
            DeadlineContext.limpar();
        }
    }

    private long lerTimeout(String valor) {
        if (valor == null || valor.isBlank()) {
            return props.padraoMs();
        }
        try {
            return Math.min(Long.parseLong(valor.trim()), props.maximoMs());
        } catch (NumberFormatException e) {
            return props.padraoMs();
        }
    }
}
