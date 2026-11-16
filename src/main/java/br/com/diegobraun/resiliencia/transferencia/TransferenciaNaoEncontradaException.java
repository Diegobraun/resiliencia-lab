package br.com.diegobraun.resiliencia.transferencia;

public class TransferenciaNaoEncontradaException extends RuntimeException {

    public TransferenciaNaoEncontradaException(String id) {
        super("transferência " + id + " não encontrada");
    }
}
