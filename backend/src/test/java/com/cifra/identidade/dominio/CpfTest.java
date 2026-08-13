package com.cifra.identidade.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CPF")
class CpfTest {

	// Valores de teste publicos e classicos, com verificadores conferidos a mao.
	private static final String VALIDO = "52998224725";

	private static final String OUTRO_VALIDO = "11144477735";

	@Test
	@DisplayName("aceita CPF valido sem formatacao")
	void aceita_valido_cru() {
		assertThat(Cpf.de(VALIDO).valor()).isEqualTo(VALIDO);
	}

	@ParameterizedTest
	@DisplayName("normaliza pontuacao e espacos")
	@ValueSource(strings = { "529.982.247-25", "529 982 247 25", "  52998224725  ", "529-982.247/25" })
	void normaliza_pontuacao(String entrada) {
		assertThat(Cpf.de(entrada).valor()).isEqualTo(VALIDO);
	}

	@Test
	@DisplayName("aceita um segundo CPF valido conhecido")
	void aceita_outro_valido() {
		assertThat(Cpf.ehValido(OUTRO_VALIDO)).isTrue();
	}

	@Test
	@DisplayName("rejeita digito verificador trocado")
	void rejeita_verificador_errado() {
		assertThatThrownBy(() -> Cpf.de("52998224726"))
			.isInstanceOf(CpfInvalidoException.class)
			.hasMessageContaining("Digito verificador");
	}

	@ParameterizedTest
	@DisplayName("rejeita sequencias de digitos iguais, que passam na conta mas nao sao CPF")
	@ValueSource(strings = { "00000000000", "11111111111", "99999999999" })
	void rejeita_digitos_repetidos(String entrada) {
		assertThatThrownBy(() -> Cpf.de(entrada))
			.isInstanceOf(CpfInvalidoException.class)
			.hasMessageContaining("digitos iguais");
	}

	@ParameterizedTest
	@DisplayName("rejeita tamanho diferente de 11 digitos")
	@ValueSource(strings = { "", "123", "5299822472", "529982247251" })
	void rejeita_tamanho_errado(String entrada) {
		assertThatThrownBy(() -> Cpf.de(entrada))
			.isInstanceOf(CpfInvalidoException.class)
			.hasMessageContaining("11 digitos");
	}

	@Test
	@DisplayName("rejeita entrada nula")
	void rejeita_nulo() {
		assertThatThrownBy(() -> Cpf.de(null)).isInstanceOf(CpfInvalidoException.class);
	}

	@Test
	@DisplayName("ehValido nao propaga excecao")
	void eh_valido_nao_lanca() {
		assertThat(Cpf.ehValido("nao e um cpf")).isFalse();
		assertThat(Cpf.ehValido(null)).isFalse();
		assertThat(Cpf.ehValido(VALIDO)).isTrue();
	}

	@Test
	@DisplayName("formata para exibicao")
	void formata() {
		assertThat(Cpf.de(VALIDO).formatado()).isEqualTo("529.982.247-25");
	}

	@Test
	@DisplayName("mascara o CPF e nunca vaza inteiro em toString")
	void mascara() {
		Cpf cpf = Cpf.de(VALIDO);

		assertThat(cpf.mascarado()).isEqualTo("***.982.247-**");
		// Protege contra o vazamento mais comum: entidade caindo inteira no log.
		assertThat(cpf.toString()).doesNotContain(VALIDO);
	}

}
