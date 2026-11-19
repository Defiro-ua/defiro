-- The drafts the Workflow Managers are designing. A draft is kept whole as JSON: it changes shape
-- as the designer learns new questions, and nothing outside the Designer reads it.
create table designer.draft (
    id      uuid  primary key,
    content jsonb not null
);
