-- The procedures part of the team's database diagram (defiro-database.drawio, «Procedures» and
-- audit_log), adapted to what exists:
--   - A workflow version is (draft, version) of engine.specification_version, not a row of a
--     workflow_version table, and a step is the id of a step inside that specification.
--   - There is no app_user or entra_group table yet: people and groups are kept by their Entra ID
--     identifiers, with no foreign key.
-- UC17 (Pedro) needs these tables first. Their owners review them: UC13 and UC20 (Dinis) for
-- procedure and document, UC27 (Daniel) for task, UC25 (Bernardo) for audit_log. Until UC13
-- exists, procedures are created by the acceptance tests only.

-- One procedure of a workflow. It runs to the end with the version it started with: versions never
-- change (V2026_11_19_5), so that version is the procedure's own copy of the specification.
create table engine.procedure (
    id              uuid        primary key,
    reference       varchar(20) not null unique,
    draft           uuid        not null,
    version         integer     not null,
    requester_id    text        not null,
    current_step_id varchar(50),
    status          varchar(20) not null,
    is_overdue      boolean     not null default false,
    created_at      timestamptz not null default now(),
    closed_at       timestamptz,
    foreign key (draft, version) references engine.specification_version (draft, version)
);

-- The answers of a step's form, as {field, label, value}, including the requester's profile
-- fields filled in from Entra ID. When a request is corrected, the answers it replaces are kept
-- in the audit log.
create table engine.document (
    id           uuid        primary key,
    procedure_id uuid        not null references engine.procedure,
    step_id      varchar(50) not null,
    data         jsonb       not null,
    status       varchar(20) not null,
    updated_at   timestamptz not null default now()
);

-- A step of a procedure waiting for a person: the members of the group, or the assigned user.
-- The first to act decides it.
create table engine.task (
    id               uuid        primary key,
    procedure_id     uuid        not null references engine.procedure,
    step_id          varchar(50) not null,
    group_id         text,
    assigned_user_id text,
    status           varchar(20) not null,
    due_at           timestamptz,
    decision         varchar(10),
    notes            text,
    decided_at       timestamptz
);

-- Who did what, and when. The person's name and job title at that moment, and what the action
-- needs to be shown again, are in details.
create table engine.audit_log (
    id           bigserial   primary key,
    actor_id     text,
    procedure_id uuid        references engine.procedure,
    action       varchar(50) not null,
    entity_type  varchar(30) not null,
    entity_id    varchar(36) not null,
    details      jsonb,
    occurred_at  timestamptz not null default now()
);
