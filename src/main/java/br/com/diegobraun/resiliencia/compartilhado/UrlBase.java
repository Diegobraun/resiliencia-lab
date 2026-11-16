package br.com.diegobraun.resiliencia.compartilhado;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class UrlBase {

    private final Environment env;

    public UrlBase(Environment env) {
        this.env = env;
    }

    public String resolver(String configurada) {
        if (configurada != null && !configurada.isBlank()) {
            return configurada;
        }
        return "http://localhost:" + env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
    }
}
