package com.gajamitra.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

	/** Row lock: the alert tools for one analysis run one at a time, so two calls with the same key can't both insert. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Analysis a where a.id = :id")
	Optional<Analysis> lockById(UUID id);

	/** Recent assessed analyses at a site, newest first (episodic memory for the agents). Bounded to 50. */
	List<Analysis> findTop50BySiteIdAndRiskNotNullAndCreatedAtAfterOrderByCreatedAtDesc(UUID siteId, Instant after);

}
