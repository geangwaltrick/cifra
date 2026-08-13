package com.cifra.identidade.api;

import com.cifra.identidade.aplicacao.Autenticacao;
import com.cifra.identidade.aplicacao.CadastroDeUsuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacaoController {

	private final CadastroDeUsuario cadastro;

	private final Autenticacao autenticacao;

	public AutenticacaoController(CadastroDeUsuario cadastro, Autenticacao autenticacao) {
		this.cadastro = cadastro;
		this.autenticacao = autenticacao;
	}

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public CadastroResponse registrar(@Valid @RequestBody CadastroRequest requisicao) {
		return CadastroResponse.de(this.cadastro.cadastrar(
				requisicao.nome(), requisicao.cpf(), requisicao.email(), requisicao.senha()));
	}

	@PostMapping("/login")
	public TokensResponse entrar(@Valid @RequestBody LoginRequest requisicao, HttpServletRequest http) {
		return TokensResponse.de(this.autenticacao.entrar(
				requisicao.email(), requisicao.senha(), http.getRemoteAddr()));
	}

	@PostMapping("/refresh")
	public TokensResponse renovar(@Valid @RequestBody RenovacaoRequest requisicao) {
		return TokensResponse.de(this.autenticacao.renovar(requisicao.refreshToken()));
	}

	@GetMapping("/verificar-email")
	public ConfirmacaoResponse verificarEmail(@RequestParam("token") String token) {
		this.autenticacao.confirmarEmail(token);
		return new ConfirmacaoResponse("E-mail confirmado. Sua conta esta ativa.");
	}

	public record ConfirmacaoResponse(String mensagem) {
	}

}
