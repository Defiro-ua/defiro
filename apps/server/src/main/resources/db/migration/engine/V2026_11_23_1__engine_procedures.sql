-- A procedure: one request on one version of a workflow, from the moment the requester starts it
-- until it ends. It runs to the end on the version it started with; versions never change, so the
-- version it points to is its own copy of the specification.
-- People are kept as their identity provider knows them (issuer and subject), with their name and
-- job title as they were at that moment, which is what the history and the inbox show.
create table engine.procedure (
    id                  uuid        primary key,
    draft               uuid        not null,
    version             integer     not null,
    -- given when the request is submitted, for example AC-2026-0147
    reference           text        unique,
    state               text        not null default 'started'
        check (state in ('started', 'in-progress', 'returned', 'rejected', 'closed')),
    current_step        text        not null,
    requester_issuer    text        not null,
    requester_subject   text        not null,
    requester_name      text,
    requester_job_title text,
    started_at          timestamptz not null default now(),
    submitted_at        timestamptz,
    foreign key (draft, version) references engine.specification_version (draft, version)
);

-- The answers of a request: version 1 when it is submitted, one more each time it is corrected and
-- sent again (UC20).
create table engine.request_data (
    procedure    uuid        not null references engine.procedure (id),
    version      integer     not null check (version > 0),
    answers      jsonb       not null,
    submitted_at timestamptz not null default now(),
    primary key (procedure, version)
);

-- A step waiting for people. The first of them to act completes it.
create table engine.task (
    id                     uuid        primary key,
    procedure              uuid        not null references engine.procedure (id),
    step                   text        not null,
    -- the id of the group or unit that handles the step, as the specification names it
    handled_by             text        not null,
    -- from the step's deadline in working days (UC06)
    due_on                 date,
    created_at             timestamptz not null default now(),
    completed_at           timestamptz,
    outcome                text,
    completed_by_issuer    text,
    completed_by_subject   text,
    completed_by_name      text,
    completed_by_job_title text,
    -- a task is open, or completed with its outcome and who completed it
    check ((completed_at is null) = (outcome is null)),
    check ((completed_at is null) = (completed_by_issuer is null)),
    check ((completed_at is null) = (completed_by_subject is null))
);

-- The inbox: the open tasks of a group
create index task_open_by_group on engine.task (handled_by) where completed_at is null;

-- The history of a procedure (UC25): who did what, and when.
create table engine.audit_entry (
    id              bigint      generated always as identity primary key,
    procedure       uuid        not null references engine.procedure (id),
    at              timestamptz not null default now(),
    actor_issuer    text        not null,
    actor_subject   text        not null,
    actor_name      text,
    actor_job_title text,
    action          text        not null
        check (action in ('submitted', 'approved', 'returned', 'rejected', 'resubmitted', 'closed')),
    -- what goes with the action, for example the reasons of a return
    details         jsonb
);

create index audit_entry_by_procedure on engine.audit_entry (procedure, at);
