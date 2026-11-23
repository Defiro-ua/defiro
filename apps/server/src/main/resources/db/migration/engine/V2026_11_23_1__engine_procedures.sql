-- The Engine's data, as the architecture design (2026-10-19, §5) gives it to each component:
-- Instance Runtime owns procedures, their participants and their data versions; Task Management owns
-- tasks; Audit Log owns the history.
-- People are kept by their internal code in defiro (identity.app_user), never by their
-- identity-provider account. There is no foreign key to it: each module owns its own schema.

-- A procedure: one request on one version of a workflow, from the moment the requester starts it
-- until it ends. It keeps its own copy of the specification it started with, so later versions only
-- apply to new procedures.
create table engine.procedure (
    id            uuid        primary key,
    draft         uuid        not null,
    version       integer     not null,
    specification jsonb       not null,
    -- given when the request is submitted, for example AC-2026-0147
    reference     text        unique,
    state         text        not null default 'started'
        check (state in ('started', 'in-progress', 'returned', 'rejected', 'closed')),
    current_step  text        not null,
    requester     uuid        not null,
    started_at    timestamptz not null default now(),
    submitted_at  timestamptz
);

-- «Os meus pedidos»: the procedures someone requested
create index procedure_by_requester on engine.procedure (requester);

-- Who takes part in a procedure: the requester and every candidate of every task it had. Only they
-- can see it.
create table engine.participant (
    procedure uuid not null references engine.procedure (id),
    person    uuid not null,
    primary key (procedure, person)
);

create index participant_by_person on engine.participant (person);

-- The answers of a request: version 1 when it is submitted, one more each time it is corrected and
-- sent again (UC20).
create table engine.request_data (
    procedure    uuid        not null references engine.procedure (id),
    version      integer     not null check (version > 0),
    answers      jsonb       not null,
    submitted_at timestamptz not null default now(),
    primary key (procedure, version)
);

-- A step waiting for people. Who may act was decided when the task was created, so later changes in
-- the organization do not move it. The first of them to act completes it.
create table engine.task (
    id            uuid        primary key,
    procedure     uuid        not null references engine.procedure (id),
    step          text        not null,
    -- the rule the people were chosen by, kept to show it
    assignee_rule jsonb       not null,
    -- from the step's deadline in working days (UC06)
    due_on        date,
    created_at    timestamptz not null default now(),
    completed_at  timestamptz,
    outcome       text,
    completed_by  uuid,
    -- a task is open, or completed with its outcome and who completed it
    check ((completed_at is null) = (outcome is null)),
    check ((completed_at is null) = (completed_by is null))
);

-- The people who may act on a task
create table engine.task_candidate (
    task   uuid not null references engine.task (id),
    person uuid not null,
    primary key (task, person)
);

-- The inbox: the tasks someone may act on
create index task_candidate_by_person on engine.task_candidate (person);

-- The history of a procedure (UC25): who did what, in which role, and when. Its details never hold
-- the values of protected fields such as the NIF or the IBAN.
create table engine.audit_entry (
    id         bigint      generated always as identity primary key,
    procedure  uuid        not null references engine.procedure (id),
    at         timestamptz not null default now(),
    actor      uuid        not null,
    actor_role text        not null check (actor_role in ('requester', 'reviewer')),
    action     text        not null
        check (action in ('submitted', 'approved', 'returned', 'rejected', 'resubmitted', 'closed', 'failed')),
    -- what goes with the action, for example the reasons of a return or what failed
    details    jsonb
);

create index audit_entry_by_procedure on engine.audit_entry (procedure, at);
