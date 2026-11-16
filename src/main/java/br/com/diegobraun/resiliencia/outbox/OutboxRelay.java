package br.com.diegobraun.resiliencia.outbox;

import java.util.concurrent.atomic.AtomicLong;

import br.com.diegobraun.resiliencia.chaos.ChaosService;
import br.com.diegobraun.resiliencia.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay {

    private final OutboxRepository repository;
    private final BrokerSimulado broker;
    private final ChaosService chaos;
    private final int lote;
    private final AtomicLong publicados = new AtomicLong();
    private final AtomicLong pausasPorBackpressure = new AtomicLong();
    private final AtomicLong falhasAntesDeMarcar = new AtomicLong();

    public OutboxRelay(OutboxRepository repository, BrokerSimulado broker, ChaosService chaos, AppProperties props) {
        this.repository = repository;
        this.broker = broker;
        this.chaos = chaos;
        this.lote = props.outbox().lote();
    }

    @Scheduled(fixedDelayString = "${app.outbox.intervalo-ms}")
    public void publicarPendentes() {
        for (EventoOutbox evento : repository.pendentes(lote)) {
            if (!broker.publicar(evento)) {
                pausasPorBackpressure.incrementAndGet();
                return;
            }
            if (chaos.sortear(chaos.atual().taxaFalhaRelay())) {
                falhasAntesDeMarcar.incrementAndGet();
                return;
            }
            repository.marcarPublicado(evento.id());
            publicados.incrementAndGet();
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(repository.contarPendentes(), publicados.get(), pausasPorBackpressure.get(),
                falhasAntesDeMarcar.get());
    }

    public record Snapshot(long pendentes, long publicados, long pausasPorBackpressure, long falhasAntesDeMarcar) {}
}
