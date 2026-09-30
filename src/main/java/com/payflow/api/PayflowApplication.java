package com.payflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class PayflowApplication {
    @Bean
    Clock paymentClock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        SpringApplication.run(PayflowApplication.class, args);
    }

}
