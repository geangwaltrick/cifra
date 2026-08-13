package com.cifra.pix.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Apelido que endereca uma conta. O valor e unico no sistema inteiro. */
@Entity
@Table(name = "chaves_pix")
public class ChavePix {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "conta_id", nullable = false, updatable = false)
	private Long contaId;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 16, updatable = false)
	private TipoChavePix tipo;

	@Column(name = "valor", nullable = false, length = 140, unique = true, updatable = false)
	private String valor;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	/** Exigido pelo JPA. */
	protected ChavePix() {
	}

	public ChavePix(Long contaId, TipoChavePix tipo, String valor) {
		this.contaId = contaId;
		this.tipo = tipo;
		this.valor = valor;
	}

	/** Nunca mostra a chave inteira em lista: CPF e telefone identificam pessoa. */
	public String mascarado() {
		return switch (this.tipo) {
			case CPF -> "***.%s.%s-**".formatted(this.valor.substring(3, 6), this.valor.substring(6, 9));
			case EMAIL -> mascararEmail();
			case TELEFONE -> "(%s) *****-%s".formatted(this.valor.substring(0, 2),
					this.valor.substring(this.valor.length() - 4));
			case ALEATORIA -> this.valor;
		};
	}

	private String mascararEmail() {
		int arroba = this.valor.indexOf('@');
		if (arroba <= 1) {
			return "***" + this.valor.substring(Math.max(arroba, 0));
		}
		return this.valor.charAt(0) + "***" + this.valor.substring(arroba);
	}

	@PrePersist
	void aoCriar() {
		this.criadoEm = Instant.now();
	}

	public Long getId() {
		return this.id;
	}

	public Long getContaId() {
		return this.contaId;
	}

	public TipoChavePix getTipo() {
		return this.tipo;
	}

	public String getValor() {
		return this.valor;
	}

	public Instant getCriadoEm() {
		return this.criadoEm;
	}

}
