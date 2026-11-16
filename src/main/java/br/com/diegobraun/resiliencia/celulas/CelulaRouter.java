package br.com.diegobraun.resiliencia.celulas;

import br.com.diegobraun.resiliencia.compartilhado.Hashes;
import br.com.diegobraun.resiliencia.config.AppProperties;
import br.com.diegobraun.resiliencia.estabilidade.ConfigOperacionalHolder;
import org.springframework.stereotype.Component;

@Component
public class CelulaRouter {

    private final int quantidade;
    private final ConfigOperacionalHolder config;

    public CelulaRouter(AppProperties props, ConfigOperacionalHolder config) {
        this.quantidade = props.celulas().quantidade();
        this.config = config;
    }

    public int celulaDe(String cliente) {
        Integer fixada = config.atual().clientesFixados().get(cliente);
        if (fixada != null && fixada >= 0 && fixada < quantidade) {
            return fixada;
        }
        return Hashes.bucket(cliente, quantidade);
    }

    public int quantidade() {
        return quantidade;
    }
}
