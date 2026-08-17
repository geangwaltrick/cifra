package com.cifra.cartao.dominio;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "cartoes")
public class Cartao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "conta_id", nullable = false, updatable = false, unique = true)
	private Long contaId;

	@Column(name = "numero", nullable = false, length = 16, updatable = false, unique = true)
	private String numero;

	@Column(name = "cvv", nullable = false, length = 4, updatable = false)
	private String cvv;

	@Column(name = "titular", nullable = false, length = 120)
	private String titular;

	@Column(name = "validade", nullable = false, updatable = false)
	private LocalDate validade;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private StatusCartao status;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	/** Exigido pelo JPA. */
	protected Cartao() {
	}

	public Cartao(Long contaId, String numero, String cvv, String titular, LocalDate validade) {
		this.contaId = contaId;
		this.numero = numero;
		this.cvv = cvv;
		this.titular = titular;
		this.validade = validade;
		this.status = StatusCartao.ATIVO;
	}

	/** **** **** **** 4321 -- o que se mostra numa lista. */
	public String mascarado() {
		return "**** **** **** " + this.numero.substring(this.numero.length() - 4);
	}

	/** 4321 5678 9012 3456 -- so quando o titular pede para ver. */
	public String formatado() {
		return this.numero.replaceAll("(.{4})(?=.)", "$1 ");
	}

	public String validadeFormatada() {
		return "%02d/%02d".formatted(this.validade.getMonthValue(), this.validade.getYear() % 100);
	}

	public boolean expirado() {
		return LocalDate.now().isAfter(this.validade);
	}

	public boolean utilizavel() {
		return this.status == StatusCartao.ATIVO && !expirado();
	}

	public void bloquear() {
		if (this.status == StatusCartao.ATIVO) {
			this.status = StatusCartao.BLOQUEADO;
		}
	}

	public void desbloquear() {
		if (this.status.podeSerDesbloqueado()) {
			this.status = StatusCartao.ATIVO;
		}
	}

	public Long getId() {
		return this.id;
	}

	public Long getContaId() {
		return this.contaId;
	}

	public String getNumero() {
		return this.numero;
	}

	public String getCvv() {
		return this.cvv;
	}

	public String getTitular() {
		return this.titular;
	}

	public LocalDate getValidade() {
		return this.validade;
	}

	public StatusCartao getStatus() {
		return this.status;
	}

	public Instant getCriadoEm() {
		return this.criadoEm;
	}

	@PrePersist
	void aoCriar() {
		this.criadoEm = Instant.now();
	}

}
