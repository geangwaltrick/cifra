package com.cifra.razao.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.cifra.razao.dominio.Transacao;

public record TransacaoResponse(Long id, String tipo, String status, BigDecimal valor, String descricao,
		Instant liquidadaEm, List<LancamentoResponse> lancamentos, BigDecimal somaDosLancamentos) {

	public static TransacaoResponse de(Transacao transacao) {
		List<LancamentoResponse> linhas = transacao.getLancamentos().stream()
			.map((lancamento) -> new LancamentoResponse(lancamento.getContaId(), lancamento.getValor()))
			.toList();

		// A soma vai na resposta de proposito: e sempre zero, e mostrar isso e
		// mais convincente do que afirmar.
		return new TransacaoResponse(transacao.getId(), transacao.getTipo().name(), transacao.getStatus().name(),
				transacao.getValorTotal(), transacao.getDescricao(), transacao.getLiquidadoEm(), linhas,
				transacao.somaDosLancamentos());
	}

	public record LancamentoResponse(Long contaId, BigDecimal valor) {
	}

}
