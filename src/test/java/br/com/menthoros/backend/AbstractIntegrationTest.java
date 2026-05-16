package br.com.menthoros.backend;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for all integration tests that require a real PostgreSQL database.
 *
 * Imports TestcontainersConfiguration so @ServiceConnection auto-wires the datasource
 * URL from the running container — no manual localhost configuration needed.
 */
@SpringBootTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}
