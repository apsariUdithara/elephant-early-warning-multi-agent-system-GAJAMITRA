package com.gajamitra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.gajamitra.TestcontainersConfiguration;
import com.gajamitra.analysis.AnalysisService;
import com.gajamitra.approval.ApprovalService;
import com.gajamitra.approval.ApprovalService.Decision;

/**
 * Spring's side of the queues against real PostgreSQL and RabbitMQ (plan Phase 3 exit: upload → message published,
 * events → status, approval → resume message, approval timeout → exactly one timeout resume).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MessagingTests {

	static final Duration WAIT = Duration.ofSeconds(5);

	@Autowired
	AnalysisService analyses;

	@Autowired
	ApprovalService approvals;

	@Autowired
	EventsConsumer consumer;

	@Autowired
	MessagingRecovery recovery;

	@Autowired
	RabbitTemplate rabbit;

	@Autowired
	AmqpAdmin admin;

	@Autowired
	JdbcTemplate jdbc;

	UUID site;

	UUID officer;

	@BeforeEach
	void seed() {
		for (String q : List.of(MessagingConfiguration.REQUESTS, MessagingConfiguration.RESUME)) {
			admin.purgeQueue(q, false);
		}
		site = jdbc.queryForObject("insert into site (name, approval_timeout_s) values ('Galgamuwa', 120) returning id",
				UUID.class);
		officer = jdbc.queryForObject("""
				insert into app_user (username, password_hash, role) values (?, 'h', 'OFFICER') returning id""",
				UUID.class, "officer" + System.nanoTime());
	}

	UUID queued() {
		UUID id = analyses.queue(site, "/data/v.mp4", List.of(0.0, 0.5, 1.0, 0.5, 0.5, 0.9));
		admin.purgeQueue(MessagingConfiguration.REQUESTS, false);
		return id;
	}

	String received(String queue) {
		Message message = rabbit.receive(queue, WAIT.toMillis());
		return message == null ? null : new String(message.getBody(), StandardCharsets.UTF_8);
	}

	/** The message for {@code analysis} in {@code queue}; jobs also act on rows other tests left behind. */
	String receivedFor(String queue, UUID analysis) {
		for (String body = received(queue); body != null; body = received(queue)) {
			if (body.contains("\"analysis_id\":\"" + analysis + "\"")) {
				return body;
			}
		}
		return null;
	}

	void event(UUID analysis, String type, String extra) {
		event(UUID.randomUUID(), analysis, type, extra);
	}

	void event(UUID eventId, UUID analysis, String type, String extra) {
		String body = "{\"schema_version\": 1, \"event_id\": \"" + eventId + "\", \"analysis_id\": \"" + analysis
				+ "\", \"type\": \"" + type + "\"" + extra + "}";
		consumer.receive(new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties()));
	}

	String status(UUID analysis) {
		return jdbc.queryForObject("select status from analysis where id = ?", String.class, analysis);
	}

	@Test
	void queueingAnAnalysisPublishesTheRequestAfterCommit() {
		UUID id = analyses.queue(site, "/data/v.mp4", List.of(0.0, 0.5, 1.0, 0.5, 0.5, 0.9));
		assertThat(receivedFor(MessagingConfiguration.REQUESTS, id)).contains("\"schema_version\":1",
				"\"video_path\":\"/data/v.mp4\"");
		assertThat(status(id)).isEqualTo("QUEUED");
	}

	@Test
	void aRedeliveredEventChangesNothing() { // invariant 5
		UUID id = queued();
		UUID eventId = UUID.randomUUID();
		event(eventId, id, "started", "");
		event(eventId, id, "started", "");
		assertThat(status(id)).isEqualTo("RUNNING");
		assertThat(jdbc.queryForObject("select count(1) from audit_log where analysis_id = ? and action = 'event_started'",
				Integer.class, id)).isOne();
	}

	@Test
	void aLateEventNeverMovesAFinishedAnalysisBack() { // invariant 5: out of order
		UUID id = queued();
		event(id, "completed", ", \"result\": {\"herd_size\": 3}");
		event(id, "started", "");
		assertThat(status(id)).isEqualTo("COMPLETED");
	}

	@Test
	void aFailedAnalysisIsRecordedWithItsReason() {
		UUID id = queued();
		event(id, "failed", ", \"reason\": \"SAM timed out\"");
		assertThat(jdbc.queryForObject("select failure_reason from analysis where id = ?", String.class, id))
			.isEqualTo("SAM timed out");
	}

	@Test
	void anUnreadableEventIsDeadLetteredAtOnceNotDropped() {
		admin.purgeQueue(MessagingConfiguration.DEAD_LETTERS, false);
		consumer.receive(new Message("not json".getBytes(), new MessageProperties()));
		event(UUID.randomUUID(), "exploded", "");
		event(UUID.randomUUID(), "failed", ""); // no reason
		assertThat(received(MessagingConfiguration.DEAD_LETTERS)).isEqualTo("not json");
		assertThat(received(MessagingConfiguration.DEAD_LETTERS)).contains("exploded");
		assertThat(received(MessagingConfiguration.DEAD_LETTERS)).contains("failed");
	}

	@Test
	void anEventForAnUnknownAnalysisIsRejected() {
		assertThatThrownBy(() -> event(UUID.randomUUID(), "started", ""))
			.isInstanceOf(AmqpRejectAndDontRequeueException.class);
	}

	@Test
	void aLateApprovalRequiredOpensNoApproval() {
		UUID id = queued();
		event(id, "completed", ", \"result\": {}");
		event(id, "approval_required", ", \"escalation\": []");
		assertThat(status(id)).isEqualTo("COMPLETED");
		assertThat(jdbc.queryForObject("select count(1) from approval_request where analysis_id = ?", Integer.class, id))
			.isZero();
	}

	@Autowired
	com.gajamitra.approval.ApprovalTimeoutJob timeoutJob;

	@Test
	void theScheduledTimeoutPathRunsInATransactionAndPublishes() { // invariant 9, the path the scheduler takes
		UUID id = awaitingApproval();
		jdbc.update("update approval_request set expires_at = now() - interval '1 second' where analysis_id = ?", id);
		timeoutJob.run();
		assertThat(jdbc.queryForObject("select status from approval_request where analysis_id = ?", String.class, id))
			.isEqualTo("TIMED_OUT");
		assertThat(receivedFor(MessagingConfiguration.RESUME, id)).contains("\"decision\":\"timeout\"");
	}

	UUID awaitingApproval() {
		UUID id = queued();
		event(id, "approval_required", ", \"escalation\": [{\"severity\": \"danger\"}]");
		return id;
	}

	UUID approvalOf(UUID analysis) {
		return jdbc.queryForObject("select id from approval_request where analysis_id = ?", UUID.class, analysis);
	}

	@Test
	void approvalRequiredOpensOneApprovalWithTheSitesTimeout() {
		UUID id = awaitingApproval();
		event(id, "approval_required", ", \"escalation\": []"); // redelivered with a new event id
		assertThat(status(id)).isEqualTo("AWAITING_APPROVAL");
		assertThat(jdbc.queryForObject("""
				select extract(epoch from expires_at - created_at)::int from approval_request where analysis_id = ?""",
				Integer.class, id)).isEqualTo(120);
		assertThat(jdbc.queryForObject("select count(1) from approval_request where analysis_id = ?", Integer.class, id))
			.isOne();
	}

	@Test
	void theOfficersDecisionPublishesOneResume() {
		UUID id = awaitingApproval();
		assertThat(approvals.decide(approvalOf(id), officer, Decision.APPROVE, "go")).isTrue();
		// Only this analysis's messages count: the shared queue may hold other tests' resumes (timeout job, recovery).
		assertThat(receivedFor(MessagingConfiguration.RESUME, id)).contains("\"decision\":\"approve\"");
		assertThat(approvals.decide(approvalOf(id), officer, Decision.REJECT, "changed my mind")).isFalse();
		assertThat(receivedFor(MessagingConfiguration.RESUME, id)).isNull();
	}

	@Test
	void anExpiredApprovalTimesOutOnceAndReleasesTheEscalation() { // invariant 9
		UUID id = awaitingApproval();
		Instant later = Instant.now().plus(Duration.ofMinutes(3));
		assertThat(approvals.timeOutExpired(later)).isGreaterThanOrEqualTo(1);
		assertThat(approvals.timeOutExpired(later)).isZero();
		assertThat(jdbc.queryForObject("select status from approval_request where analysis_id = ?", String.class, id))
			.isEqualTo("TIMED_OUT");
		assertThat(receivedFor(MessagingConfiguration.RESUME, id)).contains("\"decision\":\"timeout\"");
	}

	@Test
	void theOfficerCantDecideAfterTheApprovalExpired() { // the timeout wins a tie
		UUID id = awaitingApproval();
		jdbc.update("update approval_request set expires_at = now() - interval '1 second' where analysis_id = ?", id);
		assertThat(approvals.decide(approvalOf(id), officer, Decision.REJECT, "too late")).isFalse();
	}

	@Test
	void lostMessagesAreRepublished() { // invariants 6 and 9
		UUID stale = queued();
		UUID waiting = awaitingApproval();
		approvals.decide(approvalOf(waiting), officer, Decision.APPROVE, null);
		admin.purgeQueue(MessagingConfiguration.RESUME, false); // as if the publish after commit was lost
		recovery.run(Instant.now().plus(Duration.ofMinutes(15)));
		assertThat(receivedFor(MessagingConfiguration.REQUESTS, stale)).isNotNull();
		assertThat(receivedFor(MessagingConfiguration.RESUME, waiting)).contains("\"decision\":\"approve\"",
				"\"officer\":\"" + officer + "\"");
	}

}
