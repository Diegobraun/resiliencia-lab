package br.com.diegobraun.resiliencia.outbox;

public record EventoOutbox(long id, String eventoId, String agregadoId, String tipo, String payload) {}
