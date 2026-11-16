package br.com.diegobraun.resiliencia.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.diegobraun.resiliencia.compartilhado.Json;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

final class RespostaErro {

    private RespostaErro() {
    }

    static void escrever(HttpServletResponse response, Json json, int status, String mensagem, Map<String, Object> extras)
            throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("erro", mensagem);
        corpo.putAll(extras);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.escrever(corpo));
    }
}
