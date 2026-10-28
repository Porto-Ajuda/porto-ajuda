
package com.portoajuda.aplicacao_osc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AplicacaoOscApplication {

	public static void main(String[] args) {
		SpringApplication.run(AplicacaoOscApplication.class, args);
	}
}
