package com.gajamitra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

// Skipped when Docker isn't available locally; CI always has Docker, so it always runs there.
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GajamitraBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
