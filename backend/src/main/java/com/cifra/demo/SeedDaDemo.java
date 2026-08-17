package com.cifra.demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.cifra.identidade.aplicacao.CadastroDeUsuario;
import com.cifra.identidade.dominio.Usuario;
import com.cifra.identidade.repositorio.ContaRepository;
import com.cifra.identidade.repositorio.UsuarioRepository;
import com.cifra.razao.aplicacao.Razao;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conta de demonstracao com historico plausivel.
 *
 * <p>Existe por um motivo comercial, nao tecnico: quem abre o link tem poucos
 * segundos de paciencia. Uma tela de banco vazia nao mostra nada -- extrato,
 * saldo e movimentacao precisam ja estar la no primeiro carregamento.
 *
 * <p>So roda no perfil {@code demo}. Em desenvolvimento e nos testes nao existe.
 */
@Component
@Profile("demo")
public class SeedDaDemo implements ApplicationRunner {

	public static final String EMAIL = "demo@cifra.app";

	public static final String SENHA = "demonstracao1";

	private static final String CPF = "52998224725";

	private static final Log logger = LogFactory.getLog(SeedDaDemo.class);

	private final CadastroDeUsuario cadastro;

	private final UsuarioRepository usuarios;

	private final ContaRepository contas;

	private final Razao razao;

	private final JdbcTemplate jdbc;

	public SeedDaDemo(CadastroDeUsuario cadastro, UsuarioRepository usuarios, ContaRepository contas, Razao razao,
			JdbcTemplate jdbc) {
		this.cadastro = cadastro;
		this.usuarios = usuarios;
		this.contas = contas;
		this.razao = razao;
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		if (this.usuarios.findByEmail(EMAIL).isEmpty()) {
			semear();
		}
	}

	/**
	 * Recomeca a demo todo dia de madrugada.
	 *
	 * <p>Sem isto, o proximo visitante encontra o estrago do anterior: saldo
	 * zerado, chaves com nomes impublicaveis, extrato cheio de teste.
	 */
	@Scheduled(cron = "${cifra.demo.cron:0 0 4 * * *}", zone = "America/Sao_Paulo")
	public void reiniciar() {
		limpar();
		semear();
		logger.info("Conta de demonstracao reiniciada.");
	}

	/**
	 * Apaga os dados da demo.
	 *
	 * <p>Unica operacao do sistema que remove lancamentos, e nao e uma operacao
	 * de negocio: e manutencao de um ambiente descartavel. O razao continua
	 * append-only para tudo que e conta de verdade.
	 */
	@Transactional
	public void limpar() {
		Optional<Usuario> demo = this.usuarios.findByEmail(EMAIL);
		if (demo.isEmpty()) {
			return;
		}

		Long usuarioId = demo.get().getId();
		Long contaId = this.contas.findByUsuarioId(usuarioId).orElseThrow().getId();

		// Transacoes da demo tocam a conta de liquidacao. Apagar so os
		// lancamentos da conta deixaria o outro lado orfao e o razao pararia de
		// somar zero -- entao remove-se a transacao inteira.
		List<Long> transacoes = this.jdbc.queryForList(
				"select distinct transacao_id from lancamentos where conta_id = ?", Long.class, contaId);

		if (!transacoes.isEmpty()) {
			String marcadores = String.join(",", transacoes.stream().map((id) -> "?").toList());
			Object[] ids = transacoes.toArray();

			this.jdbc.update("delete from lancamentos where transacao_id in (" + marcadores + ")", ids);
			this.jdbc.update("update transacoes set estorno_de_id = null where id in (" + marcadores + ")", ids);
			this.jdbc.update("delete from transacoes where id in (" + marcadores + ")", ids);
		}

		this.jdbc.update("update saldos set saldo = 0 where conta_id = ?", contaId);
		this.jdbc.update("delete from chaves_pix where conta_id = ?", contaId);
		this.jdbc.update("delete from limites where conta_id = ?", contaId);
		this.jdbc.update("delete from contas where id = ?", contaId);
		this.jdbc.update("delete from usuarios where id = ?", usuarioId);
	}

