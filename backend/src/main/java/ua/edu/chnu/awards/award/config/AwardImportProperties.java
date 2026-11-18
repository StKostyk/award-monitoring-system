package ua.edu.chnu.awards.award.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Limits of the spreadsheet import of people and past awards.
 *
 * @param maxSize the largest file
 * @param maxRows the most rows below the header
 */
@ConfigurationProperties(prefix = "app.award-import")
public record AwardImportProperties(@DefaultValue("2MB") DataSize maxSize, @DefaultValue("1000") int maxRows) {
}
