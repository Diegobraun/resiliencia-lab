package br.com.diegobraun.resiliencia.rollout;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rollout")
public class RolloutController {

    private final RolloutService rollout;

    public RolloutController(RolloutService rollout) {
        this.rollout = rollout;
    }

    @GetMapping
    public EstadoRollout estado() {
        return rollout.estado();
    }

    @PostMapping("/iniciar")
    public EstadoRollout iniciar() {
        return rollout.iniciar();
    }

    @PostMapping("/abortar")
    public EstadoRollout abortar(@RequestParam(defaultValue = "manual") String motivo) {
        return rollout.abortar(motivo);
    }
}
