package com.cifra.razao.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MovimentacaoRequest(

		@NotNull(message = "Informe o valor.")
		@DecimalMin(value = "0.01", message = "O valor deve ser de ao menos R$ 0,01.")
		BigDecimal valor,

		@Size(max = 180, message = "Descricao longa demais.")
		String descricao) {

}
