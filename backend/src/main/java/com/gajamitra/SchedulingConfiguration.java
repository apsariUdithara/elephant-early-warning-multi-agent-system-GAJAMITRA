package com.gajamitra;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduled jobs (delivery retry) and the clock they read, so tests can pass their own time. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
