package com.xinyuzhilian.aiadolescentmentalhealthsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 签到分析线程池配置
 *
 * 拒绝策略保持默认 AbortPolicy（这是行为约束而非调优参数）：
 * 队列满时抛 RejectedExecutionException，任务在数据库仍为 PENDING，
 * 由定时扫尾任务重新投递，保证「用户立刻拿到响应、任务不丢」。
 */
@Configuration
public class CheckinAnalysisConfig {

    @Bean("checkinAnalysisExecutor")
    public ThreadPoolTaskExecutor checkinAnalysisExecutor(
            @Value("${checkin.analysis.executor.core-pool-size:2}") int corePoolSize,
            @Value("${checkin.analysis.executor.max-pool-size:4}") int maxPoolSize,
            @Value("${checkin.analysis.executor.queue-capacity:200}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("checkin-analysis-");
        executor.initialize();
        return executor;
    }
}
