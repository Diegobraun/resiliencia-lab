package br.com.diegobraun.resiliencia.outbox;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import br.com.diegobraun.resiliencia.chaos.ChaosService;
import br.com.diegobraun.resiliencia.config.AppProperties;
import br.com.diegobraun.resiliencia.padroes.Dormidor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class BrokerSimulado {

    private final BlockingQueue<EventoOutbox> fila;
    private final int capacidade;
    private final ChaosService chaos;
    private final Set<String> processados = ConcurrentHashMap.newKeySet();
    private final AtomicLong totalProcessados = new AtomicLong();
    private final AtomicLong duplicadosIgnorados = new AtomicLong();
    private Thread consumidor;

    public BrokerSimulado(AppProperties props, ChaosService chaos) {
        this.capacidade = props.broker().capacidadeFila();
        this.fila = new ArrayBlockingQueue<>(capacidade);
        this.chaos = chaos;
    }

    public boolean publicar(EventoOutbox evento) {
        return fila.offer(evento);
    }

    @PostConstruct
    void iniciar() {
        consumidor = Thread.ofPlatform().daemon().name("consumidor-notificacoes").start(this::consumir);
    }

    @PreDestroy
    void parar() {
        consumidor.interrupt();
    }

    private void consumir() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                EventoOutbox evento = fila.take();
                long lento = chaos.atual().consumidorLentoMs();
                if (lento > 0) {
                    Dormidor.real().dormir(lento);
                }
                if (processados.add(evento.eventoId())) {
                    totalProcessados.incrementAndGet();
                } else {
                    duplicadosIgnorados.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(capacidade, fila.size(), totalProcessados.get(), duplicadosIgnorados.get());
    }

    public record Snapshot(int capacidadeFila, int naFila, long processados, long duplicadosIgnorados) {}
}
