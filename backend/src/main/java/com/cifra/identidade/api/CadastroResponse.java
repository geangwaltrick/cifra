package com.cifra.identidade.api;

import com.cifra.identidade.aplicacao.CadastroDeUsuario;

public record CadastroResponse(Long usuarioId, String nome, String email, String cpf, String status,
		ContaResponse conta, String proximoPasso) {

	public static CadastroResponse de(CadastroDeUsuario.Resultado resultado) {
		return new CadastroResponse(
				resultado.usuario().getId(),
				resultado.usuario().getNome(),
				resultado.usuario().getEmail(),
				resultado.usuario().getCpf().mascarado(),
				resultado.usuario().getStatus().name(),
				ContaResponse.de(resultado.conta()),
				"Confirme seu e-mail para ativar a conta.");
	}

}
