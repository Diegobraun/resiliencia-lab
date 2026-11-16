package br.com.diegobraun.resiliencia.estabilidade;

import java.math.BigDecimal;
import java.util.Map;

public record ConfigOperacional(long versao, BigDecimal valorMaximoTransferencia, Map<String, Integer> clientesFixados) {

    public ConfigOperacional {
        clientesFixados = clientesFixados == null ? Map.of() : Map.copyOf(clientesFixados);
    }
}
