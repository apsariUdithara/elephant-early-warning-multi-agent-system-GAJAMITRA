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
    video_path     text not null,
    boundary       double precision[] not null check (cardinality(boundary) = 6),
    status         text not null default 'QUEUED'
                   check (status in ('QUEUED', 'RUNNING', 'AWAITING_APPROVAL', 'COMPLETED', 'FAILED')),
    risk           text check (risk in ('low', 'medium', 'high', 'critical')),  -- set by MCP record_assessment
    summary        jsonb,
    result         jsonb,
    failure_reason text,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);
create index analysis_status_created_idx on analysis (status, created_at);  -- republish stale QUEUED analyses
create index analysis_site_created_idx on analysis (site_id, created_at desc);

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
    created_at      timestamptz not null default now()
);
create index alert_analysis_idx on alert (analysis_id);

create table alert_delivery (
    id            uuid primary key default gen_random_uuid(),
    alert_id      uuid not null references alert (id),
    subscriber_id uuid not null references subscriber (id),
    status        text not null default 'PENDING' check (status in ('PENDING', 'SENT', 'FAILED', 'SKIPPED_SHADOW')),
    provider_id   text,
    updated_at    timestamptz not null default now(),
    unique (alert_id, subscriber_id)  -- one SMS per alert per villager, even after retries
);

create table officer_report (
    id              uuid primary key default gen_random_uuid(),
    analysis_id     uuid not null references analysis (id),
    idempotency_key text not null unique,
    report          text not null,
    created_at      timestamptz not null default now()
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
    check ((status = 'PENDING') = (decided_at is null))
);
create unique index approval_one_pending_per_analysis on approval_request (analysis_id) where status = 'PENDING';
create index approval_pending_expiry_idx on approval_request (expires_at) where status = 'PENDING';

create table audit_log (
    id          uuid primary key default gen_random_uuid(),
    analysis_id uuid references analysis (id),
    actor       text not null check (actor in ('AGENT', 'OFFICER', 'SYSTEM')),
    action      text not null,
    payload     jsonb not null default '{}'::jsonb,
    at          timestamptz not null default now()
);
create index audit_log_analysis_idx on audit_log (analysis_id, at);

-- RabbitMQ delivers at least once: the events consumer records each event_id and ignores repeats.
create table processed_event (
    event_id     uuid primary key,
    processed_at timestamptz not null default now()
);
