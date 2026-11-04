package com.gajamitra;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.gajamitra.analysis.VideoStorage;

/**
 * Every API error as a ProblemDetail (RFC 9457). Validation problems are 400 with a safe message; nothing else
 * (stack traces, SQL, paths) reaches the client.
 */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BadRequest.class)
	ProblemDetail badRequest(BadRequest e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	/** Thrown by libraries too: their messages may describe internals, so the client gets a generic answer. */
	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalidArgument(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "invalid request");
	}

	/** A UNIQUE or foreign-key rule refused the write, e.g. two admins adding the same phone at once. */
	@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
	ProblemDetail conflict(org.springframework.dao.DataIntegrityViolationException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "this conflicts with existing data");
	}

	@ExceptionHandler(VideoStorage.NotAVideo.class)
	ProblemDetail notAVideo(VideoStorage.NotAVideo e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
	}

	@Override
	protected org.springframework.http.ResponseEntity<Object> handleMaxUploadSizeExceededException(
			MaxUploadSizeExceededException e, org.springframework.http.HttpHeaders headers,
			org.springframework.http.HttpStatusCode status, org.springframework.web.context.request.WebRequest request) {
		return org.springframework.http.ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
			.body(ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "the video is too large"));
	}

}
