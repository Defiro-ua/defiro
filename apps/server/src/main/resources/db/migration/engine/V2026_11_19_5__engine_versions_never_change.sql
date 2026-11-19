-- A deployed version is the record of what was in force, by whom and when: it is only ever added.
-- To change a workflow, the Designer publishes a new version; the database refuses any other change.
create function engine.versions_never_change() returns trigger
    language plpgsql as $$
begin
    raise exception 'Uma versão publicada nunca muda: publique uma versão nova.';
end;
$$;

create trigger versions_never_change
    before update or delete on engine.specification_version
    for each row execute function engine.versions_never_change();
