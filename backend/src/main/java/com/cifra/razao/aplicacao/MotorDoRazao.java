package com.cifra.razao.aplicacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.razao.dominio.Lancamento;
import com.cifra.razao.dominio.Limite;
import com.cifra.razao.dominio.Saldo;
import com.cifra.razao.dominio.TipoTransacao;
import com.cifra.razao.dominio.Transacao;
import com.cifra.razao.repositorio.LancamentoRepository;
import com.cifra.razao.repositorio.LimiteRepository;
import com.cifra.razao.repositorio.SaldoRepository;
import com.cifra.razao.repositorio.TransacaoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O unico lugar do sistema que escreve no razao.
 *
 * <p>Toda movimentacao passa por {@link #lancar}: trava as contas em ordem,
 * confere saldo e limite, grava o par de lancamentos e so entao atualiza a
 * projecao. Nao existe caminho alternativo -- e por isso que o invariante se
 * sustenta.
 */
@Service
public class MotorDoRazao {

	/** Horario de Brasilia: o "dia" do limite e o dia do cliente, nao o do servidor. */
	private static final ZoneId FUSO_DO_BANCO = ZoneId.of("America/Sao_Paulo");

	private final TransacaoRepository transacoes;

	private final SaldoRepository saldos;

	private final LimiteRepository limites;

	private final LancamentoRepository lancamentos;

	public MotorDoRazao(TransacaoRepository transacoes, SaldoRepository saldos, LimiteRepository limites,
			LancamentoRepository lancamentos) {
		this.transacoes = transacoes;
		this.saldos = saldos;
		this.limites = limites;
		this.lancamentos = lancamentos;
	}

	@Transactional
	public Transacao lancar(Movimento movimento) {
		Map<Long, Saldo> travados = travarEmOrdem(List.of(movimento.contaDebitada(), movimento.contaCreditada()));

		Saldo origem = travados.get(movimento.contaDebitada());
		Saldo destino = travados.get(movimento.contaCreditada());
		BigDecimal valor = movimento.valor();

		if (!origem.comporta(valor.negate())) {
			throw ProblemaDeNegocio.requisicaoInvalida("saldo-insuficiente",
					"Saldo insuficiente para esta operacao.");
		}
		if (movimento.consomeLimiteDiario()) {
			exigirLimiteDisponivel(movimento.contaDebitada(), valor);
		}

		Transacao transacao = new Transacao(movimento.tipo(), valor, movimento.descricao(),
				movimento.idempotencyKey());
		transacao.lancarPar(movimento.contaDebitada(), movimento.contaCreditada(), valor);
		transacao.liquidar();
		this.transacoes.save(transacao);

		origem.aplicar(valor.negate());
		destino.aplicar(valor);

		return transacao;
	}

	/**
	 * Lanca o espelho de uma transacao ja liquidada.
	 *
	 * <p>Estornar nao apaga nada: escreve lancamentos de sinal contrario, do
	 * jeito que se corrige um livro contabil. O original permanece la, agora
	 * marcado como estornado, e o razao continua somando zero.
	 */
	@Transactional
	public Transacao estornar(Long transacaoId, String idempotencyKey) {
		Transacao original = this.transacoes.findWithLancamentosById(transacaoId)
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("transacao-nao-encontrada",
					"Transacao nao encontrada."));

		// Esta ordem importa para quem consome a API. Depois do primeiro
		// estorno a transacao fica ESTORNADA, entao a checagem de status
		// tambem barraria a segunda tentativa -- so que com uma mensagem
		// generica sobre status. Perguntar primeiro se ja existe estorno da a
		// resposta que o cliente precisa: nao e um estado invalido qualquer, e
		// esta transacao especifica ja foi devolvida.
		if (this.transacoes.existsByEstornoDeId(transacaoId)) {
			throw ProblemaDeNegocio.conflito("estorno-ja-realizado", "Esta transacao ja foi estornada.");
		}
		if (!original.getStatus().podeSerEstornada()) {
			throw ProblemaDeNegocio.requisicaoInvalida("transacao-nao-estornavel",
					"Somente transacoes liquidadas podem ser estornadas.");
		}

		List<Long> contas = original.getLancamentos().stream().map(Lancamento::getContaId).distinct().toList();
		Map<Long, Saldo> travados = travarEmOrdem(contas);

		Transacao estorno = new Transacao(TipoTransacao.ESTORNO, original.getValorTotal(),
				"Estorno da transacao %d".formatted(transacaoId), idempotencyKey);
		estorno.vincularEstornoDe(original);

		for (Lancamento lancamento : original.getLancamentos()) {
			BigDecimal invertido = lancamento.getValor().negate();
			Saldo saldo = travados.get(lancamento.getContaId());

			if (!saldo.comporta(invertido)) {
				// Devolver um deposito ja gasto deixaria a conta negativa. O
				// razao nao permite, e forcar aqui seria inventar dinheiro.
				throw ProblemaDeNegocio.requisicaoInvalida("estorno-sem-saldo",
						"A conta nao tem saldo para devolver este valor.");
			}
			estorno.lancar(lancamento.getContaId(), invertido);
		}

		estorno.liquidar();
		this.transacoes.save(estorno);

		for (Lancamento lancamento : estorno.getLancamentos()) {
			travados.get(lancamento.getContaId()).aplicar(lancamento.getValor());
		}

		original.marcarComoEstornada();
		return estorno;
	}

	/**
	 * Confere o teto diario com a conta ja travada.
	 *
	 * <p>A ordem importa mais do que parece. Se esta soma rodasse antes do lock,
	 * duas transferencias simultaneas de R$ 3.000 sobre um limite de R$ 5.000
	 * leriam ambas "nada gasto hoje", ambas passariam e o dia fecharia em
	 * R$ 6.000. Com o lock ja em maos, a segunda so le depois que a primeira
	 * gravou.
	 */
	private void exigirLimiteDisponivel(Long contaId, BigDecimal valor) {
		Limite limite = this.limites.findById(contaId).orElseGet(() -> Limite.padraoPara(contaId));
		Instant inicioDoDia = LocalDate.now(FUSO_DO_BANCO).atStartOfDay(FUSO_DO_BANCO).toInstant();
		BigDecimal jaGasto = this.lancamentos.somarSaidasDesde(contaId, inicioDoDia);

		if (!limite.comporta(jaGasto, valor)) {
			throw ProblemaDeNegocio.requisicaoInvalida("limite-diario-excedido",
					"Limite diario excedido. Disponivel hoje: R$ %s."
						.formatted(limite.disponivelSobre(jaGasto)));
		}
	}

	/**
	 * Trava as contas em ordem crescente de id.
	 *
	 * <p>A ordem e o que elimina o deadlock: se A para B travasse A primeiro e
	 * B para A travasse B primeiro, as duas transacoes ficariam esperando uma a
	 * outra. Ordenando, quem chega depois espera na mesma primeira linha.
	 */
	private Map<Long, Saldo> travarEmOrdem(List<Long> contas) {
		List<Long> ids = new ArrayList<>(contas).stream().distinct().sorted().toList();

		Map<Long, Saldo> encontrados = new HashMap<>();
		for (Saldo saldo : this.saldos.travarPorContas(ids)) {
			encontrados.put(saldo.getContaId(), saldo);
		}

		for (Long id : ids) {
			if (!encontrados.containsKey(id)) {
				throw ProblemaDeNegocio.naoEncontrado("conta-sem-saldo",
						"Conta %d nao possui registro de saldo.".formatted(id));
			}
		}

		return encontrados;
	}

}
