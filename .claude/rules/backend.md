---
paths:
  - "backend/**"
---

# Backend (Spring Boot)

## Java (backend/): Java 21, Spring Boot 4.1

- **Package by feature** (`site`, `analysis`, `alert`, `approval`, `audit`, `auth`, `messaging`, `mcp`, `sms`), not by layer. Inside a feature: `XController`, `XService`, `XRepository`, the domain classes, and DTOs.
- **Constructor injection only**, with `final` fields and no `@Autowired` on fields. A class with more than about 5 dependencies is doing too much, so split it.
- **DTOs are `record`s.** Never expose JPA entities from controllers or MCP tools. Map them explicitly.
- **Sealed interfaces plus pattern matching** for closed sets of variants (for example `ProposedAction permits VillageAlert, OfficerReport`).
- **Entities protect their own invariants:** no public setters for state, and validation goes in constructors or factory methods. `@Transactional` belongs on service methods, not controllers.
- **Validation:** Bean Validation (`@Valid`, `@NotNull`, `@Size`) on every request DTO and MCP tool input. Errors are returned as `ProblemDetail` (RFC 9457) from one `@RestControllerAdvice`.
- **Database:** Flyway migrations only, never `ddl-auto=update`. **Never edit a migration that has been committed**; add a new one. Every UNIQUE or foreign-key rule the plan mentions goes in the database, not just in code.
- **Configuration:** `@ConfigurationProperties` records (`gajamitra.*`), not `@Value` spread around the code. Secrets come only from environment variables.
- **Logging:** SLF4J with structured JSON and `analysisId` in the MDC.
  - Log phone numbers masked (`+9477*****12`).
  - Never log API keys, alert text with full numbers, or video bytes.

## API contracts

- Treat HTTP APIs, RabbitMQ messages and MCP schemas as versioned contracts.
- Check all consumers before changing a request, response, event or tool schema.
- Prefer additive, backward-compatible changes. Coordinate unavoidable breaking changes across all affected services and update contract tests.
