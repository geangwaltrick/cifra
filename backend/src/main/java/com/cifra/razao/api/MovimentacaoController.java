package com.cifra.razao.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.comum.auditoria.Auditoria;
import com.cifra.identidade.aplicacao.SenhaTransacional;
import com.cifra.identidade.dominio.Conta;
import com.cifra.identidade.repositorio.ContaRepository;
import com.cifra.razao.aplicacao.Razao;
import com.cifra.razao.dominio.Limite;
import com.cifra.razao.dominio.Saldo;
import com.cifra.razao.repositorio.LancamentoRepository;
import com.cifra.razao.repositorio.LimiteRepository;
import com.cifra.razao.repositorio.SaldoRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MovimentacaoController {

	private static final ZoneId FUSO_DO_BANCO = ZoneId.of("America/Sao_Paulo");

	private final Razao razao;

	private final ContaRepository contas;

	private final SaldoRepository saldos;

	private final LimiteRepository limites;

	private final LancamentoRepository lancamentos;

	private final SenhaTransacional senhaTransacional;

	private final Auditoria auditoria;

	public MovimentacaoController(Razao razao, ContaRepository contas, SaldoRepository saldos,
			LimiteRepository limites, LancamentoRepository lancamentos, SenhaTransacional senhaTransacional,
			Auditoria auditoria) {
		this.razao = razao;
		this.contas = contas;
		this.saldos = saldos;
		this.limites = limites;
		this.lancamentos = lancamentos;
		this.senhaTransacional = senhaTransacional;
		this.auditoria = auditoria;
	}

	@PostMapping("/depositos")
	@ResponseStatus(HttpStatus.CREATED)
	public TransacaoResponse depositar(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Idempotency-Key", required = false) String chave,
			@Valid @RequestBody MovimentacaoRequest requisicao, HttpServletRequest http) {

		// Deposito entra dinheiro: nao exige senha de movimentacao.
		Conta conta = minhaConta(jwt);
		TransacaoResponse resposta = TransacaoResponse.de(
				this.razao.depositar(conta.getId(), requisicao.valor(), chave, requisicao.descricao()));

		auditar(jwt, "DEPOSITO", resposta, requisicao.valor(), http);
		return resposta;
	}

	@PostMapping("/saques")
	@ResponseStatus(HttpStatus.CREATED)
	public TransacaoResponse sacar(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Idempotency-Key", required = false) String chave,
			@RequestHeader(value = "X-Senha-Transacional", required = false) String senha,
			@Valid @RequestBody MovimentacaoRequest requisicao, HttpServletRequest http) {

		this.senhaTransacional.exigir(usuarioId(jwt), senha);

		Conta conta = minhaConta(jwt);
		TransacaoResponse resposta = TransacaoResponse.de(
				this.razao.sacar(conta.getId(), requisicao.valor(), chave, requisicao.descricao()));

		auditar(jwt, "SAQUE", resposta, requisicao.valor(), http);
		return resposta;
	}

	@PostMapping("/transferencias")
	@ResponseStatus(HttpStatus.CREATED)
	public TransacaoResponse transferir(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Idempotency-Key", required = false) String chave,
			@RequestHeader(value = "X-Senha-Transacional", required = false) String senha,
			@Valid @RequestBody TransferenciaRequest requisicao, HttpServletRequest http) {

		this.senhaTransacional.exigir(usuarioId(jwt), senha);

		Conta origem = minhaConta(jwt);
		Conta destino = this.contas
			.findByAgenciaAndNumero(requisicao.agenciaDestino(), requisicao.contaDestino())
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-destino-nao-encontrada",
					"Conta de destino nao encontrada."));

		TransacaoResponse resposta = TransacaoResponse.de(this.razao.transferir(origem.getId(), destino.getId(),
				requisicao.valor(), chave, requisicao.descricao()));

		auditar(jwt, "TRANSFERENCIA", resposta, requisicao.valor(), http);
		return resposta;
	}

	@PostMapping("/transacoes/{id}/estorno")
	@ResponseStatus(HttpStatus.CREATED)
	public TransacaoResponse estornar(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") Long id,
			@RequestHeader(value = "Idempotency-Key", required = false) String chave,
			@RequestHeader(value = "X-Senha-Transacional", required = false) String senha,
			HttpServletRequest http) {

		this.senhaTransacional.exigir(usuarioId(jwt), senha);
		exigirParticipacaoNaTransacao(minhaConta(jwt).getId(), id);

		TransacaoResponse resposta = TransacaoResponse.de(this.razao.estornar(id, chave));

		this.auditoria.registrar(usuarioId(jwt), "ESTORNO", "transacao:" + id,
				Map.of("estornoId", resposta.id(), "original", id), http.getRemoteAddr());

		return resposta;
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

	@GetMapping("/contas/me/limite")
	public LimiteResponse limite(@AuthenticationPrincipal Jwt jwt) {
		Conta conta = minhaConta(jwt);
		Limite limite = this.limites.findById(conta.getId()).orElseGet(() -> Limite.padraoPara(conta.getId()));
		BigDecimal gastoHoje = this.lancamentos.somarSaidasDesde(conta.getId(), inicioDoDia());

		return new LimiteResponse(limite.getLimiteDiario(), gastoHoje, limite.disponivelSobre(gastoHoje));
	}

	@PutMapping("/contas/me/limite")
	public LimiteResponse ajustarLimite(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "X-Senha-Transacional", required = false) String senha,
			@Valid @RequestBody LimiteRequest requisicao, HttpServletRequest http) {

		this.senhaTransacional.exigir(usuarioId(jwt), senha);

		Conta conta = minhaConta(jwt);
		Limite limite = this.limites.findById(conta.getId()).orElseGet(() -> Limite.padraoPara(conta.getId()));
		limite.ajustarPara(requisicao.limiteDiario());
		this.limites.save(limite);

		this.auditoria.registrar(usuarioId(jwt), "LIMITE_ALTERADO", "conta:" + conta.getId(),
				Map.of("novoLimite", requisicao.limiteDiario().toString()), http.getRemoteAddr());

		BigDecimal gastoHoje = this.lancamentos.somarSaidasDesde(conta.getId(), inicioDoDia());
		return new LimiteResponse(limite.getLimiteDiario(), gastoHoje, limite.disponivelSobre(gastoHoje));
	}

	/** Sem isto, qualquer autenticado estornaria a transacao de qualquer outro. */
	private void exigirParticipacaoNaTransacao(Long contaId, Long transacaoId) {
		if (!this.lancamentos.existsByTransacaoIdAndContaId(transacaoId, contaId)) {
			throw ProblemaDeNegocio.naoEncontrado("transacao-nao-encontrada", "Transacao nao encontrada.");
		}
	}

	private void auditar(Jwt jwt, String acao, TransacaoResponse resposta, BigDecimal valor,
			HttpServletRequest http) {
		this.auditoria.registrar(usuarioId(jwt), acao, "transacao:" + resposta.id(),
				Map.of("valor", valor.toString()), http.getRemoteAddr());
	}

	private Conta minhaConta(Jwt jwt) {
		return this.contas.findByUsuarioId(usuarioId(jwt))
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-nao-encontrada",
					"Nenhuma conta vinculada a este usuario."));
	}

	private static Long usuarioId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

	private static Instant inicioDoDia() {
		return LocalDate.now(FUSO_DO_BANCO).atStartOfDay(FUSO_DO_BANCO).toInstant();
	}

	public record LimiteRequest(

			@NotNull(message = "Informe o limite diario.")
			@DecimalMin(value = "0.00", message = "O limite nao pode ser negativo.")
			BigDecimal limiteDiario) {
	}

	public record LimiteResponse(BigDecimal limiteDiario, BigDecimal gastoHoje, BigDecimal disponivelHoje) {
	}

}
