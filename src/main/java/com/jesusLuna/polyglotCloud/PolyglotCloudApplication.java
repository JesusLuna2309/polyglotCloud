package com.jesusLuna.polyglotCloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class PolyglotCloudApplication {

	public static void main(String[] args) {
		SpringApplication.run(PolyglotCloudApplication.class, args);
	}

}
