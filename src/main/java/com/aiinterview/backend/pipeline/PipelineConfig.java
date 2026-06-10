package com.aiinterview.backend.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class PipelineConfig {

    @Value("${app.pipeline.screening-threads:10}")
    private int screeningThreads;

    @Value("${app.pipeline.interview-concurrency:5}")
    private int interviewConcurrency;

    /**
     * Thread pool for parallel resume screening.
     * Each thread handles one candidate's screening call
     * to the Python AI service.
     */
    @Bean(name = "screeningExecutor")
    public Executor screeningExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(screeningThreads);
        executor.setMaxPoolSize(screeningThreads * 2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("screening-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        System.out.println("[Pipeline] Screening thread pool: " + screeningThreads + " threads");
        return executor;
    }

    /**
     * Semaphore bean to limit concurrent interview calls.
     * Prevents overwhelming Twilio with too many
     * simultaneous outbound calls.
     */
    @Bean(name = "interviewSemaphore")
    public Semaphore interviewSemaphore() {
        System.out.println("[Pipeline] Interview concurrency limit: " + interviewConcurrency);
        return new Semaphore(interviewConcurrency, true);
    }
}
