package com.cifra.configuracao;

import java.math.BigDecimal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Dinheiro trafega como texto no JSON, nunca como numero.
 *
 * <p>Numero em JSON vira {@code double} na maioria dos clientes -- em
 * JavaScript, sempre. Um saldo de 0,10 serializado como numero volta de
 * {@code JSON.parse} como 0.1 em ponto flutuante binario, e a partir dai toda
 * conta feita no front carrega erro. Mandar string obriga o cliente a escolher
 * conscientemente como representar o valor.
 *
 * <p>Registrado globalmente e nao campo a campo de proposito: um DTO novo com
 * um valor monetario nao pode depender de alguem lembrar da anotacao.
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfig {

	@Bean
	JacksonModule dinheiroComoTexto() {
		SimpleModule modulo = new SimpleModule("cifra-dinheiro-como-texto");
		modulo.addSerializer(BigDecimal.class, ToStringSerializer.instance);
		return modulo;
	}

}
