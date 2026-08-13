package com.cifra.razao.aplicacao;

import java.math.BigDecimal;
import java.util.List;

import com.cifra.razao.repositorio.LancamentoRepository;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confere periodicamente se os livros fecham.
 *
 * <p>Tres perguntas, todas com a mesma resposta esperada -- nada:
 * <ol>
 * <li>alguma transacao tem lancamentos que nao somam zero?</li>
 * <li>algum saldo projetado divergiu da soma do razao?</li>
 * <li>a soma de todo o razao deixou de ser zero?</li>
 * </ol>
 *
 * <p>Um sistema que so testa o caminho feliz descobre divergencia contabil pelo
 * cliente. Este job descobre sozinho.
 */
@Component
public class Reconciliacao {

	private static final Log logger = LogFactory.getLog(Reconciliacao.class);

	private final LancamentoRepository lancamentos;

	public Reconciliacao(LancamentoRepository lancamentos) {
		this.lancamentos = lancamentos;
	}

	@Transactional(readOnly = true)
	public Resultado conferir() {
		List<LancamentoRepository.TransacaoDesbalanceada> desbalanceadas = this.lancamentos.desbalanceadas();
		List<LancamentoRepository.DivergenciaDeSaldo> divergencias = this.lancamentos.divergencias();
		BigDecimal somaGeral = this.lancamentos.somarTudo();

		return new Resultado(desbalanceadas.size(), divergencias.size(), somaGeral);
	}

	@Scheduled(cron = "${cifra.reconciliacao.cron:0 */10 * * * *}")
	public void rotina() {
		Resultado resultado = conferir();

		if (resultado.fecha()) {
			logger.debug("Reconciliacao ok: razao soma zero e nenhuma divergencia.");
			return;
		}

		logger.error("RECONCILIACAO FALHOU: %d transacao(oes) desbalanceada(s), %d saldo(s) divergente(s), razao soma %s."
			.formatted(resultado.transacoesDesbalanceadas(), resultado.saldosDivergentes(), resultado.somaDoRazao()));
	}

	public record Resultado(int transacoesDesbalanceadas, int saldosDivergentes, BigDecimal somaDoRazao) {

		public boolean fecha() {
			return this.transacoesDesbalanceadas == 0
					&& this.saldosDivergentes == 0
					&& this.somaDoRazao.signum() == 0;
		}

	}

}
