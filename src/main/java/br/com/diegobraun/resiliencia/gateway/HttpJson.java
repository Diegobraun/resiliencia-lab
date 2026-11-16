package br.com.diegobraun.resiliencia.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class HttpJson {

    private final HttpClient cliente;

    public HttpJson(HttpClient cliente) {
        this.cliente = cliente;
    }

    public HttpResposta post(String url, String corpo, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo));
        headers.forEach(builder::header);
        return enviar(builder.build(), timeout);
    }

    public HttpResposta get(String url, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET();
        headers.forEach(builder::header);
        return enviar(builder.build(), timeout);
    }

    private HttpResposta enviar(HttpRequest request, Duration timeout) {
        try {
            HttpResponse<String> resposta = cliente.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResposta(resposta.statusCode(), resposta.body());
        } catch (HttpTimeoutException e) {
            throw new FalhaHttp(true, "timeout após " + timeout.toMillis() + "ms");
        } catch (IOException e) {
            throw new FalhaHttp(false, "falha de conexão: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaHttp(false, "interrompido");
        }
    }

    public static class FalhaHttp extends RuntimeException {

        private final boolean timeout;

        public FalhaHttp(boolean timeout, String mensagem) {
            super(mensagem);
            this.timeout = timeout;
        }

        public boolean timeout() {
            return timeout;
        }
    }
}
