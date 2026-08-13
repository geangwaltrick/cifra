package com.cifra.identidade.dominio;

/**
 * CPF valido, normalizado para 11 digitos.
 *
 * <p>Value object: se a instancia existe, o numero passou pelos dois digitos
 * verificadores. Nao existe "CPF possivelmente invalido" circulando no dominio.
 */
public record Cpf(String valor) {

	public Cpf {
		valor = somenteDigitos(valor);

		if (valor.length() != 11) {
			throw new CpfInvalidoException("CPF deve ter 11 digitos.");
		}
		if (todosOsDigitosIguais(valor)) {
			// 111.111.111-11 e companhia passam na conta dos verificadores,
			// mas nao sao CPF. Implementacao ingenua aceita, a Receita nao.
			throw new CpfInvalidoException("CPF com todos os digitos iguais nao e valido.");
		}
		if (!verificadoresConferem(valor)) {
			throw new CpfInvalidoException("Digito verificador do CPF nao confere.");
		}
	}

	/** Aceita "529.982.247-25" ou "52998224725". */
	public static Cpf de(String entrada) {
		return new Cpf(entrada);
	}

	public static boolean ehValido(String entrada) {
		try {
			new Cpf(entrada);
			return true;
		}
		catch (CpfInvalidoException | NullPointerException ex) {
			return false;
		}
	}

	/** Formato de exibicao: 529.982.247-25 */
	public String formatado() {
		return "%s.%s.%s-%s".formatted(
				valor.substring(0, 3),
				valor.substring(3, 6),
				valor.substring(6, 9),
				valor.substring(9, 11));
	}

	/** Para logs e telas de suporte: ***.982.247-** */
	public String mascarado() {
		return "***.%s.%s-**".formatted(valor.substring(3, 6), valor.substring(6, 9));
	}

	@Override
	public String toString() {
		return mascarado();
	}

	private static String somenteDigitos(String entrada) {
		if (entrada == null) {
			throw new CpfInvalidoException("CPF nao informado.");
		}
		return entrada.replaceAll("\\D", "");
	}

	private static boolean todosOsDigitosIguais(String digitos) {
		return digitos.chars().distinct().count() == 1;
	}

	private static boolean verificadoresConferem(String digitos) {
		int primeiro = calcularVerificador(digitos, 9);
		int segundo = calcularVerificador(digitos, 10);

		return primeiro == Character.getNumericValue(digitos.charAt(9))
				&& segundo == Character.getNumericValue(digitos.charAt(10));
	}

	/**
	 * Soma os primeiros {@code quantidade} digitos com pesos decrescentes que
	 * comecam em {@code quantidade + 1}. Resto menor que 2 significa digito zero.
	 */
	private static int calcularVerificador(String digitos, int quantidade) {
		int soma = 0;
		int peso = quantidade + 1;

		for (int i = 0; i < quantidade; i++) {
			soma += Character.getNumericValue(digitos.charAt(i)) * peso--;
		}

		int resto = soma % 11;
		return (resto < 2) ? 0 : 11 - resto;
	}

}
