package com.cifra.identidade.api;

import jakarta.validation.constraints.NotBlank;

public record RenovacaoRequest(

		@NotBlank(message = "Informe o refresh token.")
		String refreshToken) {

}
