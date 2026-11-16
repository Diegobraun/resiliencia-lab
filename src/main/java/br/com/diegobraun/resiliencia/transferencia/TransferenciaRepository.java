package br.com.diegobraun.resiliencia.transferencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TransferenciaRepository {

    private final JdbcClient jdbc;

    public TransferenciaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void inserir(Transferencia t) {
        jdbc.sql("""
                insert into transferencia (id, cliente, conta_origem, conta_destino, valor, status, motivo, celula, criado_em, atualizado_em)
                values (:id, :cliente, :origem, :destino, :valor, :status, :motivo, :celula, :criadoEm, :atualizadoEm)
                """)
                .param("id", t.id())
                .param("cliente", t.cliente())
                .param("origem", t.contaOrigem())
                .param("destino", t.contaDestino())
                .param("valor", t.valor())
                .param("status", t.status().name())
                .param("motivo", t.motivo())
                .param("celula", t.celula())
                .param("criadoEm", Timestamp.from(t.criadoEm()))
                .param("atualizadoEm", Timestamp.from(t.atualizadoEm()))
                .update();
    }

    public boolean atualizarStatus(String id, StatusTransferencia de, StatusTransferencia para, String motivo) {
        return jdbc.sql("""
                update transferencia set status = :para, motivo = :motivo, atualizado_em = :agora
                where id = :id and status = :de
                """)
                .param("para", para.name())
                .param("motivo", motivo)
                .param("agora", Timestamp.from(Instant.now()))
                .param("id", id)
                .param("de", de.name())
                .update() == 1;
    }

    public Optional<Transferencia> buscar(String id) {
        return jdbc.sql("select * from transferencia where id = :id")
                .param("id", id)
                .query(this::mapear)
                .optional();
    }

    public List<Transferencia> paraReconciliar(Collection<StatusTransferencia> status, Instant atualizadasAntesDe, int limite) {
        return jdbc.sql("""
                select * from transferencia
                where status in (:status) and atualizado_em < :antesDe
                order by atualizado_em
                limit :limite
                """)
                .param("status", status.stream().map(Enum::name).toList())
                .param("antesDe", Timestamp.from(atualizadasAntesDe))
                .param("limite", limite)
                .query(this::mapear)
                .list();
    }

    public Map<StatusTransferencia, Long> contarPorStatus() {
        Map<StatusTransferencia, Long> contagem = new EnumMap<>(StatusTransferencia.class);
        jdbc.sql("select status, count(*) total from transferencia group by status")
                .query((rs, i) -> contagem.put(StatusTransferencia.valueOf(rs.getString("status")), rs.getLong("total")))
                .list();
        return contagem;
    }

    private Transferencia mapear(ResultSet rs, int linha) throws SQLException {
        return new Transferencia(
                rs.getString("id"),
                rs.getString("cliente"),
                rs.getString("conta_origem"),
                rs.getString("conta_destino"),
                rs.getBigDecimal("valor"),
                StatusTransferencia.valueOf(rs.getString("status")),
                rs.getString("motivo"),
                rs.getInt("celula"),
                rs.getTimestamp("criado_em").toInstant(),
                rs.getTimestamp("atualizado_em").toInstant());
    }
}
