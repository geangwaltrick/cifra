package com.cifra.razao.api;

import java.math.BigDecimal;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.identidade.dominio.Conta;
import com.cifra.identidade.repositorio.ContaRepository;
import com.cifra.razao.aplicacao.Razao;
import com.cifra.razao.dominio.Saldo;
import com.cifra.razao.repositorio.LancamentoRepository;
import com.cifra.razao.repositorio.SaldoRepository;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MovimentacaoController {

	private final Razao razao;

	private final ContaRepository contas;

	private final SaldoRepository saldos;

	private final LancamentoRepository lancamentos;

	public MovimentacaoController(Razao razao, ContaRepository contas, SaldoRepository saldos,
			LancamentoRepository lancamentos) {
		this.razao = razao;
		this.contas = contas;
		this.saldos = saldos;
		this.lancamentos = lancamentos;
	}

	@PostMapping("/depositos")
	@ResponseStatus(HttpStatus.CREATED)
	public TransacaoResponse depositar(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Idempotency-Key", required = false) String chave,
			@Valid @RequestBody MovimentacaoRequest requisicao) {

		Conta conta = minhaConta(jwt);
		return TransacaoResponse.de(
				this.razao.depositar(conta.getId(), requisicao.valor(), chave, requisicao.descricao()));
	}

	@PostMapping("/saques")
	@ResponseStatus(HttpStatus.CREATED)
	public TransacaoResponse sacar(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Idempotency-Key", required = false) String chave,
			@Valid @RequestBody MovimentacaoRequest requisicao) {

		Conta conta = minhaConta(jwt);
		return TransacaoResponse.de(
				this.razao.sacar(conta.getId(), requisicao.valor(), chave, requisicao.descricao()));
	}

	@PostMapping("/transferencias")
	@ResponseStatus(HttpStatus.CREATED)
	public TransacaoResponse transferir(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Idempotency-Key", required = false) String chave,
			@Valid @RequestBody TransferenciaRequest requisicao) {

		Conta origem = minhaConta(jwt);
		Conta destino = this.contas
			.findByAgenciaAndNumero(requisicao.agenciaDestino(), requisicao.contaDestino())
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-destino-nao-encontrada",
					"Conta de destino nao encontrada."));

		return TransacaoResponse.de(this.razao.transferir(origem.getId(), destino.getId(), requisicao.valor(),
				chave, requisicao.descricao()));
	}

	@GetMapping("/contas/me/saldo")
	public SaldoResponse saldo(@AuthenticationPrincipal Jwt jwt) {
		Conta conta = minhaConta(jwt);

		Saldo saldo = this.saldos.findById(conta.getId())
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-sem-saldo",
					"Conta sem registro de saldo."));

		// O segundo valor vem de somar o razao. Os dois sempre coincidem -- e
		// expor os dois lado a lado e o jeito mais direto de mostrar por que.
		BigDecimal conferido = this.lancamentos.somarPorConta(conta.getId());

		return new SaldoResponse(conta.getId(), conta.identificacao(), saldo.getSaldo(), conferido,
				saldo.getAtualizadoEm());
	}

	private Conta minhaConta(Jwt jwt) {
		return this.contas.findByUsuarioId(Long.valueOf(jwt.getSubject()))
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-nao-encontrada",
					"Nenhuma conta vinculada a este usuario."));
	}

}
