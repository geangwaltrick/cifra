package com.cifra.configuracao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cifra")
public record PropriedadesDoCifra(String urlBase, Jwt jwt, Email email, Login login) {

	public record Jwt(String segredo, Duration validadeDoAcesso, Duration validadeDoRefresh) {
	}

	public record Email(String remetente, Duration validadeDaVerificacao) {
	}

	public record Login(int tentativasPorJanela, Duration janela) {
	}

}
