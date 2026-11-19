-- Each version of each workflow, as published from its draft. Versions are never changed: a new
-- revision of the draft becomes a new version. The Engine starts new procedures on the latest one
-- and keeps its own copy in each procedure.
create table designer.workflow_version (
    workflow          uuid        not null references designer.draft (id),
    version           integer     not null,
    revision          integer     not null,
    published_by      text        not null,
    published_by_name text,
    published_at      timestamptz not null default now(),
    specification     jsonb       not null,
    primary key (workflow, version),
    unique (workflow, revision)
);
