package br.com.diegobraun.resiliencia.metastavel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.ToIntFunction;

public class SimuladorMetastavel {

    private static final char[] BARRAS = "▁▂▃▄▅▆▇█".toCharArray();

    public ResultadoSimulacao simular(ParametrosSimulacao p) {
        Deque<Requisicao> fila = new ArrayDeque<>();
        List<Requisicao> retriesProximoTick = new ArrayList<>();
        List<ResultadoSimulacao.Ponto> pontos = new ArrayList<>();
        double budget = 0;
        int filaMaxima = 0;
        long desperdicioTotal = 0;
        double amplificacaoMaxima = 0;

        for (int tick = 0; tick < p.ticks(); tick++) {
            boolean noGatilho = tick >= p.inicioGatilho() && tick < p.fimGatilho();
            int capacidade = noGatilho ? p.capacidadeDuranteGatilho() : p.capacidade();

            List<Requisicao> entrada = new ArrayList<>(retriesProximoTick);
            int retries = retriesProximoTick.size();
            retriesProximoTick.clear();
            for (int i = 0; i < p.chegadasPorTick(); i++) {
                entrada.add(new Requisicao(tick, 1));
                if (p.mitigado()) {
                    budget = Math.min(10, budget + p.razaoBudget());
                }
            }
            amplificacaoMaxima = Math.max(amplificacaoMaxima, (double) entrada.size() / p.chegadasPorTick());

            int rejeitadas = 0;
            for (Requisicao r : entrada) {
                if (p.mitigado() && fila.size() >= p.limiteFila()) {
                    rejeitadas++;
                } else {
                    fila.addLast(r);
                }
            }

            int processados = 0;
            int goodput = 0;
            int desperdicio = 0;
            int descartados = 0;
            while (processados < capacidade && !fila.isEmpty()) {
                Requisicao r = fila.pollFirst();
                boolean clienteDesistiu = r.desistiu || tick - r.chegada >= p.timeoutTicks();
                if (clienteDesistiu) {
                    if (p.mitigado()) {
                        descartados++;
                        continue;
                    }
                    desperdicio++;
                    processados++;
                    continue;
                }
                goodput++;
                processados++;
            }
            desperdicioTotal += desperdicio;

            for (Requisicao r : fila) {
                if (!r.desistiu && tick - r.chegada >= p.timeoutTicks() - 1) {
                    r.desistiu = true;
                    if (r.tentativa < p.maxTentativas() && (!p.mitigado() || budget >= 1)) {
                        if (p.mitigado()) {
                            budget -= 1;
                        }
                        retriesProximoTick.add(new Requisicao(tick + 1, r.tentativa + 1));
                    }
                }
            }

            filaMaxima = Math.max(filaMaxima, fila.size());
            pontos.add(new ResultadoSimulacao.Ponto(tick, capacidade, p.chegadasPorTick(), retries, fila.size(),
                    goodput, desperdicio, descartados, rejeitadas));
        }

        Integer tickRecuperacao = detectarRecuperacao(pontos, p);
        return new ResultadoSimulacao(
                p,
                tickRecuperacao != null,
                tickRecuperacao,
                tickRecuperacao == null ? null : tickRecuperacao - p.fimGatilho(),
                filaMaxima,
                media(pontos.subList(0, Math.min(p.inicioGatilho(), pontos.size()))),
                media(pontos.subList(Math.max(0, pontos.size() - 20), pontos.size())),
                Math.round(amplificacaoMaxima * 100) / 100.0,
                desperdicioTotal,
                grafico(pontos, ResultadoSimulacao.Ponto::goodput, p.capacidade()),
                grafico(pontos, ResultadoSimulacao.Ponto::fila, Math.max(1, filaMaxima)),
                pontos);
    }

    private Integer detectarRecuperacao(List<ResultadoSimulacao.Ponto> pontos, ParametrosSimulacao p) {
        int alvo = (int) Math.ceil(p.chegadasPorTick() * 0.9);
        int consecutivos = 0;
        for (int tick = p.fimGatilho(); tick < pontos.size(); tick++) {
            if (pontos.get(tick).goodput() >= alvo) {
                consecutivos++;
                if (consecutivos == 5) {
                    return tick - 4;
                }
            } else {
                consecutivos = 0;
            }
        }
        return null;
    }

    private double media(List<ResultadoSimulacao.Ponto> pontos) {
        return pontos.isEmpty() ? 0 : Math.round(pontos.stream().mapToInt(ResultadoSimulacao.Ponto::goodput).average().orElse(0) * 10) / 10.0;
    }

    private String grafico(List<ResultadoSimulacao.Ponto> pontos, ToIntFunction<ResultadoSimulacao.Ponto> valor, int maximo) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pontos.size(); i += 2) {
            double proporcao = Math.min(1.0, (double) valor.applyAsInt(pontos.get(i)) / maximo);
            sb.append(BARRAS[(int) Math.round(proporcao * (BARRAS.length - 1))]);
        }
        return sb.toString();
    }

    private static final class Requisicao {
        final int chegada;
        final int tentativa;
        boolean desistiu;

        Requisicao(int chegada, int tentativa) {
            this.chegada = chegada;
            this.tentativa = tentativa;
        }
    }
}
