package com.cxy.aiprreview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class AiPrReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPrReviewApplication.class, args);
    }

}
