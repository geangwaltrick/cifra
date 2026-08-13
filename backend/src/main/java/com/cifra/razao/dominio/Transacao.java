package com.cifra.razao.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Um fato economico e os lancamentos que ele produziu.
 *
 * <p>Invariante: a soma dos lancamentos de uma transacao liquidada e sempre
 * zero. E o que garante que dinheiro nunca aparece nem some -- so muda de lugar.
 */
@Entity
@Table(name = "transacoes")
public class Transacao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 16, updatable = false)
	private TipoTransacao tipo;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private StatusTransacao status;

	@Column(name = "valor_total", nullable = false, updatable = false, precision = 18, scale = 2)
	private BigDecimal valorTotal;

	@Column(name = "descricao", length = 180)
	private String descricao;

	@Column(name = "idempotency_key", length = 120, updatable = false)
	private String idempotencyKey;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "estorno_de_id", updatable = false)
	private Transacao estornoDe;

	@OneToMany(mappedBy = "transacao", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Lancamento> lancamentos = new ArrayList<>();

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@Column(name = "liquidado_em")
	private Instant liquidadoEm;

	/** Exigido pelo JPA. */
	protected Transacao() {
	}

	public Transacao(TipoTransacao tipo, BigDecimal valorTotal, String descricao, String idempotencyKey) {
		if (valorTotal.signum() <= 0) {
			throw new IllegalArgumentException("Valor da transacao deve ser positivo.");
		}
		this.tipo = tipo;
		this.valorTotal = valorTotal;
		this.descricao = descricao;
		this.idempotencyKey = idempotencyKey;
		this.status = StatusTransacao.PENDENTE;
	}

	/** Debita a origem e credita o destino, no mesmo ato. */
	public void lancarPar(Long contaDebitada, Long contaCreditada, BigDecimal valor) {
		lancar(contaDebitada, valor.negate());
		lancar(contaCreditada, valor);
	}

	/** Uma linha isolada. Usado pelo estorno, que espelha os lancamentos originais. */
	public void lancar(Long contaId, BigDecimal valor) {
		this.lancamentos.add(new Lancamento(this, contaId, valor));
	}

	public void liquidar() {
		if (!somaZero()) {
			// Rede de seguranca de ultimo instante: nada desbalanceado chega ao banco.
			throw new IllegalStateException(
					"Transacao desbalanceada: os lancamentos somam %s em vez de zero.".formatted(somaDosLancamentos()));
		}
		this.status = StatusTransacao.LIQUIDADA;
		this.liquidadoEm = Instant.now();
	}

	public void marcarComoEstornada() {
		this.status = StatusTransacao.ESTORNADA;
	}

	public void vincularEstornoDe(Transacao original) {
		this.estornoDe = original;
	}

	public BigDecimal somaDosLancamentos() {
		return this.lancamentos.stream().map(Lancamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public boolean somaZero() {
		return somaDosLancamentos().signum() == 0;
	}

	@PrePersist
	void aoCriar() {
		this.criadoEm = Instant.now();
	}

	public Long getId() {
		return this.id;
	}

	public TipoTransacao getTipo() {
		return this.tipo;
	}

	public StatusTransacao getStatus() {
		return this.status;
	}

	public BigDecimal getValorTotal() {
		return this.valorTotal;
	}

	public String getDescricao() {
		return this.descricao;
	}

	public String getIdempotencyKey() {
		return this.idempotencyKey;
	}

	public Transacao getEstornoDe() {
		return this.estornoDe;
	}

	public List<Lancamento> getLancamentos() {
		return Collections.unmodifiableList(this.lancamentos);
	}

	public Instant getCriadoEm() {
		return this.criadoEm;
	}

	public Instant getLiquidadoEm() {
		return this.liquidadoEm;
	}

}
