package com.gajamitra.sms;

/** Port to the SMS provider. Twilio comes in its own slice; until then {@link LoggingSmsGateway} is used. */
public interface SmsGateway {

	/**
	 * Hand one SMS to the provider and return its message id. Throws if the provider refused it; the caller then
	 * marks the delivery FAILED, never SENT.
	 */
	String send(String phone, String text);

}
