package br.com.diegobraun.resiliencia.transferencia;

public class ConcorrenciaException extends RuntimeException {

    public ConcorrenciaException(String id, StatusTransferencia esperado) {
        super("transferência " + id + " não está mais em " + esperado);
    }
}
