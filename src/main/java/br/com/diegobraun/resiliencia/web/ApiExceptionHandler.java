package br.com.diegobraun.resiliencia.web;

import java.util.Map;

import br.com.diegobraun.resiliencia.idempotencia.ChaveReutilizadaException;
import br.com.diegobraun.resiliencia.idempotencia.RequisicaoEmAndamentoException;
import br.com.diegobraun.resiliencia.transferencia.ConcorrenciaException;
import br.com.diegobraun.resiliencia.transferencia.LimiteExcedidoException;
import br.com.diegobraun.resiliencia.transferencia.TransferenciaNaoEncontradaException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ChaveReutilizadaException.class)
    ResponseEntity<Map<String, String>> chaveReutilizada(ChaveReutilizadaException e) {
        return erro(422, e.getMessage());
    }

    @ExceptionHandler(RequisicaoEmAndamentoException.class)
    ResponseEntity<Map<String, String>> emAndamento(RequisicaoEmAndamentoException e) {
        return ResponseEntity.status(409).header("Retry-After", "1").body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    ResponseEntity<Map<String, String>> limite(LimiteExcedidoException e) {
        return erro(422, e.getMessage());
    }

    @ExceptionHandler(TransferenciaNaoEncontradaException.class)
    ResponseEntity<Map<String, String>> naoEncontrada(TransferenciaNaoEncontradaException e) {
        return erro(404, e.getMessage());
    }

    @ExceptionHandler(ConcorrenciaException.class)
    ResponseEntity<Map<String, String>> concorrencia(ConcorrenciaException e) {
        return erro(409, e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Map<String, String>> headerAusente(MissingRequestHeaderException e) {
        return erro(400, "header obrigatório ausente: " + e.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalido(MethodArgumentNotValidException e) {
        return erro(400, "requisição inválida: " + e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .toList());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> ilegivel(Exception e) {
        return erro(400, e.getMessage());
    }

    private ResponseEntity<Map<String, String>> erro(int status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("erro", mensagem));
    }
}
