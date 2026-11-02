package com.dasigochim.insight.service;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 인사이트 생성용 스레드 풀.
 *
 * <p>한 건에 Gemini 호출이 2~3회 나가 수 초씩 걸린다. 큐를 짧게 두어 밀리면
 * 호출한 스레드가 대신 실행하게 한다(CallerRuns) — 조용히 버려지는 것보다 낫다.
 */
@Configuration
@EnableAsync
public class InsightAsyncConfig {
    @Bean("insightExecutor")
    Executor insightExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("insight-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
