package com.gajamitra.sms;

/**
 * Port to the SMS provider: {@link TwilioSmsGateway} when gajamitra.sms.enabled=true, otherwise
 * {@link LoggingSmsGateway}. Implementations must time out rather than block.
 */
public interface SmsGateway {

	/**
	 * Hand one SMS to the provider and return its message id. Throws if the provider refused it; the caller then
	 * marks the delivery FAILED, never SENT.
	 */
	String send(String phone, String text);

}
