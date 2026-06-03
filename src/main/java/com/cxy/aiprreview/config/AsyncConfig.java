package com.cxy.aiprreview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/***
 * 配置一个带界限的线程池
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "aiReviewExecutor")
    public Executor aiReviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：日常常驻的审查线程数
        executor.setCorePoolSize(4);
        
        // 最大线程数：并发突增时，线程池最多能临时扩展到的线程上限
        executor.setMaxPoolSize(8);
        
        // 队列容量：如果所有线程都在忙，最多允许 50 个 PR 请求排队等待
        executor.setQueueCapacity(50);
        
        // 线程前缀名：方便你在控制台看日志时，一眼认出是谁在后台干活
        executor.setThreadNamePrefix("AI-Review-Thread-");
        
        // 拒绝策略：当排队队列满了、且最大线程也满了，由调用者所在线程（即 Tomcat 线程）同步执行，确保任务绝对不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        return executor;
    }
}