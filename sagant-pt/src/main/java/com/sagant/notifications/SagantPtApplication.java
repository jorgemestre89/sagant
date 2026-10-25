package com.sagant.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@SpringBootApplication
public class SagantPtApplication {

	public static void main(String[] args) {
		SpringApplication.run(SagantPtApplication.class, args);
	}

}
