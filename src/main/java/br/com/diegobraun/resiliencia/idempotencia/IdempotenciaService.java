package br.com.diegobraun.resiliencia.idempotencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import br.com.diegobraun.resiliencia.compartilhado.Hashes;
import br.com.diegobraun.resiliencia.config.AppProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class IdempotenciaService {

    private final JdbcClient jdbc;
    private final long expiracaoEmAndamentoMs;

    public IdempotenciaService(JdbcClient jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.expiracaoEmAndamentoMs = props.idempotencia().expiracaoEmAndamentoMs();
    }

    public RespostaArmazenada executar(String cliente, String chave, String impressaoDigital,
                                       Supplier<RespostaArmazenada> operacao) {
        String hash = Hashes.sha256(impressaoDigital);
        if (!reservar(cliente, chave, hash)) {
            Optional<RespostaArmazenada> replay = tratarExistente(cliente, chave, hash);
            if (replay.isPresent()) {
                return replay.get();
            }
        }
        try {
            RespostaArmazenada resposta = operacao.get();
            if (resposta.armazenar()) {
                concluir(cliente, chave, resposta);
            } else {
                remover(cliente, chave);
            }
            return resposta;
        } catch (RuntimeException e) {
            remover(cliente, chave);
            throw e;
        }
    }

    private Optional<RespostaArmazenada> tratarExistente(String cliente, String chave, String hash) {
        Registro registro = buscar(cliente, chave).orElse(null);
        if (registro == null) {
            if (reservar(cliente, chave, hash)) {
                return Optional.empty();
            }
            throw new RequisicaoEmAndamentoException(chave);
        }
        if (!registro.hash().equals(hash)) {
            throw new ChaveReutilizadaException(chave);
        }
        if ("CONCLUIDA".equals(registro.status())) {
            return Optional.of(new RespostaArmazenada(registro.statusHttp(), registro.resposta(), true, true));
        }
        boolean travaVencida = registro.criadoEm().isBefore(Instant.now().minusMillis(expiracaoEmAndamentoMs));
        if (travaVencida && assumir(cliente, chave, registro.criadoEm())) {
            return Optional.empty();
        }
        throw new RequisicaoEmAndamentoException(chave);
    }

    private boolean reservar(String cliente, String chave, String hash) {
        try {
            jdbc.sql("""
                    insert into idempotencia (cliente, chave, hash_requisicao, status, criado_em)
                    values (:cliente, :chave, :hash, 'EM_ANDAMENTO', :agora)
                    """)
                    .param("cliente", cliente)
                    .param("chave", chave)
                    .param("hash", hash)
                    .param("agora", Timestamp.from(Instant.now()))
                    .update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private boolean assumir(String cliente, String chave, Instant criadoEmAnterior) {
        return jdbc.sql("""
                update idempotencia set criado_em = :agora
                where cliente = :cliente and chave = :chave and status = 'EM_ANDAMENTO' and criado_em = :anterior
                """)
                .param("agora", Timestamp.from(Instant.now()))
                .param("cliente", cliente)
                .param("chave", chave)
                .param("anterior", Timestamp.from(criadoEmAnterior))
                .update() == 1;
    }

    private void concluir(String cliente, String chave, RespostaArmazenada resposta) {
        jdbc.sql("""
                update idempotencia set status = 'CONCLUIDA', status_http = :status, resposta = :resposta
                where cliente = :cliente and chave = :chave
                """)
                .param("status", resposta.status())
                .param("resposta", resposta.corpo())
                .param("cliente", cliente)
                .param("chave", chave)
                .update();
    }

    private void remover(String cliente, String chave) {
        jdbc.sql("delete from idempotencia where cliente = :cliente and chave = :chave and status = 'EM_ANDAMENTO'")
                .param("cliente", cliente)
                .param("chave", chave)
                .update();
    }

    private Optional<Registro> buscar(String cliente, String chave) {
        return jdbc.sql("select hash_requisicao, status, status_http, resposta, criado_em from idempotencia where cliente = :cliente and chave = :chave")
                .param("cliente", cliente)
                .param("chave", chave)
                .query((rs, i) -> new Registro(
                        rs.getString("hash_requisicao"),
                        rs.getString("status"),
                        rs.getInt("status_http"),
                        rs.getString("resposta"),
                        rs.getTimestamp("criado_em").toInstant()))
                .optional();
    }

    private record Registro(String hash, String status, int statusHttp, String resposta, Instant criadoEm) {}
}
