package com.gajamitra.alert;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An alert proposed by the AI service, in all three languages. Untrusted input: the severity is re-checked against
 * the stored tracker risk by {@link AlertPolicy}, and the MCP layer validates these constraints before calling in.
 */
public record VillageAlert(@NotBlank String severity, @NotBlank @Size(max = 320) String en,
		@NotBlank @Size(max = 320) String si, @NotBlank @Size(max = 320) String ta) {
}
