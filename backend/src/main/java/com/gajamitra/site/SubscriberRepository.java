package com.gajamitra.site;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriberRepository extends JpaRepository<Subscriber, UUID> {

	List<Subscriber> findBySiteId(UUID siteId);

}
