package com.cifra.identidade.api;

import com.cifra.identidade.dominio.Conta;

public record ContaResponse(Long id, String agencia, String numero, String identificacao, String tipo,
		String status) {

	public static ContaResponse de(Conta conta) {
		return new ContaResponse(conta.getId(), conta.getAgencia(), conta.getNumero(), conta.identificacao(),
				conta.getTipo().name(), conta.getStatus().name());
	}

}
