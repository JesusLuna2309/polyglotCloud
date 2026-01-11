package com.jesusLuna.polyglotCloud;

import org.springframework.boot.SpringApplication;

public class TestPolyglotCloudApplication {

	public static void main(String[] args) {
		SpringApplication.from(PolyglotCloudApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
