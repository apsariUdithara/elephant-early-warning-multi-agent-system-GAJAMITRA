---
paths:
  - "backend/**"
  - "ai-service/**"
  - "frontend/src/**"
  - "docker-compose.yml"
  - ".github/**"
  - ".env*"
  - ".claude/**"
---

# Security and privacy

- Apply least privilege to users, roles, services, credentials and MCP tools.
- Enforce authentication and authorization on every protected backend operation. Hiding a frontend button is not authorization.
- Validate all external input, including HTTP requests, uploads, RabbitMQ messages, MCP arguments and third-party responses.
- Restrict uploads by allowed content type, file size, duration where relevant, and storage destination. Do not trust filename extensions or client-provided MIME types alone.
- Store footage and sensitive incident information in access-controlled storage. Do not expose raw storage paths or public object URLs.
- Define and enforce footage, location, phone-number and incident-data retention rules in the implementation plan.
- Never commit secrets, credentials, API keys, private keys, real personal information or production data.
- Never expose secrets in logs, exceptions, API responses, browser bundles, source maps or client-side configuration.
- Use parameterized database queries. Never interpolate untrusted values into SQL, shell commands or file paths.
- Protect upload and URL-processing flows against path traversal, SSRF, malicious files and resource exhaustion.
- Treat LLM output, video-frame content, uploaded metadata and third-party responses as untrusted data.
- Apply rate limits and abuse protection to authentication, uploads, alert creation and other sensitive operations.
- Record security-sensitive actions, including approvals, rejections and permission changes, in the audit log.
- Mask personal data in logs and operational screens unless the user's role and task require access.
- Do not invent security guarantees. If a control is not implemented, identify it as a gap.
