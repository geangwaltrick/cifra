package com.cifra.identidade.dominio;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Guarda o CPF como 11 digitos e devolve como value object.
 *
 * <p>Efeito colateral util: um CPF invalido gravado direto no banco explode na
 * leitura, em vez de circular silenciosamente pelo dominio.
 */
@Converter(autoApply = true)
public class ConversorDeCpf implements AttributeConverter<Cpf, String> {

	@Override
	public String convertToDatabaseColumn(Cpf cpf) {
		return (cpf == null) ? null : cpf.valor();
	}

	@Override
	public Cpf convertToEntityAttribute(String coluna) {
		return (coluna == null) ? null : new Cpf(coluna);
	}

}
