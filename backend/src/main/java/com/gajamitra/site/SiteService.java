package com.gajamitra.site;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.context.SecurityContextHolder;

import com.gajamitra.BadRequest;
import com.gajamitra.audit.AuditEntry.Actor;
import com.gajamitra.audit.AuditLog;

/** Site and villager administration (plan Phase 3, REST API, ADMIN). Every change is audited. */
@Service
public class SiteService {

	/** E.164: "+", country code, up to 15 digits. Sri Lankan mobiles are +947XXXXXXXX. */
	static final Pattern PHONE = Pattern.compile("\\+[1-9]\\d{7,14}");

	private final SiteRepository sites;
	private final SubscriberRepository subscribers;
	private final AuditLog audit;
	private final JdbcTemplate jdbc;

	SiteService(SiteRepository sites, SubscriberRepository subscribers, AuditLog audit, JdbcTemplate jdbc) {
		this.sites = sites;
		this.subscribers = subscribers;
		this.audit = audit;
		this.jdbc = jdbc;
	}

	@Transactional
	public Site create(SiteForm form) {
		Site site = sites.save(Site.create(form));
		audit.record(null, Actor.OFFICER, "site_created", Map.of("site_id", site.id().toString(), "by", by()));
		return site;
	}

	@Transactional
	public Optional<Site> update(UUID id, SiteForm form) {
		return sites.findById(id).map(site -> {
			site.update(form);
			audit.record(null, Actor.OFFICER, "site_updated", Map.of("site_id", id.toString(), "by", by()));
			return site;
		});
	}

	/** SHADOW ↔ LIVE. LIVE means villagers get real SMS, so the switch is audited on its own. */
	@Transactional
	public Optional<Site> switchMode(UUID id, Site.Mode mode) {
		return sites.findById(id).map(site -> {
			site.switchTo(mode);
			audit.record(null, Actor.OFFICER, "site_mode_" + mode.name().toLowerCase(), Map.of("site_id", id.toString(), "by", by()));
			return site;
		});
	}

	public List<Site> all() {
		return sites.findAll();
	}

	@Transactional
	public Subscriber addSubscriber(UUID siteId, String phone, Subscriber.Language language) {
		if (!sites.existsById(siteId)) {
			throw new BadRequest("unknown site");
		}
		String normalized = phone == null ? "" : phone.replaceAll("[\\s-]", "");
		if (!PHONE.matcher(normalized).matches()) {
			throw new BadRequest("phone must be in international format, e.g. +94771234567");
		}
		if (subscribers.existsBySiteIdAndPhone(siteId, normalized)) {
			throw new BadRequest("this phone is already subscribed to the site");
		}
		Subscriber subscriber = subscribers.save(new Subscriber(siteId, normalized, language));
		audit.record(null, Actor.OFFICER, "subscriber_added", Map.of("site_id", siteId.toString(),
				"phone", Subscriber.masked(normalized), "by", by()));
		return subscriber;
	}

	/**
	 * Remove a villager. Their delivery records stay, anonymised (the database sets subscriber_id to null). Refused while
	 * one of their SMS is still PENDING or SENDING, so a retry can't text a number that is being removed (plan, GM-10).
	 */
	@Transactional
	public boolean removeSubscriber(UUID siteId, UUID subscriberId) {
		var subscriber = subscribers.findById(subscriberId).filter(s -> s.siteId().equals(siteId));
		if (subscriber.isEmpty()) {
			return false;
		}
		// Still to be sent: PENDING, SENDING, or FAILED with retries left (DeliveryRetryJob will try it again).
		Integer inFlight = jdbc.queryForObject("""
				select count(1) from alert_delivery where subscriber_id = ?
				and (status in ('PENDING', 'SENDING') or (status = 'FAILED' and attempts < ?))""",
				Integer.class, subscriberId, com.gajamitra.alert.DeliveryRetryJob.MAX_ATTEMPTS);
		if (inFlight != null && inFlight > 0) {
			throw new BadRequest("an SMS to this villager is still being sent; try again in a few minutes");
		}
		subscribers.delete(subscriber.get());
		audit.record(null, Actor.OFFICER, "subscriber_removed", Map.of("site_id", siteId.toString(),
				"phone", Subscriber.masked(subscriber.get().phone()), "by", by()));
		return true;
	}

	// Who made the change (invariant 11): the logged-in user's name.
	private static String by() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		return auth == null ? "unknown" : auth.getName();
	}

}
