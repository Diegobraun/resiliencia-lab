package br.com.diegobraun.resiliencia.chaos;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ChaosService {

    private final AtomicReference<Chaos> atual = new AtomicReference<>(Chaos.desligado());
    private final ObjectMapper mapper;

    public ChaosService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Chaos atual() {
        return atual.get();
    }

    public Chaos aplicar(Map<String, Object> alteracoes) {
        Map<String, Object> campos = mapper.convertValue(atual.get(), new TypeReference<>() {});
        alteracoes.keySet().stream()
                .filter(campo -> !campos.containsKey(campo))
                .findFirst()
                .ifPresent(campo -> {
                    throw new IllegalArgumentException("campo de chaos desconhecido: " + campo + ". Válidos: " + campos.keySet());
                });
        campos.putAll(alteracoes);
        Chaos novo = mapper.convertValue(campos, Chaos.class);
        atual.set(novo);
        return novo;
    }

    public Chaos resetar() {
        atual.set(Chaos.desligado());
        return atual.get();
    }

    public boolean sortear(double taxa) {
        return taxa > 0 && ThreadLocalRandom.current().nextDouble() < taxa;
    }
}
