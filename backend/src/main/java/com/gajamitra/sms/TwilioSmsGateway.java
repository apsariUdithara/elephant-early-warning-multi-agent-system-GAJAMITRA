package com.gajamitra.sms;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Sends SMS through Twilio's Messages API with Spring's RestClient (no SDK). Used when gajamitra.sms.enabled=true.
 * Every call has a timeout, so a slow provider can't hold up the sender; any non-2xx answer or timeout throws, and
 * the delivery is recorded FAILED (and retried by the retry job), never SENT.
 */
@Component
@ConditionalOnProperty(name = "gajamitra.sms.enabled", havingValue = "true")
class TwilioSmsGateway implements SmsGateway {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	private final RestClient twilio;

	private final String from;

	@Autowired
	TwilioSmsGateway(SmsProperties properties) {
		this(properties, RestClient.builder().requestFactory(timeouts()));
	}

	/** Tests pass a builder bound to a mock server. */
	TwilioSmsGateway(SmsProperties properties, RestClient.Builder builder) {
		if (blank(properties.twilioAccountSid()) || blank(properties.twilioAuthToken()) || blank(properties.twilioFrom())) {
			throw new IllegalStateException(
					"gajamitra.sms.enabled=true needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_FROM_NUMBER");
		}
		this.twilio = builder
			.baseUrl("https://api.twilio.com/2010-04-01/Accounts/" + properties.twilioAccountSid())
			.defaultHeaders(h -> h.setBasicAuth(properties.twilioAccountSid(), properties.twilioAuthToken()))
			.build();
		this.from = properties.twilioFrom();
	}

	@Override
	public String send(String phone, String text) {
		var form = new LinkedMultiValueMap<String, String>();
		form.add("To", phone);
		form.add("From", from);
		form.add("Body", text);
		Map<?, ?> answer = twilio.post()
			.uri("/Messages.json")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(Map.class);
		Object sid = answer == null ? null : answer.get("sid");
		if (!(sid instanceof String s) || s.isBlank()) {
			throw new IllegalStateException("Twilio answered without a message sid");
		}
		return s;
	}

	private static SimpleClientHttpRequestFactory timeouts() {
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
