-- GajaMitra schema v1: the data model from docs/IMPLEMENTATION_PLAN.md Phase 3 (docs/architecture/05-data-model.svg).
-- Rules the safety invariants depend on live here, not only in code: idempotency keys are UNIQUE (invariant 4),
-- risk and severity values are restricted, and only one approval can be pending per analysis.
-- Never edit this file after it is merged; add V2__... instead.

create table site (
    id                 uuid primary key default gen_random_uuid(),
    name               text not null,
    villages           text not null default '',
    officer_contact    text not null default '',
    mode               text not null default 'SHADOW' check (mode in ('SHADOW', 'LIVE')),
    requires_approval  boolean not null default true,
    approval_timeout_s integer not null default 120 check (approval_timeout_s > 0),
    created_at         timestamptz not null default now()
);

create table subscriber (
    id         uuid primary key default gen_random_uuid(),
    site_id    uuid not null references site (id),
    phone      text not null,
    language   text not null check (language in ('EN', 'SI', 'TA')),
    created_at timestamptz not null default now(),
    unique (site_id, phone)
);

create table app_user (
    id            uuid primary key default gen_random_uuid(),
    username      text not null unique,
    password_hash text not null,
    role          text not null check (role in ('ADMIN', 'OFFICER'))
);

create table analysis (
    id             uuid primary key default gen_random_uuid(),  -- also the Langfuse trace id and LangGraph thread id
    site_id        uuid not null references site (id),
    video_path     text,  -- cleared by the 30-day footage purge; the row stays for the 1-year alert history
    video_deleted_at timestamptz,
    boundary       double precision[] not null check (cardinality(boundary) = 6),
    status         text not null default 'QUEUED'
                   check (status in ('QUEUED', 'RUNNING', 'AWAITING_APPROVAL', 'COMPLETED', 'FAILED')),
    risk           text check (risk in ('low', 'medium', 'high', 'critical')),  -- set by MCP record_assessment
    summary        jsonb,
    result         jsonb,
    failure_reason text,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),  -- set by the code on every update (no trigger)
    check ((video_path is null) = (video_deleted_at is not null))
);
create index analysis_status_created_idx on analysis (status, created_at);  -- republish stale QUEUED analyses
create index analysis_site_created_idx on analysis (site_id, created_at desc);
create index analysis_footage_purge_idx on analysis (created_at) where video_path is not null;

create table alert (
    id              uuid primary key default gen_random_uuid(),
    analysis_id     uuid not null references analysis (id),
    idempotency_key text not null unique,  -- {analysis_id}:{now|escalation}:{i}
    stage           text not null check (stage in ('NOW', 'ESCALATION')),
    severity        text not null check (severity in ('advisory', 'warning', 'danger')),
    text_en         text not null,
    text_si         text not null,
    text_ta         text not null,
    shadow          boolean not null,
    created_at      timestamptz not null default now(),
    check (idempotency_key like analysis_id::text || ':%')  -- a key can't dedupe across analyses (invariant 4)
);
create index alert_analysis_idx on alert (analysis_id);
create index alert_created_idx on alert (created_at);

create table alert_delivery (
    id            uuid primary key default gen_random_uuid(),
    alert_id      uuid not null references alert (id),
    -- Deleting a villager keeps the delivery record for the audit trail but drops the link to their phone.
    subscriber_id uuid references subscriber (id) on delete set null,
    status        text not null default 'PENDING' check (status in ('PENDING', 'SENT', 'FAILED', 'SKIPPED_SHADOW')),
    provider_id   text,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    unique (alert_id, subscriber_id)  -- one SMS per alert per villager, even after retries
);
create index delivery_pending_idx on alert_delivery (updated_at) where status = 'PENDING';  -- incomplete dispatch

create table officer_report (
    id              uuid primary key default gen_random_uuid(),
    analysis_id     uuid not null references analysis (id),
    idempotency_key text not null unique,
    report          text not null,
    created_at      timestamptz not null default now(),
    check (idempotency_key like analysis_id::text || ':%')
);

create table approval_request (
    id             uuid primary key default gen_random_uuid(),
    analysis_id    uuid not null references analysis (id),
    proposed_alert jsonb not null,
    status         text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED', 'TIMED_OUT')),
    expires_at     timestamptz not null,
    decided_by     uuid references app_user (id),  -- null for TIMED_OUT
    note           text,
    decided_at     timestamptz,
    created_at     timestamptz not null default now(),
    check ((status = 'PENDING') = (decided_at is null)),
    check ((status in ('APPROVED', 'REJECTED')) = (decided_by is not null))  -- every officer decision names the officer
);
create unique index approval_one_pending_per_analysis on approval_request (analysis_id) where status = 'PENDING';
create index approval_pending_expiry_idx on approval_request (expires_at) where status = 'PENDING';
create index approval_created_idx on approval_request (created_at);

create table audit_log (
    id          uuid primary key default gen_random_uuid(),
    analysis_id uuid references analysis (id),
    actor       text not null check (actor in ('AGENT', 'OFFICER', 'SYSTEM')),
    action      text not null,
    payload     jsonb not null default '{}'::jsonb,
    at          timestamptz not null default now()
);
create index audit_log_analysis_idx on audit_log (analysis_id, at);
create index audit_log_at_idx on audit_log (at);

-- RabbitMQ delivers at least once: the events consumer records each event_id and ignores repeats.
create table processed_event (
    event_id     uuid primary key,
    processed_at timestamptz not null default now()
);
create index processed_event_at_idx on processed_event (processed_at);  -- purged after 1 year with the rest
