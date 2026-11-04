package com.gajamitra.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * gajamitra.sms.*: enabled switches from logging to real SMS through Twilio. Credentials come only from the
 * environment (TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM_NUMBER).
 */
@ConfigurationProperties("gajamitra.sms")
public record SmsProperties(boolean enabled, String twilioAccountSid, String twilioAuthToken, String twilioFrom) {
}
