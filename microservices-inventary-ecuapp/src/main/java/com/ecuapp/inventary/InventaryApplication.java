package com.ecuapp.inventary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InventaryApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventaryApplication.class, args);
	}

}
