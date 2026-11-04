package com.gajamitra.sms;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gajamitra.site.Subscriber;

/** Sends nothing: logs the SMS with the phone number masked. Used unless gajamitra.sms.enabled=true. */
@Component
@ConditionalOnProperty(name = "gajamitra.sms.enabled", havingValue = "false", matchIfMissing = true)
class LoggingSmsGateway implements SmsGateway {

	private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);

	@Override
	public String send(String phone, String text) {
		String id = "log-" + UUID.randomUUID();
		log.info("SMS not sent (logging gateway) to {} ({} chars), id {}", Subscriber.masked(phone), text.length(), id);
		return id;
	}

}
