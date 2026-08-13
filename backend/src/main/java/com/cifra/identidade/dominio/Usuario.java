package com.cifra.identidade.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nome", nullable = false, length = 120)
	private String nome;

	@Column(name = "cpf", nullable = false, length = 11, unique = true)
	private Cpf cpf;

	@Column(name = "email", nullable = false, length = 180, unique = true)
	private String email;

	@Column(name = "senha_hash", nullable = false, length = 255)
	private String senhaHash;

	/**
	 * Senha de movimentacao, separada da de acesso. Nula ate o titular definir;
	 * a partir dai e exigida em toda saida de dinheiro.
	 */
	@Column(name = "senha_transacional_hash", length = 255)
	private String senhaTransacionalHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 24)
	private StatusUsuario status;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	/** Exigido pelo JPA. */
	protected Usuario() {
	}

	public Usuario(String nome, Cpf cpf, String email, String senhaHash) {
		this.nome = nome;
		this.cpf = cpf;
		this.email = normalizarEmail(email);
		this.senhaHash = senhaHash;
		this.status = StatusUsuario.PENDENTE_VERIFICACAO;
	}

	public static String normalizarEmail(String email) {
		return email == null ? null : email.trim().toLowerCase();
	}

	public void confirmarEmail() {
		if (this.status == StatusUsuario.PENDENTE_VERIFICACAO) {
			this.status = StatusUsuario.ATIVO;
		}
	}

	public boolean podeAutenticar() {
		return this.status.podeAutenticar();
	}

	public void definirSenhaTransacional(String hash) {
		this.senhaTransacionalHash = hash;
	}

	public boolean exigeSenhaTransacional() {
		return this.senhaTransacionalHash != null;
	}

	@PrePersist
	void aoCriar() {
		Instant agora = Instant.now();
		this.criadoEm = agora;
		this.atualizadoEm = agora;
	}

	@PreUpdate
	void aoAtualizar() {
		this.atualizadoEm = Instant.now();
	}

	public Long getId() {
		return this.id;
	}

	public String getNome() {
		return this.nome;
	}

	public Cpf getCpf() {
		return this.cpf;
	}

	public String getEmail() {
		return this.email;
	}

	public String getSenhaHash() {
		return this.senhaHash;
	}

	public String getSenhaTransacionalHash() {
		return this.senhaTransacionalHash;
	}

	public StatusUsuario getStatus() {
		return this.status;
	}

	public Instant getCriadoEm() {
		return this.criadoEm;
	}

}
