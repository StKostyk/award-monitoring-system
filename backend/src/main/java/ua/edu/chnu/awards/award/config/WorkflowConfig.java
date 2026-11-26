package ua.edu.chnu.awards.award.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the approval workflow settings and those of people without an account.
 */
@Configuration
@EnableConfigurationProperties({WorkflowProperties.class, PeopleProperties.class, AwardImportProperties.class})
public class WorkflowConfig {
}
