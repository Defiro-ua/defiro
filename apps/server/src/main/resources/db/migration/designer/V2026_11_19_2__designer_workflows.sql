-- The workflows created in the Designer. The definition is the draft each was created from.
create table designer.workflow (
    id         uuid    primary key,
    name       text    not null,
    version    integer not null,
    status     text    not null,
    definition jsonb   not null
);
