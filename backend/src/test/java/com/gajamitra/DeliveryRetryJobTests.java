package com.gajamitra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.gajamitra.alert.Alert.Stage;
import com.gajamitra.alert.AlertDispatchService;
import com.gajamitra.alert.DeliveryRetryJob;
import com.gajamitra.alert.VillageAlert;
import com.gajamitra.sms.SmsGateway;

/** The retry job finishes what the after-commit sender didn't, without ever texting anyone twice (invariants 4, 10). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DeliveryRetryJobTests {

	static final VillageAlert WARNING = new VillageAlert("warning", "Elephants near the fence.", "සිංහල", "தமிழ்");

	/** Far enough ahead that every delivery written by the test is due. */
	static final Instant LATER = Instant.now().plus(Duration.ofHours(1));

	@Autowired
	AlertDispatchService service;

	@Autowired
	DeliveryRetryJob job;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	SmsGateway sms;

	UUID analysis;

	@BeforeEach
	void seed() {
		jdbc.update("update alert_delivery set status = 'SENT' where status in ('PENDING', 'FAILED')"); // other tests' rows
		reset(sms);
		when(sms.send(anyString(), anyString())).thenReturn("SM1");
		UUID site = jdbc.queryForObject("insert into site (name, mode) values ('Galgamuwa', 'LIVE') returning id",
				UUID.class);
		jdbc.update("insert into subscriber (site_id, phone, language) values (?, ?, 'SI')", site,
				"+9477" + (1_000_000 + System.nanoTime() % 8_000_000));
		analysis = jdbc.queryForObject("""
				insert into analysis (site_id, video_path, boundary) values (?, '/data/v.mp4', '{0,0.5,1,0.5,0.5,0.9}')
				returning id""", UUID.class, site);
		service.recordAssessment(analysis, "high", "{}");
	}

	void sendAndThen(String status) {
		service.sendVillageAlert(analysis, analysis + ":now:0", Stage.NOW, WARNING);
		jdbc.update("""
				update alert_delivery set status = ?
				where alert_id in (select id from alert where analysis_id = ?)""", status, analysis);
		reset(sms);
		when(sms.send(anyString(), anyString())).thenReturn("SM2");
	}

	List<String> statuses() {
		return jdbc.queryForList("""
				select d.status from alert_delivery d join alert a on a.id = d.alert_id where a.analysis_id = ?""",
				String.class, analysis);
	}

	@Test
	void aDeliveryLeftPendingByACrashIsSentOnce() {
		sendAndThen("PENDING");
		assertThat(job.run(LATER)).isOne();
		assertThat(job.run(LATER)).isZero();
		verify(sms, times(1)).send(anyString(), anyString());
		assertThat(statuses()).containsExactly("SENT");
	}

	@Test
	void aDeliveryTheProviderRefusedIsRetried() {
		sendAndThen("FAILED");
		job.run(LATER);
		verify(sms, times(1)).send(anyString(), anyString());
		assertThat(statuses()).containsExactly("SENT");
	}

	@Test
	void aDeliveryThatMayAlreadyHaveBeenSentIsNeverResent() { // invariant 4
		sendAndThen("SENDING");
		assertThat(job.run(LATER)).isZero();
		verify(sms, never()).send(anyString(), anyString());
		assertThat(statuses()).containsExactly("SENDING");
	}

	@Test
	void retriesStopAfterTheAttemptLimit() {
		sendAndThen("FAILED");
		when(sms.send(anyString(), anyString())).thenThrow(new IllegalStateException("provider down"));
		for (int i = 0; i < 5; i++) {
			job.run(LATER);
		}
		verify(sms, times(2)).send(anyString(), anyString()); // 3 attempts in all, the first one was the original send
		assertThat(jdbc.queryForObject("""
				select d.attempts from alert_delivery d join alert a on a.id = d.alert_id where a.analysis_id = ?""",
				Integer.class, analysis)).isEqualTo(3);
	}

	@Test
	void aRecentDeliveryIsLeftToTheFirstSender() {
		sendAndThen("PENDING");
		assertThat(job.run(Instant.now().minus(Duration.ofMinutes(5)))).isZero();
		verify(sms, never()).send(anyString(), anyString());
	}

}
