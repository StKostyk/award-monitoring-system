package ua.edu.chnu.awards.award.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for people without an account.
 *
 * @param invitationInterval    how long a repeated invitation to the same person is refused
 * @param dataProtectionContact the address of the data protection officer named in the invitation
 */
@ConfigurationProperties(prefix = "app.people")
public record PeopleProperties(@DefaultValue("PT1H") Duration invitationInterval,
                               @DefaultValue("gdpr@chnu.edu.ua") String dataProtectionContact) {
}
