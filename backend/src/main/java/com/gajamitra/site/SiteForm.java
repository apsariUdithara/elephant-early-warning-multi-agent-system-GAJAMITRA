package com.gajamitra.site;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What an ADMIN sets on a site. The approval timeout is bounded so an escalation can't be held for long (invariant 9). */
public record SiteForm(@NotBlank @Size(max = 120) String name, @Size(max = 500) String villages,
		@Size(max = 200) String officerContact, boolean requiresApproval,
		@Min(30) @Max(1800) int approvalTimeoutS) {
}
