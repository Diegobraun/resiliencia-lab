package br.com.diegobraun.resiliencia.transferencia;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import br.com.diegobraun.resiliencia.celulas.CelulaRouter;
import br.com.diegobraun.resiliencia.compartilhado.Json;
import br.com.diegobraun.resiliencia.core.LancamentoCore;
import br.com.diegobraun.resiliencia.core.TipoLancamento;
import br.com.diegobraun.resiliencia.estabilidade.ConfigOperacional;
import br.com.diegobraun.resiliencia.estabilidade.ConfigOperacionalHolder;
import br.com.diegobraun.resiliencia.gateway.CoreGateway;
import br.com.diegobraun.resiliencia.gateway.ResultadoLancamento;
import br.com.diegobraun.resiliencia.outbox.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferenciaSaga {

    private final TransferenciaRepository repository;
    private final OutboxRepository outbox;
    private final CoreGateway core;
    private final CelulaRouter celulas;
    private final ConfigOperacionalHolder config;
    private final TransactionTemplate tx;
    private final Json json;

    public TransferenciaSaga(TransferenciaRepository repository, OutboxRepository outbox, CoreGateway core,
                             CelulaRouter celulas, ConfigOperacionalHolder config, TransactionTemplate tx, Json json) {
        this.repository = repository;
        this.outbox = outbox;
        this.core = core;
        this.celulas = celulas;
        this.config = config;
        this.tx = tx;
        this.json = json;
    }

    public Transferencia transferir(String cliente, NovaTransferencia pedido) {
        ConfigOperacional atual = config.atual();
        if (pedido.valor().compareTo(atual.valorMaximoTransferencia()) > 0) {
            throw new LimiteExcedidoException(pedido.valor(), atual.valorMaximoTransferencia(), atual.versao());
        }
        Instant agora = Instant.now();
        Transferencia t = new Transferencia(UUID.randomUUID().toString(), cliente, pedido.contaOrigem(),
                pedido.contaDestino(), pedido.valor(), StatusTransferencia.INICIADA, null,
                celulas.celulaDe(cliente), agora, agora);
        tx.executeWithoutResult(s -> {
            repository.inserir(t);
            outbox.registrar(t.id(), "transferencia.iniciada", payload(t, StatusTransferencia.INICIADA, null));
        });
        return debitar(t);
    }

    public Transferencia debitar(Transferencia t) {
        ResultadoLancamento r = core.lancar(t.cliente(),
                new LancamentoCore(t.operacaoDebito(), t.contaOrigem(), TipoLancamento.DEBITO, t.valor()));
        return switch (r.tipo()) {
            case SUCESSO -> creditar(mudar(t, StatusTransferencia.DEBITADA, null));
            case REJEITADO -> mudar(t, StatusTransferencia.REJEITADA, r.motivo());
            case NAO_ENVIADO -> mudar(t, StatusTransferencia.FALHOU, r.motivo());
            case INCERTO -> mudar(t, StatusTransferencia.DEBITO_INCERTO, r.motivo());
        };
    }

    public Transferencia creditar(Transferencia t) {
        ResultadoLancamento r = core.lancar(t.cliente(),
                new LancamentoCore(t.operacaoCredito(), t.contaDestino(), TipoLancamento.CREDITO, t.valor()));
        return switch (r.tipo()) {
            case SUCESSO -> mudar(t, StatusTransferencia.CONCLUIDA, null);
            case REJEITADO -> estornar(mudar(t, StatusTransferencia.COMPENSANDO, r.motivo()));
            case NAO_ENVIADO -> t.status() == StatusTransferencia.DEBITADA
                    ? mudar(t, StatusTransferencia.DEBITADA, "crédito não enviado, reconciliação tentará de novo: " + r.motivo())
                    : t;
            case INCERTO -> t.status() == StatusTransferencia.CREDITO_INCERTO
                    ? t
                    : mudar(t, StatusTransferencia.CREDITO_INCERTO, r.motivo());
        };
    }

    public Transferencia estornar(Transferencia t) {
        ResultadoLancamento r = core.lancar(t.cliente(),
                new LancamentoCore(t.operacaoEstorno(), t.contaOrigem(), TipoLancamento.CREDITO, t.valor()));
        return switch (r.tipo()) {
            case SUCESSO -> mudar(t, StatusTransferencia.COMPENSADA, t.motivo());
            case REJEITADO -> mudar(t, StatusTransferencia.COMPENSACAO_FALHOU, r.motivo());
            case NAO_ENVIADO, INCERTO -> t.status() == StatusTransferencia.COMPENSACAO_PENDENTE
                    ? t
                    : mudar(t, StatusTransferencia.COMPENSACAO_PENDENTE, t.motivo() + " | estorno: " + r.motivo());
        };
    }

    public Transferencia mudar(Transferencia t, StatusTransferencia para, String motivo) {
        tx.executeWithoutResult(s -> {
            if (!repository.atualizarStatus(t.id(), t.status(), para, motivo)) {
                throw new ConcorrenciaException(t.id(), t.status());
            }
            outbox.registrar(t.id(), "transferencia." + para.name().toLowerCase(), payload(t, para, motivo));
        });
        return t.com(para, motivo);
    }

    private String payload(Transferencia t, StatusTransferencia status, String motivo) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("transferenciaId", t.id());
        dados.put("cliente", t.cliente());
        dados.put("status", status);
        dados.put("valor", t.valor());
        dados.put("contaOrigem", t.contaOrigem());
        dados.put("contaDestino", t.contaDestino());
        if (motivo != null) {
            dados.put("motivo", motivo);
        }
        return json.escrever(dados);
    }
}
