package com.cifra.razao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.cifra.DadosDeTeste;
import com.cifra.TestcontainersConfiguration;
import com.cifra.identidade.aplicacao.GeradorDeNumeroDeConta;
import com.cifra.identidade.dominio.Conta;
import com.cifra.identidade.dominio.Cpf;
import com.cifra.identidade.dominio.TipoConta;
import com.cifra.identidade.dominio.Usuario;
import com.cifra.identidade.repositorio.ContaRepository;
import com.cifra.identidade.repositorio.UsuarioRepository;
import com.cifra.razao.aplicacao.ContaDeLiquidacao;
import com.cifra.razao.aplicacao.Razao;
import com.cifra.razao.aplicacao.Reconciliacao;
import com.cifra.razao.dominio.Saldo;
import com.cifra.razao.dominio.StatusTransacao;
import com.cifra.razao.dominio.Transacao;
import com.cifra.razao.repositorio.LancamentoRepository;
import com.cifra.razao.repositorio.SaldoRepository;
import com.cifra.razao.repositorio.TransacaoRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O invariante do sistema, sob carga.
 *
 * <p>Testes de caminho feliz nao provam nada aqui: qualquer implementacao
 * ingenua passa neles. O que estes cenarios cobram e o comportamento sob
 * concorrencia real -- que e onde a implementacao ingenua fura o saldo.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Razao")
class RazaoIT {

	private static final int CONCORRENTES = 50;

	@Autowired
	private Razao razao;

	@Autowired
	private Reconciliacao reconciliacao;

	@Autowired
	private ContaDeLiquidacao liquidacao;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ContaRepository contas;

	@Autowired
	private SaldoRepository saldos;

	@Autowired
	private LancamentoRepository lancamentos;

	@Autowired
	private TransacaoRepository transacoes;

	@Autowired
	private GeradorDeNumeroDeConta gerador;

	// --- invariante ---------------------------------------------------------

	@Test
	@DisplayName("deposito credita o cliente e debita a liquidacao, somando zero")
	void deposito_soma_zero() {
		Long conta = novaConta();

		Transacao transacao = this.razao.depositar(conta, reais("250.00"), chave(), "deposito inicial");

		assertThat(transacao.getStatus()).isEqualTo(StatusTransacao.LIQUIDADA);
		assertThat(transacao.getLancamentos()).hasSize(2);
		assertThat(transacao.somaDosLancamentos()).isEqualByComparingTo("0.00");
		assertThat(razaoDe(conta)).isEqualByComparingTo("250.00");
		assertThat(razaoDe(this.liquidacao.id())).isNegative();
	}

	@Test
	@DisplayName("transferencia move dinheiro sem criar nem destruir")
	void transferencia_conserva_o_total() {
		Long origem = novaConta();
		Long destino = novaConta();
		this.razao.depositar(origem, reais("300.00"), chave(), "carga");

		BigDecimal antes = razaoDe(origem).add(razaoDe(destino));
		this.razao.transferir(origem, destino, reais("120.00"), chave(), "transferencia");

		assertThat(razaoDe(origem)).isEqualByComparingTo("180.00");
		assertThat(razaoDe(destino)).isEqualByComparingTo("120.00");
		assertThat(razaoDe(origem).add(razaoDe(destino))).isEqualByComparingTo(antes);
	}

	@Test
	@DisplayName("o razao inteiro soma exatamente zero")
	void razao_inteiro_soma_zero() {
		Long conta = novaConta();
		this.razao.depositar(conta, reais("77.77"), chave(), "movimento");
		this.razao.sacar(conta, reais("13.13"), chave(), "movimento");

		assertThat(this.lancamentos.somarTudo()).isEqualByComparingTo("0.00");
		assertThat(this.lancamentos.desbalanceadas()).isEmpty();
	}

	@Test
	@DisplayName("projecao de saldo e soma do razao nunca divergem")
	void projecao_bate_com_o_razao() {
		Long conta = novaConta();
		this.razao.depositar(conta, reais("500.00"), chave(), "carga");
		this.razao.sacar(conta, reais("175.50"), chave(), "saque");

		assertThat(saldoDe(conta)).isEqualByComparingTo(razaoDe(conta)).isEqualByComparingTo("324.50");
		assertThat(this.lancamentos.divergencias()).isEmpty();
	}

	// --- concorrencia -------------------------------------------------------

