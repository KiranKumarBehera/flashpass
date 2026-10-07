package com.kiran.flashpassengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FlashpassEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlashpassEngineApplication.class, args);
	}

}
