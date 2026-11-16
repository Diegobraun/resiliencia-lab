package br.com.diegobraun.resiliencia.transferencia;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record NovaTransferencia(@NotBlank String contaOrigem, @NotBlank String contaDestino,
                                @NotNull @Positive BigDecimal valor) {

    public String impressaoDigital() {
        return contaOrigem + "|" + contaDestino + "|" + valor.stripTrailingZeros().toPlainString();
    }
}
