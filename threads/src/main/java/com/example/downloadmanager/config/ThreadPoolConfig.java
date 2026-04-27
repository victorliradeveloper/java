package com.example.downloadmanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ThreadPoolConfig {

    @Value("${download.thread-pool.size:5}")
    private int poolSize;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService downloadExecutor() {
        return Executors.newFixedThreadPool(poolSize);
    }
}
