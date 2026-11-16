package br.com.diegobraun.resiliencia.estabilidade;

import java.time.Duration;
import java.util.Map;

import br.com.diegobraun.resiliencia.compartilhado.Json;
import br.com.diegobraun.resiliencia.compartilhado.UrlBase;
import br.com.diegobraun.resiliencia.config.AppProperties;
import br.com.diegobraun.resiliencia.gateway.HttpJson;
import br.com.diegobraun.resiliencia.gateway.HttpResposta;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ControlPlaneSync {

    private final HttpJson http;
    private final UrlBase urlBase;
    private final AppProperties props;
    private final Json json;
    private final ConfigOperacionalHolder holder;

    public ControlPlaneSync(HttpJson http, UrlBase urlBase, AppProperties props, Json json,
                            ConfigOperacionalHolder holder) {
        this.http = http;
        this.urlBase = urlBase;
        this.props = props;
        this.json = json;
        this.holder = holder;
    }

    @Scheduled(fixedDelayString = "${app.control-plane.intervalo-ms}", initialDelay = 1000)
    public void sincronizar() {
        try {
            HttpResposta resposta = http.get(
                    urlBase.resolver(props.controlPlane().url()) + "/control-plane/config",
                    Map.of(),
                    Duration.ofMillis(props.controlPlane().timeoutMs()));
            if (resposta.status() != 200) {
                holder.registrarFalha("control plane respondeu " + resposta.status());
                return;
            }
            holder.atualizar(json.ler(resposta.corpo(), ConfigOperacional.class));
        } catch (HttpJson.FalhaHttp | IllegalArgumentException e) {
            holder.registrarFalha(e.getMessage());
        }
    }
}
