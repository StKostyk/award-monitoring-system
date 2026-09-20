package ua.edu.chnu.awards.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for repository slice tests running against the PostgreSQL container with Flyway applied.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ContainersConfiguration.class)
@ActiveProfiles("test")
public abstract class AbstractJpaSliceTest {
}
