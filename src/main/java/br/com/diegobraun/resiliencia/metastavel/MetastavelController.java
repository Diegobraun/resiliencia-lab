package br.com.diegobraun.resiliencia.metastavel;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metastavel")
public class MetastavelController {

    private final SimuladorMetastavel simulador = new SimuladorMetastavel();

    @GetMapping("/simular")
    public ResultadoSimulacao simular(
            @RequestParam(defaultValue = "false") boolean mitigado,
            @RequestParam(defaultValue = "false") boolean detalhado,
            @RequestParam(defaultValue = "200") int ticks,
            @RequestParam(defaultValue = "80") int chegadasPorTick,
            @RequestParam(defaultValue = "100") int capacidade,
            @RequestParam(defaultValue = "30") int capacidadeDuranteGatilho,
            @RequestParam(defaultValue = "50") int inicioGatilho,
            @RequestParam(defaultValue = "70") int fimGatilho,
            @RequestParam(defaultValue = "10") int timeoutTicks,
            @RequestParam(defaultValue = "3") int maxTentativas,
            @RequestParam(defaultValue = "500") int limiteFila,
            @RequestParam(defaultValue = "0.1") double razaoBudget) {
        ResultadoSimulacao r = simulador.simular(new ParametrosSimulacao(ticks, chegadasPorTick, capacidade,
                capacidadeDuranteGatilho, inicioGatilho, fimGatilho, timeoutTicks, maxTentativas, mitigado, limiteFila,
                razaoBudget));
        return detalhado ? r : new ResultadoSimulacao(r.parametros(), r.recuperou(), r.tickRecuperacao(),
                r.ticksAteRecuperarAposGatilho(), r.filaMaxima(), r.goodputMedioAntesDoGatilho(),
                r.goodputMedioUltimos20Ticks(), r.amplificacaoMaximaDeCarga(), r.trabalhoDesperdicado(),
                r.graficoGoodput(), r.graficoFila(), null);
    }

    @GetMapping("/comparar")
    public Map<String, ResultadoSimulacao> comparar() {
        Map<String, ResultadoSimulacao> resultado = new LinkedHashMap<>();
        for (boolean mitigado : new boolean[] {false, true}) {
            ResultadoSimulacao r = simulador.simular(ParametrosSimulacao.padrao(mitigado));
            resultado.put(mitigado ? "mitigado" : "ingenuo", new ResultadoSimulacao(r.parametros(), r.recuperou(),
                    r.tickRecuperacao(), r.ticksAteRecuperarAposGatilho(), r.filaMaxima(),
                    r.goodputMedioAntesDoGatilho(), r.goodputMedioUltimos20Ticks(), r.amplificacaoMaximaDeCarga(),
                    r.trabalhoDesperdicado(), r.graficoGoodput(), r.graficoFila(), null));
        }
        return resultado;
    }
}
