package com.cifra.razao.aplicacao;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Expoe a reconciliacao em /actuator/health.
 *
 * <p>A saude da aplicacao passa a incluir uma pergunta contabil: os livros
 * fecham? Se o razao deixar de somar zero, o monitoramento acusa junto com
 * banco fora do ar -- que e mais ou menos a gravidade do problema.
 */
@Component("razao")
public class SaudeDoRazao implements HealthIndicator {

	private final Reconciliacao reconciliacao;

	public SaudeDoRazao(Reconciliacao reconciliacao) {
		this.reconciliacao = reconciliacao;
	}

	@Override
	public Health health() {
		Reconciliacao.Resultado resultado = this.reconciliacao.conferir();

		Health.Builder saude = resultado.fecha() ? Health.up() : Health.down();

		return saude.withDetail("somaDoRazao", resultado.somaDoRazao())
			.withDetail("transacoesDesbalanceadas", resultado.transacoesDesbalanceadas())
			.withDetail("saldosDivergentes", resultado.saldosDivergentes())
			.build();
	}

}
