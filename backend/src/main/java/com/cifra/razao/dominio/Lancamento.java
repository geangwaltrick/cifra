package com.cifra.razao.dominio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Uma linha do razao. Negativo debita, positivo credita.
 *
 * <p>Nao ha setter nem operacao de alteracao: lancamento e imutavel depois de
 * escrito. Corrigir e lancar o contrario, nao apagar.
 */
@Entity
@Table(name = "lancamentos")
public class Lancamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "transacao_id", nullable = false, updatable = false)
	private Transacao transacao;

	@Column(name = "conta_id", nullable = false, updatable = false)
	private Long contaId;

	@Column(name = "valor", nullable = false, updatable = false, precision = 18, scale = 2)
	private BigDecimal valor;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	/** Exigido pelo JPA. */
	protected Lancamento() {
	}

	public Lancamento(Transacao transacao, Long contaId, BigDecimal valor) {
		if (valor.signum() == 0) {
			throw new IllegalArgumentException("Lancamento de valor zero nao movimenta nada.");
		}
		this.transacao = transacao;
		this.contaId = contaId;
		this.valor = valor;
	}

	public boolean ehDebito() {
		return this.valor.signum() < 0;
	}

	@PrePersist
	void aoCriar() {
		this.criadoEm = Instant.now();
	}

	public Long getId() {
		return this.id;
	}

	public Transacao getTransacao() {
		return this.transacao;
	}

	public Long getContaId() {
		return this.contaId;
	}

	public BigDecimal getValor() {
		return this.valor;
	}

	public Instant getCriadoEm() {
		return this.criadoEm;
	}

}
