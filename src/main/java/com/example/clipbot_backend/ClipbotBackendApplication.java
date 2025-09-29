package com.example.clipbot_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ClipbotBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClipbotBackendApplication.class, args);
	}

}
