package com.cifra;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.cifra.identidade.dominio.Cpf;

/** Geradores de dados unicos compartilhados pelos testes de integracao. */
public final class DadosDeTeste {

	private static final AtomicInteger SEQUENCIA = new AtomicInteger();

	private DadosDeTeste() {
	}

	public static String emailUnico() {
		return "teste+%d-%d@cifra.local".formatted(SEQUENCIA.incrementAndGet(), System.nanoTime() % 100_000);
	}

	/**
	 * Sorteia ate cair num CPF valido. Usa {@link Cpf#ehValido} de proposito, em
	 * vez de recalcular os digitos verificadores aqui: a matematica e coberta por
	 * CpfTest com valores fixos, e reimplementar o algoritmo no teste faria os
	 * dois errarem juntos. Acerta em ~121 tentativas.
	 */
	public static String cpfValido() {
		for (int tentativa = 0; tentativa < 10_000; tentativa++) {
			StringBuilder candidato = new StringBuilder(11);
			for (int i = 0; i < 11; i++) {
				candidato.append(ThreadLocalRandom.current().nextInt(10));
			}
			if (Cpf.ehValido(candidato.toString())) {
				return candidato.toString();
			}
		}
		throw new IllegalStateException("Nao foi possivel sortear um CPF valido.");
	}

}