	@Test
	@DisplayName("saques simultaneos nao furam o saldo")
	void saques_concorrentes_nao_furam_o_saldo() {
		Long conta = novaConta();
		this.razao.depositar(conta, reais("100.00"), chave(), "carga");

		// 50 saques de R$ 10 sobre R$ 100: no maximo 10 podem passar.
		AtomicInteger aceitos = new AtomicInteger();
		AtomicInteger recusados = new AtomicInteger();

		emParalelo(CONCORRENTES, () -> {
			try {
				this.razao.sacar(conta, reais("10.00"), null, "saque concorrente");
				aceitos.incrementAndGet();
			}
			catch (RuntimeException ex) {
				recusados.incrementAndGet();
			}
			return null;
		});

		assertThat(aceitos.get()).as("saques aceitos").isEqualTo(10);
		assertThat(recusados.get()).as("saques recusados").isEqualTo(CONCORRENTES - 10);
		assertThat(saldoDe(conta)).isEqualByComparingTo("0.00");
		assertThat(razaoDe(conta)).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("transferencias cruzadas simultaneas nao geram deadlock")
	void transferencias_cruzadas_nao_travam() {
		Long a = novaConta();
		Long b = novaConta();
		this.razao.depositar(a, reais("1000.00"), chave(), "carga");
		this.razao.depositar(b, reais("1000.00"), chave(), "carga");

		BigDecimal total = razaoDe(a).add(razaoDe(b));
		List<Throwable> falhas = new ArrayList<>();

		// Metade vai de A para B, metade de B para A, ao mesmo tempo. Sem a
		// ordenacao dos locks, este e o cenario que produz deadlock.
		List<Throwable> erros = emParalelo(CONCORRENTES, new AtomicInteger()::getAndIncrement, (indice) -> {
			boolean deAparaB = (indice % 2 == 0);
			this.razao.transferir(deAparaB ? a : b, deAparaB ? b : a, reais("1.00"), null, "cruzada");
		});
		falhas.addAll(erros);

		assertThat(falhas).as("nenhuma transferencia deve falhar por deadlock").isEmpty();
		assertThat(razaoDe(a).add(razaoDe(b))).isEqualByComparingTo(total);
	}

	@Test
	@DisplayName("mesma chave de idempotencia sob concorrencia executa uma unica vez")
	void idempotencia_sob_concorrencia() {
		Long conta = novaConta();
		String chave = chave();

		List<Long> idsRetornados = new ArrayList<>();
		emParalelo(CONCORRENTES, () -> {
			Transacao transacao = this.razao.depositar(conta, reais("25.00"), chave, "deposito repetido");
			synchronized (idsRetornados) {
				idsRetornados.add(transacao.getId());
			}
			return null;
		});

		// Todas as 50 chamadas devolveram a mesma transacao...
		assertThat(idsRetornados).hasSize(CONCORRENTES).containsOnly(idsRetornados.get(0));
		// ...e o dinheiro entrou uma vez so.
		assertThat(saldoDe(conta)).isEqualByComparingTo("25.00");
		assertThat(this.lancamentos.findByContaIdOrderByCriadoEmDesc(conta)).hasSize(1);
	}

	@Test
	@DisplayName("reenvio sequencial com a mesma chave devolve a transacao original")
	void idempotencia_sequencial() {
		Long conta = novaConta();
		String chave = chave();

		Transacao primeira = this.razao.depositar(conta, reais("40.00"), chave, "primeira tentativa");
		Transacao repetida = this.razao.depositar(conta, reais("40.00"), chave, "cliente reenviou");

		assertThat(repetida.getId()).isEqualTo(primeira.getId());
		assertThat(saldoDe(conta)).isEqualByComparingTo("40.00");
	}

	// --- validacoes ---------------------------------------------------------

	@Test
	@DisplayName("saque maior que o saldo e recusado")
	void saque_sem_saldo() {
		Long conta = novaConta();
		this.razao.depositar(conta, reais("50.00"), chave(), "carga");

		assertThatThrownBy(() -> this.razao.sacar(conta, reais("50.01"), chave(), "saque"))
			.hasMessageContaining("Saldo insuficiente");

		assertThat(saldoDe(conta)).isEqualByComparingTo("50.00");
	}

	@Test
	@DisplayName("valor com fracao de centavo e recusado, nao arredondado")
	void valor_com_fracao_de_centavo() {
		Long conta = novaConta();

		assertThatThrownBy(() -> this.razao.depositar(conta, new BigDecimal("10.999"), chave(), "invalido"))
			.hasMessageContaining("duas casas decimais");

		assertThat(saldoDe(conta)).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("transferencia para a propria conta e recusada")
	void transferencia_para_si() {
		Long conta = novaConta();
		this.razao.depositar(conta, reais("10.00"), chave(), "carga");

		assertThatThrownBy(() -> this.razao.transferir(conta, conta, reais("5.00"), chave(), "loop"))
			.hasMessageContaining("mesma conta");
	}

	@Test
	@DisplayName("a conta de liquidacao nao aceita movimentacao direta")
	void liquidacao_nao_e_movimentavel() {
		assertThatThrownBy(() -> this.razao.depositar(this.liquidacao.id(), reais("10.00"), chave(), "burla"))
			.hasMessageContaining("nao aceita movimentacao direta");
	}

	// --- reconciliacao ------------------------------------------------------

	@Test
	@DisplayName("a reconciliacao fecha depois de todo o movimento dos testes")
	void reconciliacao_fecha() {
		Long conta = novaConta();
		this.razao.depositar(conta, reais("999.99"), chave(), "carga");
		this.razao.sacar(conta, reais("111.11"), chave(), "saque");

		Reconciliacao.Resultado resultado = this.reconciliacao.conferir();

		assertThat(resultado.transacoesDesbalanceadas()).isZero();
		assertThat(resultado.saldosDivergentes()).isZero();
		assertThat(resultado.somaDoRazao()).isEqualByComparingTo("0.00");
		assertThat(resultado.fecha()).isTrue();
	}

	// --- auxiliares ---------------------------------------------------------

	private Long novaConta() {
		Usuario usuario = this.usuarios
			.save(new Usuario("Titular de Teste", Cpf.de(DadosDeTeste.cpfValido()), DadosDeTeste.emailUnico(),
					"hash-nao-usado-neste-teste"));

		Conta conta = this.contas.save(new Conta(usuario, GeradorDeNumeroDeConta.AGENCIA_PADRAO,
				this.gerador.proximoNumero(), TipoConta.CORRENTE));

		this.saldos.save(new Saldo(conta.getId(), false));
		return conta.getId();
	}

	private BigDecimal saldoDe(Long conta) {
		return this.saldos.findById(conta).orElseThrow().getSaldo();
	}

	private BigDecimal razaoDe(Long conta) {
		return this.lancamentos.somarPorConta(conta);
	}

	private static BigDecimal reais(String valor) {
		return new BigDecimal(valor);
	}

	private static String chave() {
		return UUID.randomUUID().toString();
	}

	private void emParalelo(int vezes, Callable<Void> tarefa) {
		List<Throwable> erros = emParalelo(vezes, () -> 0, (ignorado) -> tarefa.call());
		if (!erros.isEmpty()) {
			// Erros de negocio sao contados pelo proprio cenario; chegar aqui
			// significa falha de infraestrutura, que nao pode passar batida.
			throw new IllegalStateException("Falha inesperada em execucao paralela.", erros.get(0));
		}
	}

	/**
	 * Dispara {@code vezes} tarefas que largam todas no mesmo instante.
	 *
	 * <p>O {@link CountDownLatch} importa: sem ele as threads iniciam
	 * escalonadas, a disputa quase nao acontece e o teste passaria mesmo numa
	 * implementacao sem trava nenhuma.
	 */
	private List<Throwable> emParalelo(int vezes, java.util.function.Supplier<Integer> indices,
			TarefaIndexada tarefa) {
		ExecutorService executor = Executors.newFixedThreadPool(Math.min(vezes, 16));
		CountDownLatch largada = new CountDownLatch(1);
		List<Future<Throwable>> futuros = new ArrayList<>(vezes);

		try {
			for (int i = 0; i < vezes; i++) {
				int indice = indices.get();
				futuros.add(executor.submit(() -> {
					largada.await();
					try {
						tarefa.executar(indice);
						return null;
					}
					catch (Throwable ex) {
						return ex;
					}
				}));
			}

			largada.countDown();

			List<Throwable> erros = new ArrayList<>();
			for (Future<Throwable> futuro : futuros) {
				Throwable erro = futuro.get(60, TimeUnit.SECONDS);
				if (erro != null) {
					erros.add(erro);
				}
			}
			return erros;
		}
		catch (Exception ex) {
			throw new IllegalStateException("Execucao paralela nao concluiu.", ex);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@FunctionalInterface
	private interface TarefaIndexada {

		void executar(int indice) throws Exception;

	}

}
