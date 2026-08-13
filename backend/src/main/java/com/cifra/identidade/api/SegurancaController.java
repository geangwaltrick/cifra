package com.cifra.identidade.api;

import java.util.Map;

import com.cifra.comum.auditoria.Auditoria;
import com.cifra.identidade.aplicacao.SenhaTransacional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seguranca")
public class SegurancaController {

	private final SenhaTransacional senhaTransacional;

	private final Auditoria auditoria;

	public SegurancaController(SenhaTransacional senhaTransacional, Auditoria auditoria) {
		this.senhaTransacional = senhaTransacional;
		this.auditoria = auditoria;
	}

	@PostMapping("/senha-transacional")
	public Confirmacao definir(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody DefinicaoRequest requisicao, HttpServletRequest http) {

		Long usuarioId = Long.valueOf(jwt.getSubject());
		this.senhaTransacional.definir(usuarioId, requisicao.senhaDeAcesso(), requisicao.senhaTransacional());

		this.auditoria.registrar(usuarioId, "SENHA_TRANSACIONAL_DEFINIDA", "usuario:" + usuarioId, Map.of(),
				http.getRemoteAddr());

		return new Confirmacao("Senha de movimentacao definida. "
				+ "A partir de agora ela e exigida no cabecalho X-Senha-Transacional para mover dinheiro.");
	}

	public record DefinicaoRequest(

			@NotBlank(message = "Informe sua senha de acesso.")
			String senhaDeAcesso,

			@NotBlank(message = "Escolha a senha de movimentacao.")
			@Size(min = 6, max = 72, message = "A senha de movimentacao deve ter entre 6 e 72 caracteres.")
			String senhaTransacional) {
	}

	public record Confirmacao(String mensagem) {
	}

}
