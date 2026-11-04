package com.gajamitra.auth;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A dashboard user. Only the BCrypt hash of the password is stored. */
@Entity
@Table(name = "app_user")
public class AppUser {

	public enum Role { ADMIN, OFFICER }

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, updatable = false)
	private String username;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Role role;

	protected AppUser() {
	}

	AppUser(String username, String passwordHash, Role role) {
		this.id = UUID.randomUUID();
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = role;
	}

	public UUID id() {
		return id;
	}

	public String username() {
		return username;
	}

	String passwordHash() {
		return passwordHash;
	}

	public Role role() {
		return role;
	}

}
