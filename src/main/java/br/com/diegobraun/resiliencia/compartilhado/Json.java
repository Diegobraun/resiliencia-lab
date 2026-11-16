package br.com.diegobraun.resiliencia.compartilhado;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class Json {

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String escrever(Object valor) {
        try {
            return mapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public <T> T ler(String json, Class<T> tipo) {
        try {
            return mapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON inválido: " + e.getOriginalMessage(), e);
        }
    }

    public String campo(String json, String nome) {
        try {
            var no = mapper.readTree(json);
            return no != null && no.hasNonNull(nome) ? no.get(nome).asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
