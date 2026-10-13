package ua.edu.chnu.awards.award.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the approval workflow settings.
 */
@Configuration
@EnableConfigurationProperties(WorkflowProperties.class)
public class WorkflowConfig {
}
