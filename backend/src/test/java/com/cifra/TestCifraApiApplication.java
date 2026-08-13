package com.cifra;

import org.springframework.boot.SpringApplication;

public class TestCifraApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CifraApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
