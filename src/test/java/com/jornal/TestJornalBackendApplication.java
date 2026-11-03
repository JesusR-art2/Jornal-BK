package com.jornal;

import org.springframework.boot.SpringApplication;

public class TestJornalBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(JornalBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
