package com.cifra.cartao.api;

import com.cifra.cartao.aplicacao.EmissorDeCartao;
import com.cifra.cartao.dominio.Cartao;
import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.identidade.dominio.Conta;
import com.cifra.identidade.repositorio.ContaRepository;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cartoes")
public class CartaoController {

	private final EmissorDeCartao emissor;

	private final ContaRepository contas;

	public CartaoController(EmissorDeCartao emissor, ContaRepository contas) {
		this.emissor = emissor;
		this.contas = contas;
	}

	/**
	 * O cartao da conta, emitido na primeira consulta.
	 *
	 * <p>Numero completo e CVV so saem com {@code revelar=true}, e a chamada
	 * precisa ser deliberada. Assim a tela pode listar o cartao mascarado sem
	 * nunca trazer o dado sensivel para a memoria do navegador.
	 */
	@GetMapping("/me")
	public CartaoResponse meuCartao(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(name = "revelar", defaultValue = "false") boolean revelar) {

		Conta conta = minhaConta(jwt);
		Cartao cartao = this.emissor.doTitular(conta, jwt.getClaimAsString("nome"));

		return CartaoResponse.de(cartao, revelar);
	}

	@PostMapping("/me/bloqueio")
	public CartaoResponse bloquear(@AuthenticationPrincipal Jwt jwt) {
		return CartaoResponse.de(this.emissor.bloquear(minhaConta(jwt)), false);
	}

	@PostMapping("/me/desbloqueio")
	public CartaoResponse desbloquear(@AuthenticationPrincipal Jwt jwt) {
		return CartaoResponse.de(this.emissor.desbloquear(minhaConta(jwt)), false);
	}

	private Conta minhaConta(Jwt jwt) {
		return this.contas.findByUsuarioId(Long.valueOf(jwt.getSubject()))
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-nao-encontrada",
					"Nenhuma conta vinculada a este usuario."));
	}

}
