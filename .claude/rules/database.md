---
paths:
  - "backend/src/main/resources/db/**"
  - "backend/src/main/java/**/*Repository.java"
  - "backend/src/main/java/**/*Entity.java"
---

# Database and migrations

- Use Flyway for every database schema change. Never rely on Hibernate schema auto-update.
- Never edit a migration that has already been committed or applied; add a new migration.
- Use pagination and bounded queries for potentially large result sets.
- Avoid N+1 queries and unbounded collection loading.
- Define transaction boundaries explicitly. Do not hold database transactions open while waiting on slow external services.
- Use database constraints to enforce uniqueness and referential integrity, not application checks alone.
