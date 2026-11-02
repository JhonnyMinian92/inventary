package com.ecuapp.inventary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableScheduling
@EnableAsync
// Stable page JSON: {"content":[...],"page":{"size","number","totalElements","totalPages"}}.
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class InventaryApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventaryApplication.class, args);
	}

}
