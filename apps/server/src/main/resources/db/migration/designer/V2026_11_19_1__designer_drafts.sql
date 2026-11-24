-- The drafts the Workflow Managers are designing. A draft is kept whole as JSON: the browser
-- changes it and saves it whole, and nothing outside the Designer reads it. Each save is a new
-- revision. Publishing deploys a revision to the Engine, which keeps every version; the draft keeps
-- the version its last publication became, and who published it when, with their name at the time.
create table designer.draft (
    id                 uuid        primary key,
    revision           integer     not null,
    content            jsonb       not null,
    published_revision integer,
    published_version  integer,
    published_by       uuid        references identity.app_user (id),
    published_by_name  text,
    published_at       timestamptz
);
