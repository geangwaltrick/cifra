package com.cifra.identidade.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "contas")
public class Conta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Nulo apenas na conta de liquidacao, que nao pertence a ninguem. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuario_id")
	private Usuario usuario;

	@Column(name = "agencia", nullable = false, length = 4)
	private String agencia;

	@Column(name = "numero", nullable = false, length = 12)
	private String numero;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 16)
	private TipoConta tipo;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private StatusConta status;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	/** Exigido pelo JPA. */
	protected Conta() {
	}

	public Conta(Usuario usuario, String agencia, String numero, TipoConta tipo) {
		this.usuario = usuario;
		this.agencia = agencia;
		this.numero = numero;
		this.tipo = tipo;
		this.status = StatusConta.ATIVA;
	}

	/** Identificacao de exibicao: 0001 / 47201-3 */
	public String identificacao() {
		int corte = this.numero.length() - 1;
		return "%s / %s-%s".formatted(this.agencia, this.numero.substring(0, corte), this.numero.substring(corte));
	}

	@PrePersist
	void aoCriar() {
		this.criadoEm = Instant.now();
	}

	public Long getId() {
		return this.id;
	}

	public Usuario getUsuario() {
		return this.usuario;
	}

	public String getAgencia() {
		return this.agencia;
	}

	public String getNumero() {
		return this.numero;
	}

	public TipoConta getTipo() {
		return this.tipo;
	}

	public StatusConta getStatus() {
		return this.status;
	}

	public Instant getCriadoEm() {
		return this.criadoEm;
	}

}
