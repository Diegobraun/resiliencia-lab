package br.com.diegobraun.resiliencia.estabilidade;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import br.com.diegobraun.resiliencia.chaos.ChaosService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/control-plane/config")
public class ControlPlaneController {

    private final ChaosService chaos;
    private final AtomicReference<ConfigOperacional> config =
            new AtomicReference<>(new ConfigOperacional(1, new BigDecimal("5000"), Map.of()));

    public ControlPlaneController(ChaosService chaos) {
        this.chaos = chaos;
    }

    @GetMapping
    public ResponseEntity<?> obter() {
        if (chaos.atual().controlPlaneForaDoAr()) {
            return ResponseEntity.status(503).body(Map.of("erro", "control plane fora do ar"));
        }
        return ResponseEntity.ok(config.get());
    }

    @PutMapping
    public ConfigOperacional alterar(@RequestBody AlteracaoConfig alteracao) {
        return config.updateAndGet(atual -> new ConfigOperacional(
                atual.versao() + 1,
                alteracao.valorMaximoTransferencia() != null ? alteracao.valorMaximoTransferencia() : atual.valorMaximoTransferencia(),
                alteracao.clientesFixados() != null ? alteracao.clientesFixados() : atual.clientesFixados()));
    }

    public record AlteracaoConfig(BigDecimal valorMaximoTransferencia, Map<String, Integer> clientesFixados) {}
}
