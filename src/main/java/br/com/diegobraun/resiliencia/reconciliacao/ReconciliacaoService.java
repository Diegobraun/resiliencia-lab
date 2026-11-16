package br.com.diegobraun.resiliencia.reconciliacao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import br.com.diegobraun.resiliencia.config.AppProperties;
import br.com.diegobraun.resiliencia.gateway.CoreGateway;
import br.com.diegobraun.resiliencia.padroes.Deadline;
import br.com.diegobraun.resiliencia.padroes.DeadlineContext;
import br.com.diegobraun.resiliencia.transferencia.ConcorrenciaException;
import br.com.diegobraun.resiliencia.transferencia.StatusTransferencia;
import br.com.diegobraun.resiliencia.transferencia.Transferencia;
import br.com.diegobraun.resiliencia.transferencia.TransferenciaRepository;
import br.com.diegobraun.resiliencia.transferencia.TransferenciaSaga;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static br.com.diegobraun.resiliencia.transferencia.StatusTransferencia.*;

@Service
public class ReconciliacaoService {

    private static final Set<StatusTransferencia> PENDENTES = EnumSet.of(
            INICIADA, DEBITO_INCERTO, DEBITADA, CREDITO_INCERTO, COMPENSANDO, COMPENSACAO_PENDENTE);

    private final TransferenciaRepository repository;
    private final TransferenciaSaga saga;
    private final CoreGateway core;
    private final AppProperties.Reconciliacao props;
    private volatile RelatorioReconciliacao ultimo;

    public ReconciliacaoService(TransferenciaRepository repository, TransferenciaSaga saga, CoreGateway core,
                                AppProperties props) {
        this.repository = repository;
        this.saga = saga;
        this.core = core;
        this.props = props.reconciliacao();
    }

    @Scheduled(fixedDelayString = "${app.reconciliacao.intervalo-ms}", initialDelayString = "${app.reconciliacao.intervalo-ms}")
    public void agendada() {
        executarPadrao();
    }

    public RelatorioReconciliacao executarPadrao() {
        return executar(props.idadeMinimaMs());
    }

    public synchronized RelatorioReconciliacao executar(long idadeMinimaMs) {
        List<Transferencia> candidatas = repository.paraReconciliar(PENDENTES, Instant.now().minusMillis(idadeMinimaMs), 200);
        List<String> acoes = new ArrayList<>();
        int resolvidas = 0;
        for (Transferencia t : candidatas) {
            try {
                Transferencia depois = DeadlineContext.com(Deadline.daquiA(props.deadlineMs()), () -> reconciliar(t));
                if (depois.status() != t.status()) {
                    resolvidas++;
                    acoes.add(t.id() + ": " + t.status() + " -> " + depois.status());
                }
            } catch (ConcorrenciaException e) {
                acoes.add(t.id() + ": ignorada, " + e.getMessage());
            }
        }
        ultimo = new RelatorioReconciliacao(Instant.now(), candidatas.size(), resolvidas, candidatas.size() - resolvidas, acoes);
        return ultimo;
    }

    private Transferencia reconciliar(Transferencia t) {
        return switch (t.status()) {
            case INICIADA, DEBITO_INCERTO -> switch (core.consultar(t.cliente(), t.operacaoDebito())) {
                case EXISTE -> saga.creditar(saga.mudar(t, DEBITADA, "reconciliação: débito encontrado no core"));
                case NAO_EXISTE -> saga.mudar(t, FALHOU, "reconciliação: débito nunca chegou ao core");
                case DESCONHECIDO -> t;
            };
            case DEBITADA, CREDITO_INCERTO -> switch (core.consultar(t.cliente(), t.operacaoCredito())) {
                case EXISTE -> saga.mudar(t, CONCLUIDA, "reconciliação: crédito encontrado no core");
                case NAO_EXISTE -> saga.creditar(t);
                case DESCONHECIDO -> t;
            };
            case COMPENSANDO, COMPENSACAO_PENDENTE -> switch (core.consultar(t.cliente(), t.operacaoEstorno())) {
                case EXISTE -> saga.mudar(t, COMPENSADA, t.motivo());
                case NAO_EXISTE -> saga.estornar(t);
                case DESCONHECIDO -> t;
            };
            default -> t;
        };
    }

    public RelatorioReconciliacao ultimo() {
        return ultimo;
    }
}
