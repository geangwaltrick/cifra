package com.cifra.identidade.api;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.identidade.repositorio.ContaRepository;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contas")
public class ContaController {

	private final ContaRepository contas;

	public ContaController(ContaRepository contas) {
		this.contas = contas;
	}

	@GetMapping("/me")
	public ContaResponse minhaConta(@AuthenticationPrincipal Jwt jwt) {
		Long usuarioId = Long.valueOf(jwt.getSubject());

		return this.contas.findByUsuarioId(usuarioId)
			.map(ContaResponse::de)
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-nao-encontrada",
					"Nenhuma conta vinculada a este usuario."));
	}

}
