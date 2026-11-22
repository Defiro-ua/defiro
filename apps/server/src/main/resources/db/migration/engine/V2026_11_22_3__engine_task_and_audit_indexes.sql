-- The queries the review stories run most:
--   - the open tasks, for the inbox, and the open tasks of one procedure (who takes part, undo);
--   - the newest entry of an action about one entity, such as who took or decided a task.
create index task_open_by_procedure on engine.task (procedure_id) where status = 'open';
create index audit_log_by_entity on engine.audit_log (entity_id, action, id);
