package com.cifra.cartao.aplicacao;

import java.security.SecureRandom;
import java.time.LocalDate;

import com.cifra.cartao.dominio.Cartao;
import com.cifra.cartao.repositorio.CartaoRepository;
import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.identidade.dominio.Conta;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emite e guarda o cartao virtual da conta. Um por conta. */
@Service
public class EmissorDeCartao {

	/** Prefixo ficticio: nao pertence a nenhuma bandeira real. */
	private static final String BIN = "500199";

	private static final int ANOS_DE_VALIDADE = 4;

	private static final int TENTATIVAS = 10;

	private final CartaoRepository cartoes;

	private final SecureRandom aleatorio = new SecureRandom();

	public EmissorDeCartao(CartaoRepository cartoes) {
		this.cartoes = cartoes;
	}

	/** Devolve o cartao da conta, emitindo na primeira vez que for pedido. */
	@Transactional
	public Cartao doTitular(Conta conta, String nomeDoTitular) {
		return this.cartoes.findByContaId(conta.getId())
			.orElseGet(() -> emitir(conta, nomeDoTitular));
	}

	private Cartao emitir(Conta conta, String nomeDoTitular) {
		LocalDate validade = LocalDate.now().plusYears(ANOS_DE_VALIDADE).withDayOfMonth(1);

		for (int tentativa = 0; tentativa < TENTATIVAS; tentativa++) {
			String numero = sortearNumero();

			if (!this.cartoes.existsByNumero(numero)) {
				return this.cartoes.save(new Cartao(conta.getId(), numero, sortearCvv(),
						nomeDoTitular.toUpperCase(), validade));
			}
		}

		throw new IllegalStateException("Nao foi possivel sortear um numero de cartao livre.");
	}

	/**
	 * Numero de 16 digitos valido pelo algoritmo de Luhn.
	 *
	 * <p>Luhn e o mesmo digito verificador que as bandeiras usam. Gerar assim
	 * faz o numero passar em qualquer validador de formulario -- a demonstracao
	 * fica honesta em vez de exibir digitos inventados que qualquer campo de
	 * checkout recusaria.
	 */
	private String sortearNumero() {
		StringBuilder base = new StringBuilder(BIN);
		while (base.length() < 15) {
			base.append(this.aleatorio.nextInt(10));
		}
		return base.append(digitoDeLuhn(base.toString())).toString();
	}

	/**
	 * Soma os digitos da direita para a esquerda dobrando os de posicao par;
	 * quando o dobro passa de 9, subtrai 9. O verificador e o que falta para
	 * fechar a proxima dezena.
	 */
	public static int digitoDeLuhn(String base) {
		int soma = 0;
		boolean dobra = true;

		for (int i = base.length() - 1; i >= 0; i--) {
			int digito = Character.getNumericValue(base.charAt(i));

			if (dobra) {
				digito *= 2;
				if (digito > 9) {
					digito -= 9;
				}
			}
			soma += digito;
			dobra = !dobra;
		}

		return (10 - (soma % 10)) % 10;
	}

	/** Confere um numero completo. Usado pelos testes e por quem consumir a API. */
	public static boolean numeroValido(String numero) {
		if (numero == null || !numero.matches("\\d{13,19}")) {
			return false;
		}
		String base = numero.substring(0, numero.length() - 1);
		int informado = Character.getNumericValue(numero.charAt(numero.length() - 1));
		return digitoDeLuhn(base) == informado;
	}

	private String sortearCvv() {
		return "%03d".formatted(this.aleatorio.nextInt(1000));
	}

	@Transactional
	public Cartao bloquear(Conta conta) {
		Cartao cartao = exigirCartao(conta);
		cartao.bloquear();
		return cartao;
	}

	@Transactional
	public Cartao desbloquear(Conta conta) {
		Cartao cartao = exigirCartao(conta);

		if (!cartao.getStatus().podeSerDesbloqueado()) {
			throw ProblemaDeNegocio.requisicaoInvalida("cartao-nao-desbloqueavel",
					"Somente cartao bloqueado pode ser desbloqueado.");
		}
		cartao.desbloquear();
		return cartao;
	}

	private Cartao exigirCartao(Conta conta) {
		return this.cartoes.findByContaId(conta.getId())
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("cartao-nao-encontrado",
					"Nenhum cartao emitido para esta conta."));
	}

}
