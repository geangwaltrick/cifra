package com.cifra.identidade.aplicacao;

import java.security.SecureRandom;

import com.cifra.identidade.repositorio.ContaRepository;

import org.springframework.stereotype.Component;

/** Numero de conta com digito verificador, unico dentro da agencia. */
@Component
public class GeradorDeNumeroDeConta {

	public static final String AGENCIA_PADRAO = "0001";

	private static final int DIGITOS_DA_BASE = 7;

	private static final int TENTATIVAS_MAXIMAS = 12;

	private final SecureRandom aleatorio = new SecureRandom();

	private final ContaRepository contas;

	public GeradorDeNumeroDeConta(ContaRepository contas) {
		this.contas = contas;
	}

	public String proximoNumero() {
		for (int tentativa = 0; tentativa < TENTATIVAS_MAXIMAS; tentativa++) {
			String numero = sortearNumero();

			if (!this.contas.existsByAgenciaAndNumero(AGENCIA_PADRAO, numero)) {
				return numero;
			}
		}

		// A colisao real e a unique constraint (agencia, numero); isto aqui so
		// evita gastar a excecao do banco no caso comum.
		throw new IllegalStateException("Nao foi possivel sortear um numero de conta livre.");
	}

	private String sortearNumero() {
		StringBuilder base = new StringBuilder(DIGITOS_DA_BASE);
		for (int i = 0; i < DIGITOS_DA_BASE; i++) {
			base.append(this.aleatorio.nextInt(10));
		}
		return base.append(verificadorDe(base.toString())).toString();
	}

	private static int verificadorDe(String base) {
		int soma = 0;
		int peso = base.length() + 1;

		for (int i = 0; i < base.length(); i++) {
			soma += Character.getNumericValue(base.charAt(i)) * peso--;
		}

		int resto = soma % 11;
		return (resto < 2) ? 0 : 11 - resto;
	}

}
