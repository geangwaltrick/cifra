package com.cifra.razao.dominio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Projecao do razao para uma conta.
 *
 * <p>Nao e a fonte da verdade -- o razao e. Existe porque somar todos os
 * lancamentos a cada consulta nao escala. A reconciliacao confere periodicamente
 * se esta coluna ainda bate com sum(lancamentos.valor).
 */
@Entity
@Table(name = "saldos")
public class Saldo {

	@Id
	@Column(name = "conta_id", updatable = false)
	private Long contaId;

	@Column(name = "saldo", nullable = false, precision = 18, scale = 2)
	private BigDecimal saldo;

	@Column(name = "permite_negativo", nullable = false)
	private boolean permiteNegativo;

	/**
	 * Trava otimista por cima da pessimista. Redundante no caminho que usa
	 * SELECT FOR UPDATE, e e essa a intencao: se algum codigo futuro esquecer
	 * de travar, a versao ainda pega a atualizacao perdida.
	 */
	@Version
	@Column(name = "versao", nullable = false)
	private Long versao;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	/** Exigido pelo JPA. */
	protected Saldo() {
	}

	public Saldo(Long contaId, boolean permiteNegativo) {
		this.contaId = contaId;
		this.saldo = BigDecimal.ZERO.setScale(2);
		this.permiteNegativo = permiteNegativo;
		this.atualizadoEm = Instant.now();
	}

	public boolean comporta(BigDecimal valor) {
		return this.permiteNegativo || this.saldo.add(valor).signum() >= 0;
	}

	public void aplicar(BigDecimal valor) {
		if (!comporta(valor)) {
			throw new IllegalStateException("Movimento deixaria a conta %d negativa.".formatted(this.contaId));
		}
		this.saldo = this.saldo.add(valor);
		this.atualizadoEm = Instant.now();
	}

	@PreUpdate
	void aoAtualizar() {
		this.atualizadoEm = Instant.now();
	}

	public Long getContaId() {
		return this.contaId;
	}

	public BigDecimal getSaldo() {
		return this.saldo;
	}

	public boolean isPermiteNegativo() {
		return this.permiteNegativo;
	}

	public Long getVersao() {
		return this.versao;
	}

	public Instant getAtualizadoEm() {
		return this.atualizadoEm;
	}

}
