package ua.edu.chnu.awards.gdpr.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the erasure timing.
 */
@Configuration
@EnableConfigurationProperties(ErasureProperties.class)
public class ErasureConfig {
}
