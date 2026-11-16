package br.com.diegobraun.resiliencia.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxRepository {

    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String agregadoId, String tipo, String payload) {
        jdbc.sql("""
                insert into outbox (evento_id, agregado_id, tipo, payload, criado_em)
                values (:eventoId, :agregadoId, :tipo, :payload, :agora)
                """)
                .param("eventoId", UUID.randomUUID().toString())
                .param("agregadoId", agregadoId)
                .param("tipo", tipo)
                .param("payload", payload)
                .param("agora", Timestamp.from(Instant.now()))
                .update();
    }

    public List<EventoOutbox> pendentes(int limite) {
        return jdbc.sql("select id, evento_id, agregado_id, tipo, payload from outbox where publicado_em is null order by id limit :limite")
                .param("limite", limite)
                .query((rs, i) -> new EventoOutbox(rs.getLong("id"), rs.getString("evento_id"),
                        rs.getString("agregado_id"), rs.getString("tipo"), rs.getString("payload")))
                .list();
    }

    public void marcarPublicado(long id) {
        jdbc.sql("update outbox set publicado_em = :agora where id = :id")
                .param("agora", Timestamp.from(Instant.now()))
                .param("id", id)
                .update();
    }

    public long contarPendentes() {
        return jdbc.sql("select count(*) from outbox where publicado_em is null").query(Long.class).single();
    }
}
