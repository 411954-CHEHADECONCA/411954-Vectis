package com.vectis.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VectisApplication {

    public static void main(String[] args) {
        SpringApplication.run(VectisApplication.class, args);
    }
}
