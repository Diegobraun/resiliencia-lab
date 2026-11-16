package br.com.diegobraun.resiliencia.estabilidade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;

import br.com.diegobraun.resiliencia.compartilhado.Json;
import br.com.diegobraun.resiliencia.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConfigOperacionalHolder {

    public enum Origem { PADRAO_EMBARCADO, CACHE_DISCO, CONTROL_PLANE }

    private static final Logger log = LoggerFactory.getLogger(ConfigOperacionalHolder.class);

    private final Json json;
    private final Path cache;

    private volatile ConfigOperacional atual;
    private volatile Origem origem;
    private volatile Instant obtidaEm;
    private volatile int falhasConsecutivas;
    private volatile String ultimaFalha;

    public ConfigOperacionalHolder(AppProperties props, Json json) {
        this.json = json;
        this.cache = Path.of(props.controlPlane().cacheArquivo());
        this.atual = new ConfigOperacional(0, props.controlPlane().valorMaximoPadrao(), Map.of());
        this.origem = Origem.PADRAO_EMBARCADO;
        this.obtidaEm = Instant.now();
        carregarCache();
    }

    public ConfigOperacional atual() {
        return atual;
    }

    public void atualizar(ConfigOperacional nova) {
        falhasConsecutivas = 0;
        ultimaFalha = null;
        obtidaEm = Instant.now();
        origem = Origem.CONTROL_PLANE;
        if (nova.versao() != atual.versao()) {
            atual = nova;
            salvarCache(nova);
        }
    }

    public void registrarFalha(String motivo) {
        falhasConsecutivas++;
        ultimaFalha = motivo;
    }

    private void carregarCache() {
        if (!Files.exists(cache)) {
            return;
        }
        try {
            atual = json.ler(Files.readString(cache), ConfigOperacional.class);
            origem = Origem.CACHE_DISCO;
            obtidaEm = Files.getLastModifiedTime(cache).toInstant();
            log.info("config operacional carregada do cache em disco, versão {}", atual.versao());
        } catch (IOException | IllegalArgumentException e) {
            log.warn("cache de config ilegível, usando padrão embarcado: {}", e.getMessage());
        }
    }

    private void salvarCache(ConfigOperacional config) {
        try {
            Files.createDirectories(cache.toAbsolutePath().getParent());
            Path temporario = cache.resolveSibling(cache.getFileName() + ".tmp");
            Files.writeString(temporario, json.escrever(config));
            Files.move(temporario, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("não foi possível gravar cache de config: {}", e.getMessage());
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(atual, origem, Instant.now().toEpochMilli() - obtidaEm.toEpochMilli(),
                falhasConsecutivas, ultimaFalha);
    }

    public record Snapshot(ConfigOperacional config, Origem origem, long idadeMs, int falhasConsecutivas,
                           String ultimaFalha) {}
}
