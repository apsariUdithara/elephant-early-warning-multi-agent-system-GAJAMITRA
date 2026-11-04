package com.gajamitra.site;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, UUID> {
}
