package com.gajamitra.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** The Twilio gateway against a mock Twilio: no network, no real credentials (made-up values below). */
class TwilioSmsGatewayTests {

	static final SmsProperties PROPERTIES = new SmsProperties(true, "AC-test", "token-test", "+15005550006");

	MockRestServiceServer twilio;

	TwilioSmsGateway gateway;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		twilio = MockRestServiceServer.bindTo(builder).build();
		gateway = new TwilioSmsGateway(PROPERTIES, builder);
	}

	@Test
	void sendsTheMessageAndReturnsTwiliosSid() {
		twilio.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/AC-test/Messages.json"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
			.andExpect(content().formDataContains(java.util.Map.of("To", "+94771234512", "From", "+15005550006")))
			.andRespond(withSuccess("{\"sid\": \"SM123\", \"status\": \"queued\"}", MediaType.APPLICATION_JSON));
		assertThat(gateway.send("+94771234512", "අලි වැටට ළඟයි")).isEqualTo("SM123");
		twilio.verify();
	}

	@Test
	void aRefusedMessageThrowsSoTheDeliveryIsRecordedFailed() {
		twilio.expect(requestTo(org.hamcrest.Matchers.endsWith("/Messages.json"))).andRespond(withServerError());
		assertThatThrownBy(() -> gateway.send("+94771234512", "x")).isInstanceOf(RestClientException.class);
	}

	@Test
	void anAnswerWithoutASidCountsAsFailed() {
		twilio.expect(requestTo(org.hamcrest.Matchers.endsWith("/Messages.json")))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> gateway.send("+94771234512", "x")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void enablingSmsWithoutCredentialsStopsStartup() {
		assertThatThrownBy(() -> new TwilioSmsGateway(new SmsProperties(true, "AC-test", "", "+15005550006")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TWILIO_AUTH_TOKEN");
	}

}
