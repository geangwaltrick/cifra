package com.cifra.identidade.dominio;

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

/** Link de confirmacao de e-mail. Guarda o hash do token, nunca o token. */
@Entity
@Table(name = "tokens_verificacao_email")
public class TokenDeVerificacao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "expira_em", nullable = false)
	private Instant expiraEm;

	@Column(name = "usado_em")
	private Instant usadoEm;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	/** Exigido pelo JPA. */
	protected TokenDeVerificacao() {
	}

	public TokenDeVerificacao(Usuario usuario, String tokenHash, Instant expiraEm) {
		this.usuario = usuario;
		this.tokenHash = tokenHash;
		this.expiraEm = expiraEm;
	}

	public boolean utilizavelEm(Instant momento) {
		return this.usadoEm == null && momento.isBefore(this.expiraEm);
	}

	public void marcarComoUsado(Instant momento) {
		this.usadoEm = momento;
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

	public Instant getExpiraEm() {
		return this.expiraEm;
	}

	public Instant getUsadoEm() {
		return this.usadoEm;
	}

}
