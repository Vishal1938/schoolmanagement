package com.school;

import com.school.common.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. One deployment serves exactly one school; everything school-specific comes from
 * environment variables or the {@code school_config} collection.
 */
@SpringBootApplication
@Modulithic(systemName = "School Management")
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class SchoolManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(SchoolManagementApplication.class, args);
	}
}
