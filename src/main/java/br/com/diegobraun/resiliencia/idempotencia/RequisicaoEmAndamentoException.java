package br.com.diegobraun.resiliencia.idempotencia;

public class RequisicaoEmAndamentoException extends RuntimeException {

    public RequisicaoEmAndamentoException(String chave) {
        super("requisição com Idempotency-Key '" + chave + "' ainda em andamento");
    }
}
