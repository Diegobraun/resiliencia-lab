package br.com.diegobraun.resiliencia.transferencia;

import br.com.diegobraun.resiliencia.compartilhado.Json;
import br.com.diegobraun.resiliencia.idempotencia.IdempotenciaService;
import br.com.diegobraun.resiliencia.idempotencia.RespostaArmazenada;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transferencias")
public class TransferenciaController {

    private final TransferenciaSaga saga;
    private final TransferenciaRepository repository;
    private final IdempotenciaService idempotencia;
    private final Json json;

    public TransferenciaController(TransferenciaSaga saga, TransferenciaRepository repository,
                                   IdempotenciaService idempotencia, Json json) {
        this.saga = saga;
        this.repository = repository;
        this.idempotencia = idempotencia;
        this.json = json;
    }

    @PostMapping
    public ResponseEntity<String> transferir(@RequestHeader("Idempotency-Key") String chave,
                                             @RequestHeader("X-Cliente") String cliente,
                                             @Valid @RequestBody NovaTransferencia pedido) {
        RespostaArmazenada resposta = idempotencia.executar(cliente, chave, pedido.impressaoDigital(), () -> {
            Transferencia t = saga.transferir(cliente, pedido);
            return RespostaArmazenada.nova(t.status().statusHttp(), json.escrever(TransferenciaResposta.de(t)),
                    t.status() != StatusTransferencia.FALHOU);
        });
        return ResponseEntity.status(resposta.status())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotent-Replayed", String.valueOf(resposta.replay()))
                .body(resposta.corpo());
    }

    @GetMapping("/{id}")
    public TransferenciaResposta buscar(@PathVariable String id) {
        return repository.buscar(id)
                .map(TransferenciaResposta::de)
                .orElseThrow(() -> new TransferenciaNaoEncontradaException(id));
    }
}
