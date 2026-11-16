package br.com.diegobraun.resiliencia.shuffle;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shuffle-sharding")
public class ShuffleShardingController {

    private final ShuffleSharding sharding;

    public ShuffleShardingController(ShuffleSharding sharding) {
        this.sharding = sharding;
    }

    @GetMapping("/clientes/{cliente}")
    public Map<String, Object> shard(@PathVariable String cliente) {
        List<Integer> workers = Arrays.stream(sharding.shardDe(cliente)).boxed().toList();
        return Map.of("cliente", cliente, "workers", workers);
    }

    @GetMapping("/analise")
    public AnaliseShuffleSharding analise(@RequestParam(defaultValue = "1000") int clientes,
                                          @RequestParam(defaultValue = "cliente-barulhento") String barulhento) {
        return sharding.analisar(clientes, barulhento);
    }
}
