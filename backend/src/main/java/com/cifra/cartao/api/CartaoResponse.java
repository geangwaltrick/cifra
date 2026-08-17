package com.cifra.cartao.api;

import com.cifra.cartao.dominio.Cartao;

public record CartaoResponse(Long id, String numero, String cvv, String titular, String validade, String status,
		String bandeira, boolean utilizavel, boolean revelado) {

	public static CartaoResponse de(Cartao cartao, boolean revelar) {
		return new CartaoResponse(
				cartao.getId(),
				revelar ? cartao.formatado() : cartao.mascarado(),
				// CVV nunca sai junto do numero mascarado: se o cliente nao pediu
				// para revelar, nao ha motivo para o dado trafegar.
				revelar ? cartao.getCvv() : null,
				cartao.getTitular(),
				cartao.validadeFormatada(),
				cartao.getStatus().name(),
				"CIFRA",
				cartao.utilizavel(),
				revelar);
	}

}
