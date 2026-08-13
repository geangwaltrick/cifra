package com.cifra.comum;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cifra.identidade.dominio.CpfInvalidoException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz excecoes para RFC 7807 (Problem Details).
 *
 * <p>Toda resposta de erro tem um {@code type} estavel. O front nunca precisa
 * inspecionar texto de mensagem para decidir comportamento.
 */
@RestControllerAdvice
public class TratadorDeErros {

	private static final String BASE_DOS_TIPOS = "https://cifra.dev/problemas/";

	@ExceptionHandler(ProblemaDeNegocio.class)
	ProblemDetail problemaDeNegocio(ProblemaDeNegocio ex) {
		return montar(ex.getStatus(), ex.getTipo(), ex.getMessage());
	}

	@ExceptionHandler(CpfInvalidoException.class)
	ProblemDetail cpfInvalido(CpfInvalidoException ex) {
		return montar(HttpStatus.BAD_REQUEST, "cpf-invalido", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail camposInvalidos(MethodArgumentNotValidException ex) {
		ProblemDetail problema = montar(HttpStatus.BAD_REQUEST, "campos-invalidos",
				"Um ou mais campos nao passaram na validacao.");

		Map<String, String> erros = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach((erro) -> erros.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
		problema.setProperty("campos", erros);

		return problema;
	}

	private ProblemDetail montar(HttpStatus status, String tipo, String detalhe) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
		problema.setType(URI.create(BASE_DOS_TIPOS + tipo));
		problema.setTitle(tipo);
		problema.setProperty("momento", Instant.now().toString());
		return problema;
	}

}
