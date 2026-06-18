package com.reforma.domain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReformaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReformaApplication.class, args);
    }
}
