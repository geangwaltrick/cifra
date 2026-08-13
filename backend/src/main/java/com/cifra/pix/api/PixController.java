package com.cifra.pix.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.comum.auditoria.Auditoria;
import com.cifra.identidade.aplicacao.SenhaTransacional;
import com.cifra.identidade.dominio.Conta;
import com.cifra.identidade.repositorio.ContaRepository;
import com.cifra.pix.aplicacao.ChavesPix;
import com.cifra.pix.dominio.ChavePix;
import com.cifra.pix.dominio.TipoChavePix;
import com.cifra.razao.aplicacao.Razao;
import com.cifra.razao.api.TransacaoResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pix")
public class PixController {

	private final ChavesPix chaves;

	private final Razao razao;

	private final ContaRepository contas;

	private final SenhaTransacional senhaTransacional;

	private final Auditoria auditoria;

	public PixController(ChavesPix chaves, Razao razao, ContaRepository contas,
			SenhaTransacional senhaTransacional, Auditoria auditoria) {
		this.chaves = chaves;
		this.razao = razao;
		this.contas = contas;
		this.senhaTransacional = senhaTransacional;
		this.auditoria = auditoria;
	}

	@PostMapping("/chaves")
	@ResponseStatus(HttpStatus.CREATED)
	public ChavePixResponse registrar(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ChavePixRequest requisicao) {

		Conta conta = minhaConta(jwt);
		ChavePix chave = this.chaves.registrar(conta, requisicao.tipo(), requisicao.valor());

		this.auditoria.registrar(usuarioId(jwt), "PIX_CHAVE_REGISTRADA", "chave:" + chave.getId(),
				Map.of("tipo", chave.getTipo().name(), "contaId", conta.getId()));

		return ChavePixResponse.de(chave, true);
	}

	@GetMapping("/chaves")
	public List<ChavePixResponse> listar(@AuthenticationPrincipal Jwt jwt) {
		return this.chaves.listar(minhaConta(jwt).getId()).stream()
			.map((chave) -> ChavePixResponse.de(chave, true))
			.toList();
	}

	@DeleteMapping("/chaves/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remover(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") Long id) {
		this.chaves.remover(minhaConta(jwt).getId(), id);
		this.auditoria.registrar(usuarioId(jwt), "PIX_CHAVE_REMOVIDA", "chave:" + id, Map.of());
	}

	@PostMapping("/transferencias")
	@ResponseStatus(HttpStatus.CREATED)
	public TransacaoResponse pagar(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestHeader(value = "X-Senha-Transacional", required = false) String senha,
			@Valid @RequestBody PixRequest requisicao, HttpServletRequest http) {

		Long usuarioId = usuarioId(jwt);
		this.senhaTransacional.exigir(usuarioId, senha);

		Conta origem = minhaConta(jwt);
		Conta destino = this.chaves.resolver(requisicao.chave());

		TransacaoResponse resposta = TransacaoResponse.de(this.razao.pagarPix(origem.getId(), destino.getId(),
				requisicao.valor(), idempotencyKey, requisicao.descricao()));

		this.auditoria.registrar(usuarioId, "PIX_ENVIADO", "transacao:" + resposta.id(),
				Map.of("valor", requisicao.valor().toString(), "destino", destino.identificacao()),
				http.getRemoteAddr());

		return resposta;
	}

	private Conta minhaConta(Jwt jwt) {
		return this.contas.findByUsuarioId(usuarioId(jwt))
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-nao-encontrada",
					"Nenhuma conta vinculada a este usuario."));
	}

	private static Long usuarioId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

	public record ChavePixRequest(

			@NotNull(message = "Informe o tipo da chave.")
			TipoChavePix tipo,

			@Size(max = 140, message = "Chave longa demais.")
			String valor) {
	}

	public record PixRequest(

			@NotBlank(message = "Informe a chave de destino.")
			String chave,

			@NotNull(message = "Informe o valor.")
			@DecimalMin(value = "0.01", message = "O valor deve ser de ao menos R$ 0,01.")
			BigDecimal valor,

			@Size(max = 180, message = "Descricao longa demais.")
			String descricao) {
	}

	public record ChavePixResponse(Long id, String tipo, String valor, Instant criadoEm) {

		/**
		 * O valor so aparece inteiro para o proprio dono. Em qualquer outro
		 * contexto vai mascarado: chave de CPF e telefone identifica pessoa.
		 */
		static ChavePixResponse de(ChavePix chave, boolean ehDono) {
			return new ChavePixResponse(chave.getId(), chave.getTipo().name(),
					ehDono ? chave.getValor() : chave.mascarado(), chave.getCriadoEm());
		}

	}

}
