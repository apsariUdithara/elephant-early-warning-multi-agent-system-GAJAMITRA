package com.gajamitra;

/** A client error whose message is written for the client. Only these messages are returned (see ApiErrors). */
public class BadRequest extends RuntimeException {

	public BadRequest(String message) {
		super(message);
	}

}
