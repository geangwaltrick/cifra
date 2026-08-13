package com.cifra.razao.dominio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Teto de saida de dinheiro por dia, por conta. */
@Entity
@Table(name = "limites")
public class Limite {

	public static final BigDecimal PADRAO = new BigDecimal("5000.00");

	@Id
	@Column(name = "conta_id", updatable = false)
	private Long contaId;

	@Column(name = "limite_diario", nullable = false, precision = 18, scale = 2)
	private BigDecimal limiteDiario;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	/** Exigido pelo JPA. */
	protected Limite() {
	}

	public Limite(Long contaId, BigDecimal limiteDiario) {
		this.contaId = contaId;
		this.limiteDiario = limiteDiario;
		this.atualizadoEm = Instant.now();
	}

	public static Limite padraoPara(Long contaId) {
		return new Limite(contaId, PADRAO);
	}

	public boolean comporta(BigDecimal jaGastoHoje, BigDecimal novaSaida) {
		return jaGastoHoje.add(novaSaida).compareTo(this.limiteDiario) <= 0;
	}

	public BigDecimal disponivelSobre(BigDecimal jaGastoHoje) {
		return this.limiteDiario.subtract(jaGastoHoje).max(BigDecimal.ZERO);
	}

	public void ajustarPara(BigDecimal novoLimite) {
		this.limiteDiario = novoLimite;
	}

	@PreUpdate
	void aoAtualizar() {
		this.atualizadoEm = Instant.now();
	}

	public Long getContaId() {
		return this.contaId;
	}

	public BigDecimal getLimiteDiario() {
		return this.limiteDiario;
	}

	public Instant getAtualizadoEm() {
		return this.atualizadoEm;
	}

}
