package com.cifra.configuracao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SegurancaConfig {

	/**
	 * Custo 12: cerca de 250 ms por hash em hardware atual. Caro de proposito --
	 * e o que torna um vazamento de base inutil para forca bruta.
	 */
	private static final int CUSTO_DO_BCRYPT = 12;

	@Bean
	SecurityFilterChain cadeiaDeFiltros(HttpSecurity http) throws Exception {
		return http
			// API sem sessao e sem cookie: nao ha o que um ataque CSRF sequestrar.
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement((sessao) -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests((rotas) -> rotas
				.requestMatchers("/api/v1/auth/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
				// O dispatch de erro tambem atravessa esta cadeia. Sem liberar
				// /error, qualquer 500 volta para o cliente como um 401 vazio --
				// e o erro de verdade fica invisivel de fora.
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()))
			.build();
	}

	@Bean
	PasswordEncoder codificadorDeSenha() {
		return new BCryptPasswordEncoder(CUSTO_DO_BCRYPT);
	}

}
