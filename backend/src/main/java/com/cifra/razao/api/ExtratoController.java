package com.cifra.razao.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.identidade.dominio.Conta;
import com.cifra.identidade.repositorio.ContaRepository;
import com.cifra.razao.repositorio.ExtratoRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contas/me/extrato")
public class ExtratoController {

	private static final int TAMANHO_MAXIMO = 100;

	private final ExtratoRepository extrato;

	private final ContaRepository contas;

	public ExtratoController(ExtratoRepository extrato, ContaRepository contas) {
		this.extrato = extrato;
		this.contas = contas;
	}

	@GetMapping
	public ExtratoResponse consultar(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(value = "de", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
			@RequestParam(value = "ate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
			@RequestParam(value = "tipo", required = false) String tipo,
			@RequestParam(value = "pagina", defaultValue = "0") int pagina,
			@RequestParam(value = "tamanho", defaultValue = "25") int tamanho) {

		Conta conta = this.contas.findByUsuarioId(Long.valueOf(jwt.getSubject()))
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-nao-encontrada",
					"Nenhuma conta vinculada a este usuario."));

		Page<ExtratoRepository.LinhaDoExtrato> linhas = this.extrato.extrato(conta.getId(), de, ate,
				(tipo == null || tipo.isBlank()) ? null : tipo.toUpperCase(),
				PageRequest.of(Math.max(pagina, 0), Math.clamp(tamanho, 1, TAMANHO_MAXIMO)));

		return ExtratoResponse.de(conta.identificacao(), linhas);
	}

	public record ExtratoResponse(String conta, List<Linha> linhas, int pagina, int tamanho, long total,
			int totalDePaginas) {

		static ExtratoResponse de(String conta, Page<ExtratoRepository.LinhaDoExtrato> pagina) {
			List<Linha> linhas = pagina.getContent().stream().map(Linha::de).toList();

			return new ExtratoResponse(conta, linhas, pagina.getNumber(), pagina.getSize(),
					pagina.getTotalElements(), pagina.getTotalPages());
		}

		public record Linha(Long id, Instant data, Long transacaoId, String tipo, String status, String descricao,
				BigDecimal valor, String sentido, BigDecimal saldoApos, String contraparte) {

			static Linha de(ExtratoRepository.LinhaDoExtrato origem) {
				return new Linha(origem.getId(), origem.getData(), origem.getTransacaoId(), origem.getTipo(),
						origem.getStatus(), origem.getDescricao(), origem.getValor(),
						(origem.getValor().signum() < 0) ? "DEBITO" : "CREDITO", origem.getSaldoApos(),
						origem.getContraparte());
			}

		}

	}

}
