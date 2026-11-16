package br.com.diegobraun.resiliencia.chaos;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chaos")
public class ChaosController {

    private final ChaosService chaos;

    public ChaosController(ChaosService chaos) {
        this.chaos = chaos;
    }

    @GetMapping
    public Chaos atual() {
        return chaos.atual();
    }

    @PutMapping
    public Chaos aplicar(@RequestBody Map<String, Object> alteracoes) {
        return chaos.aplicar(alteracoes);
    }

    @DeleteMapping
    public Chaos resetar() {
        return chaos.resetar();
    }
}
