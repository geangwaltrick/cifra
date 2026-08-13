package com.cifra.comum;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tokens opacos")
class TokensOpacosTest {

	@Test
	@DisplayName("gera valores distintos a cada chamada")
	void gera_distintos() {
		Set<String> gerados = new HashSet<>();
		for (int i = 0; i < 1_000; i++) {
			gerados.add(TokensOpacos.gerar());
		}

		assertThat(gerados).hasSize(1_000);
	}

	@Test
	@DisplayName("gera token seguro para URL, sem padding")
	void gera_seguro_para_url() {
		String token = TokensOpacos.gerar();

		assertThat(token).matches("[A-Za-z0-9_-]+").doesNotContain("=", "+", "/");
	}

	@Test
	@DisplayName("hash e deterministico e de 64 hexadigitos")
	void hash_deterministico() {
		String token = TokensOpacos.gerar();

		assertThat(TokensOpacos.hashDe(token))
			.isEqualTo(TokensOpacos.hashDe(token))
			.matches("[0-9a-f]{64}");
	}

	@Test
	@DisplayName("hash nao permite recuperar o token")
	void hash_nao_revela_token() {
		String token = TokensOpacos.gerar();

		assertThat(TokensOpacos.hashDe(token)).isNotEqualTo(token).doesNotContain(token);
	}

}
