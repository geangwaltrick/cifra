package com.cifra.identidade.dominio;

import java.time.Instant;
import java.util.UUID;

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
 * Refresh token rotativo.
 *
 * <p>Cada login abre uma familia. Cada refresh emite um token novo e revoga o
 * anterior da mesma familia. Se um token ja revogado voltar a aparecer, ele foi
 * copiado por outra pessoa -- e a familia inteira e derrubada.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "familia", nullable = false)
	private UUID familia;

	@Column(name = "expira_em", nullable = false)
	private Instant expiraEm;

	@Column(name = "revogado_em")
	private Instant revogadoEm;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	/** Exigido pelo JPA. */
	protected RefreshToken() {
	}

	public RefreshToken(Usuario usuario, String tokenHash, UUID familia, Instant expiraEm) {
		this.usuario = usuario;
		this.tokenHash = tokenHash;
		this.familia = familia;
		this.expiraEm = expiraEm;
	}

	public boolean ativoEm(Instant momento) {
		return this.revogadoEm == null && momento.isBefore(this.expiraEm);
	}

	public boolean jaRevogado() {
		return this.revogadoEm != null;
	}

	public void revogar(Instant momento) {
		if (this.revogadoEm == null) {
			this.revogadoEm = momento;
		}
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

	public UUID getFamilia() {
		return this.familia;
	}

	public Instant getExpiraEm() {
		return this.expiraEm;
	}

	public Instant getRevogadoEm() {
		return this.revogadoEm;
	}

}
