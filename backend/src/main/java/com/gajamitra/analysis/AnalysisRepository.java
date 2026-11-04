package com.gajamitra.analysis;

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

}
