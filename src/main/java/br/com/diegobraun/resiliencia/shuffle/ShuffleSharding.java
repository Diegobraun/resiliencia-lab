package br.com.diegobraun.resiliencia.shuffle;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import br.com.diegobraun.resiliencia.compartilhado.Hashes;
import br.com.diegobraun.resiliencia.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShuffleSharding {

    private final int workers;
    private final int tamanhoShard;
    private final int capacidadePorWorker;
    private final Semaphore[] permissoes;
    private final AtomicLong rejeitadas = new AtomicLong();

    @Autowired
    public ShuffleSharding(AppProperties props) {
        this(props.shuffleSharding().workers(), props.shuffleSharding().tamanhoShard(),
                props.shuffleSharding().capacidadePorWorker());
    }

    public ShuffleSharding(int workers, int tamanhoShard, int capacidadePorWorker) {
        if (tamanhoShard > workers) {
            throw new IllegalArgumentException("tamanho do shard maior que a quantidade de workers");
        }
        this.workers = workers;
        this.tamanhoShard = tamanhoShard;
        this.capacidadePorWorker = capacidadePorWorker;
        this.permissoes = IntStream.range(0, workers).mapToObj(i -> new Semaphore(capacidadePorWorker)).toArray(Semaphore[]::new);
    }

    public int[] shardDe(String cliente) {
        int[] todos = IntStream.range(0, workers).toArray();
        Random random = new Random(Hashes.fnv1a(cliente));
        for (int i = 0; i < tamanhoShard; i++) {
            int j = i + random.nextInt(workers - i);
            int troca = todos[i];
            todos[i] = todos[j];
            todos[j] = troca;
        }
        int[] shard = Arrays.copyOf(todos, tamanhoShard);
        Arrays.sort(shard);
        return shard;
    }

    public Optional<Integer> adquirir(String cliente) {
        for (int worker : shardDe(cliente)) {
            if (permissoes[worker].tryAcquire()) {
                return Optional.of(worker);
            }
        }
        rejeitadas.incrementAndGet();
        return Optional.empty();
    }

    public void liberar(int worker) {
        permissoes[worker].release();
    }

    public AnaliseShuffleSharding analisar(int quantidadeClientes, String barulhento) {
        int[] shardBarulhento = shardDe(barulhento);
        int grupos = workers / tamanhoShard;
        int grupoBarulhento = Hashes.bucket(barulhento, grupos);
        int outros = 0;
        int totalmente = 0;
        int parcialmente = 0;
        int afetadosShardingSimples = 0;
        for (int i = 0; i < quantidadeClientes; i++) {
            String cliente = "cliente-" + i;
            if (cliente.equals(barulhento)) {
                continue;
            }
            outros++;
            int[] shard = shardDe(cliente);
            long emComum = Arrays.stream(shard).filter(w -> Arrays.stream(shardBarulhento).anyMatch(b -> b == w)).count();
            if (emComum == tamanhoShard) {
                totalmente++;
            } else if (emComum > 0) {
                parcialmente++;
            }
            if (Hashes.bucket(cliente, grupos) == grupoBarulhento) {
                afetadosShardingSimples++;
            }
        }
        return new AnaliseShuffleSharding(
                workers,
                tamanhoShard,
                combinacoes(workers, tamanhoShard),
                barulhento,
                Arrays.stream(shardBarulhento).boxed().toList(),
                outros,
                percentual(totalmente, outros),
                percentual(parcialmente, outros),
                percentual(afetadosShardingSimples, outros));
    }

    private static double percentual(int parte, int total) {
        return total == 0 ? 0 : Math.round(parte * 10000.0 / total) / 100.0;
    }

    private static long combinacoes(int n, int k) {
        long resultado = 1;
        for (int i = 1; i <= k; i++) {
            resultado = resultado * (n - k + i) / i;
        }
        return resultado;
    }

    public Snapshot snapshot() {
        List<Integer> emUso = Arrays.stream(permissoes).map(s -> capacidadePorWorker - s.availablePermits()).toList();
        return new Snapshot(workers, tamanhoShard, capacidadePorWorker, emUso, rejeitadas.get());
    }

    public record Snapshot(int workers, int tamanhoShard, int capacidadePorWorker, List<Integer> emUsoPorWorker,
                           long rejeitadas) {}
}
