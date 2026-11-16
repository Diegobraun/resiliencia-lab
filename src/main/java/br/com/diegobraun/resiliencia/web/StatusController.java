package br.com.diegobraun.resiliencia.web;

import java.util.LinkedHashMap;
import java.util.Map;

import br.com.diegobraun.resiliencia.chaos.ChaosService;
import br.com.diegobraun.resiliencia.core.CoreSimulado;
import br.com.diegobraun.resiliencia.estabilidade.ConfigOperacionalHolder;
import br.com.diegobraun.resiliencia.gateway.CoreGateway;
import br.com.diegobraun.resiliencia.outbox.BrokerSimulado;
import br.com.diegobraun.resiliencia.outbox.OutboxRelay;
import br.com.diegobraun.resiliencia.padroes.RetryPolicy;
import br.com.diegobraun.resiliencia.reconciliacao.ReconciliacaoService;
import br.com.diegobraun.resiliencia.rollout.RolloutService;
import br.com.diegobraun.resiliencia.shuffle.ShuffleSharding;
import br.com.diegobraun.resiliencia.transferencia.TransferenciaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private final CoreGateway core;
    private final RetryPolicy retry;
    private final LoadSheddingFilter shedding;
    private final ShuffleSharding sharding;
    private final OutboxRelay relay;
    private final BrokerSimulado broker;
    private final ConfigOperacionalHolder config;
    private final RolloutService rollout;
    private final TransferenciaRepository transferencias;
    private final ReconciliacaoService reconciliacao;
    private final CoreSimulado coreSimulado;
    private final ChaosService chaos;

    public StatusController(CoreGateway core, RetryPolicy retry, LoadSheddingFilter shedding, ShuffleSharding sharding,
                            OutboxRelay relay, BrokerSimulado broker, ConfigOperacionalHolder config,
                            RolloutService rollout, TransferenciaRepository transferencias,
                            ReconciliacaoService reconciliacao, CoreSimulado coreSimulado, ChaosService chaos) {
        this.core = core;
        this.retry = retry;
        this.shedding = shedding;
        this.sharding = sharding;
        this.relay = relay;
        this.broker = broker;
        this.config = config;
        this.rollout = rollout;
        this.transferencias = transferencias;
        this.reconciliacao = reconciliacao;
        this.coreSimulado = coreSimulado;
        this.chaos = chaos;
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("circuitBreakers", core.breakers());
        status.put("bulkheads", core.bulkheads());
        status.put("retry", retry.snapshot());
        status.put("loadShedding", shedding.snapshot());
        status.put("shuffleSharding", sharding.snapshot());
        status.put("outbox", relay.snapshot());
        status.put("broker", broker.snapshot());
        status.put("configOperacional", config.snapshot());
        status.put("rollout", rollout.estado());
        status.put("transferenciasPorStatus", transferencias.contarPorStatus());
        status.put("ultimaReconciliacao", reconciliacao.ultimo());
        status.put("coreSimulado", coreSimulado.snapshot());
        status.put("chaos", chaos.atual());
        return status;
    }
}
