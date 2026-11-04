package com.gajamitra.site;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A villager who receives SMS alerts for a site, in their own language. */
@Entity
@Immutable
@Table(name = "subscriber")
public class Subscriber {

	public enum Language { EN, SI, TA }

	@Id
	private UUID id;

	@Column(name = "site_id", nullable = false)
	private UUID siteId;

	@Column(nullable = false)
	private String phone;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Language language;

	protected Subscriber() {
	}

	public UUID id() {
		return id;
	}

	public String phone() {
		return phone;
	}

	public Language language() {
		return language;
	}

	/** For logs: never log a full phone number (+9477*****12). */
	public static String masked(String phone) {
		if (phone == null || phone.length() < 8) {
			return "*****";
		}
		return phone.substring(0, 5) + "*".repeat(phone.length() - 7) + phone.substring(phone.length() - 2);
	}

}
