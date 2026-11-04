package com.gajamitra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.DockerClientFactory;

/**
 * Container tests skip themselves when Docker is missing, which is right on a laptop but must never happen in CI:
 * a skipped constraint test would let a broken migration through. GitHub Actions sets CI=true.
 */
class CiRunsContainerTestsTests {

	@Test
	@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
	void dockerIsAvailableInCi() {
		assertThat(DockerClientFactory.instance().isDockerAvailable())
				.as("CI must run the Testcontainers tests, not skip them").isTrue();
	}

}
