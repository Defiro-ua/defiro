-- The drafts the Workflow Managers are designing. A draft is kept whole as JSON: the browser
-- changes it and saves it whole, and nothing outside the Designer reads it. Each save is a new
-- revision.
create table designer.draft (
    id       uuid    primary key,
    revision integer not null,
    content  jsonb   not null
);
