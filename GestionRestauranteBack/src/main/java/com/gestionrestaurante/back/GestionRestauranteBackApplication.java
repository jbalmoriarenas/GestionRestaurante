package com.gestionrestaurante.back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: primera tarea programada del proyecto (ValoracionSincronizacionScheduler,
// back/fase4-1-back.md).
@SpringBootApplication
@EnableScheduling
public class GestionRestauranteBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(GestionRestauranteBackApplication.class, args);
	}

}
