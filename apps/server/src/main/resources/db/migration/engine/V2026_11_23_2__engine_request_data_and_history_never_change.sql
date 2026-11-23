-- A submitted version of a request and an entry of the history are records of what happened: they
-- are only ever added. Correcting a request adds a new version; the database refuses any other change.
create function engine.only_added() returns trigger
    language plpgsql as $$
begin
    raise exception '% nunca muda: só se acrescenta.', tg_argv[0];
end;
$$;

create trigger request_data_never_changes
    before update or delete on engine.request_data
    for each row execute function engine.only_added('Uma versão submetida do pedido');

create trigger audit_entry_never_changes
    before update or delete on engine.audit_entry
    for each row execute function engine.only_added('O histórico');
