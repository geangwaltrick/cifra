package com.cifra.identidade.api;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

		@NotBlank(message = "Informe seu e-mail.")
		String email,

		@NotBlank(message = "Informe sua senha.")
		String senha) {

}
