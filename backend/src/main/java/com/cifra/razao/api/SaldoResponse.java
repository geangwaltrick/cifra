package com.cifra.razao.api;

import java.math.BigDecimal;
import java.time.Instant;

public record SaldoResponse(Long contaId, String identificacao, BigDecimal saldo, BigDecimal saldoConferidoNoRazao,
		Instant atualizadoEm) {

}
