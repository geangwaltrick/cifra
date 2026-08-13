package com.cifra.razao.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferenciaRequest(

		@NotBlank(message = "Informe a agencia de destino.")
		@Size(min = 4, max = 4, message = "A agencia tem 4 digitos.")
		String agenciaDestino,

		@NotBlank(message = "Informe a conta de destino.")
		@Size(max = 12, message = "Numero de conta invalido.")
		String contaDestino,

		@NotNull(message = "Informe o valor.")
		@DecimalMin(value = "0.01", message = "O valor deve ser de ao menos R$ 0,01.")
		BigDecimal valor,

		@Size(max = 180, message = "Descricao longa demais.")
		String descricao) {

}
