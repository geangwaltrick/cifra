package com.cifra.cartao;

import com.cifra.cartao.aplicacao.EmissorDeCartao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Numero de cartao (Luhn)")
class LuhnTest {

	@ParameterizedTest
	@DisplayName("aceita numeros de teste publicos das bandeiras")
	@ValueSource(strings = { "4539578763621486", "5555555555554444", "4111111111111111", "378282246310005" })
	void aceita_numeros_validos(String numero) {
		assertThat(EmissorDeCartao.numeroValido(numero)).isTrue();
	}

	@ParameterizedTest
	@DisplayName("rejeita numero com digito verificador trocado")
	@ValueSource(strings = { "4539578763621487", "5555555555554445", "4111111111111112" })
	void rejeita_verificador_errado(String numero) {
		assertThat(EmissorDeCartao.numeroValido(numero)).isFalse();
	}

	@ParameterizedTest
	@DisplayName("rejeita entrada que nem parece cartao")
	@ValueSource(strings = { "", "abcd", "123", "4539-5787-6362-1486" })
	void rejeita_formato_invalido(String entrada) {
		assertThat(EmissorDeCartao.numeroValido(entrada)).isFalse();
	}

	@Test
	@DisplayName("nao explode com entrada nula")
	void tolera_nulo() {
		assertThat(EmissorDeCartao.numeroValido(null)).isFalse();
	}

	@Test
	@DisplayName("o digito calculado fecha o numero")
	void digito_fecha_o_numero() {
		String base = "453957876362148";

		int digito = EmissorDeCartao.digitoDeLuhn(base);

		assertThat(digito).isEqualTo(6);
		assertThat(EmissorDeCartao.numeroValido(base + digito)).isTrue();
	}

}
