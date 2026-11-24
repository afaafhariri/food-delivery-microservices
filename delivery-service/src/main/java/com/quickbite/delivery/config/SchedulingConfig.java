package com.quickbite.delivery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the periodic sweep of the waiting list ({@code WaitingOrderDispatcher}). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