	private void semear() {
		var resultado = this.cadastro.cadastrar("Ana Ribeiro (demonstracao)", CPF, EMAIL, SENHA);

		// A demo entra direto: ninguem vai conferir e-mail para testar seu portfolio.
		Usuario usuario = this.usuarios.findById(resultado.usuario().getId()).orElseThrow();
		usuario.confirmarEmail();
		this.usuarios.save(usuario);

		Long conta = resultado.conta().getId();
		this.razao.depositar(conta, new BigDecimal("8400.00"), UUID.randomUUID().toString(), "Salario");

		gerarHistorico(conta);
		espalharNoTempo(conta);
		logger.info("Conta de demonstracao criada: " + EMAIL);
	}

	/**
	 * Recua as datas para os ultimos ~60 dias.
	 *
	 * <p>O seed grava tudo no mesmo instante, e um extrato com 40 lancamentos
	 * no mesmo dia denuncia que foi gerado. As datas sao ajustadas depois, no
	 * banco: o dominio nao aceita data de lancamento vinda de fora, e nem
	 * deveria -- data de fato economico e o momento em que ele aconteceu, nao
	 * um parametro. Aqui e manutencao de um ambiente descartavel.
	 *
	 * <p>A mais antiga fica no fundo (o salario), e o espacamento de 36h
	 * distribui as 40 movimentacoes em cerca de dois meses.
	 */
	private void espalharNoTempo(Long conta) {
		this.jdbc.update("""
				with numeradas as (
				  select t.id, row_number() over (order by t.id desc) as posicao
				    from transacoes t
				    join lancamentos l on l.transacao_id = t.id
				   where l.conta_id = ?
				   group by t.id
				)
				update transacoes t
				   set criado_em    = now() - (n.posicao * interval '36 hours'),
				       liquidado_em = now() - (n.posicao * interval '36 hours')
				  from numeradas n
				 where t.id = n.id
				""", conta);

		// Os dois lados da transacao andam juntos: mover so o lancamento do
		// cliente deixaria o da liquidacao com data diferente do mesmo fato.
		this.jdbc.update("""
				update lancamentos l
				   set criado_em = t.criado_em
				  from transacoes t
				 where t.id = l.transacao_id
				   and t.id in (select transacao_id from lancamentos where conta_id = ?)
				""", conta);
	}

	/** Movimentacao variada, para o extrato parecer o de alguem de verdade. */
	private void gerarHistorico(Long conta) {
		String[] saidas = { "Mercado", "Farmacia", "Aluguel", "Conta de luz", "Restaurante", "Assinatura de streaming",
				"Transporte", "Livraria" };
		String[] entradas = { "Reembolso", "Freelance", "Devolucao de compra" };

		var sorteio = ThreadLocalRandom.current();

		for (int i = 0; i < 40; i++) {
			boolean saida = sorteio.nextInt(10) < 7;
			BigDecimal valor = new BigDecimal(sorteio.nextInt(saida ? 1500 : 4000, saida ? 25000 : 90000))
				.movePointLeft(2);

			try {
				if (saida) {
					this.razao.sacar(conta, valor, UUID.randomUUID().toString(),
							saidas[sorteio.nextInt(saidas.length)]);
				} else {
					this.razao.depositar(conta, valor, UUID.randomUUID().toString(),
							entradas[sorteio.nextInt(entradas.length)]);
				}
			}
			catch (RuntimeException ex) {
				// Saldo insuficiente num sorteio infeliz: pula e segue. O
				// historico nao precisa ser exato, precisa ser plausivel.
				logger.debug("Movimento do seed ignorado: " + ex.getMessage());
			}
		}
	}

}
