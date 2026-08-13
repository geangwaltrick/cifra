package com.cifra.comum.auditoria;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Uma linha da trilha de auditoria.
 *
 * <p>O payload e jsonb para que uma acao nova possa registrar campos proprios
 * sem exigir migration -- e continue consultavel por operador.
 */
@Entity
@Table(name = "auditoria")
public class RegistroDeAuditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ator_id")
	private Long atorId;

	@Column(name = "acao", nullable = false, length = 60)
	private String acao;

	@Column(name = "recurso", length = 120)
	private String recurso;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload")
	private Map<String, Object> payload;

	@Column(name = "ip", length = 45)
	private String ip;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	/** Exigido pelo JPA. */
	protected RegistroDeAuditoria() {
	}

	public RegistroDeAuditoria(Long atorId, String acao, String recurso, Map<String, Object> payload, String ip) {
		this.atorId = atorId;
		this.acao = acao;
		this.recurso = recurso;
		this.payload = payload;
		this.ip = ip;
	}

	@PrePersist
	void aoCriar() {
		this.criadoEm = Instant.now();
	}

	public Long getId() {
		return this.id;
	}

	public Long getAtorId() {
		return this.atorId;
	}

	public String getAcao() {
		return this.acao;
	}

	public String getRecurso() {
		return this.recurso;
	}

	public Map<String, Object> getPayload() {
		return this.payload;
	}

	public String getIp() {
		return this.ip;
	}

	public Instant getCriadoEm() {
		return this.criadoEm;
	}

}
