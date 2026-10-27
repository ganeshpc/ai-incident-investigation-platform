package com.aiops.investigator.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Beans shared by the incident API and the worker.
 *
 * <p>{@code @Configuration} is a class Spring reads while it starts. {@code @Bean} methods return
 * objects that other classes can request. {@code @EnableScheduling} turns on {@code @Scheduled}
 * methods. The worker uses one of those methods to look for waiting attempts.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(InvestigatorProperties.class)
public class InvestigatorConfiguration {

  /**
   * The clock the application uses for stored timestamps.
   *
   * <p>{@link Clock#systemUTC} reads the operating-system clock and labels the moment as UTC. Tests
   * can replace this bean if they need a fixed time.
   */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
