-- Each version of each workflow, as the Designer deployed it. Versions are never changed: a new
-- revision of the draft becomes a new version, and running procedures keep their own copy.
create table engine.specification_version (
    draft         uuid        not null,
    version       integer     not null,
    revision      integer     not null,
    published_by  uuid        not null references identity.app_user (id),
    published_at  timestamptz not null default now(),
    specification jsonb       not null,
    primary key (draft, version),
    unique (draft, revision)
);
