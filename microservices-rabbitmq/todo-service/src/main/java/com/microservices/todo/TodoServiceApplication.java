package com.microservices.todo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TodoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoServiceApplication.class, args);
    }
}
