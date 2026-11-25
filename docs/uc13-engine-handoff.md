# Procedures, tasks and history: what the review stories build on

**For UC17–UC20, from UC13.** These are the Engine's tables and the plugin contract on `feature/UC13-step-contracts`, as the architecture design gives them (2026-10-19, §5 and §7.1). They are a **proposal until UC13 and UC17 agree on them**: `feature/UC17-approve-document` also creates `engine.procedure` and `engine.task`, and only one of the two can merge.

**The plan.** Once we agree and this branch merges, `ProcedureSeed` writes these tables instead (see [A submitted request](#a-submitted-request)). When UC13's start and submit land, the acceptance tests submit through them and the seed goes away.

`ProcedureTablesIT` shows every rule below working against Postgres.

## People

Every column that names a person holds the `id` (uuid) of `identity.app_user`, which UC01 adds (`feature/UC01-change-entra-id-to-keycloak`). It is never an Entra ID or any other identity-provider account. There is no foreign key to it, because each module owns its own schema. A person's name and job title at the time of an action go in that history entry's `details`.

## The tables

### `engine.procedure`: one request, from the moment it is started

| Column | What it is |
|---|---|
| `id` | uuid |
| `draft`, `version` | The workflow version it started on |
| `specification` | **Its own copy** of that version. Read the steps, reasons and transitions from here, not from `engine.specification_version`. |
| `reference` | Unique. **Empty until the request is submitted**, then for example `AC-2026-0147` |
| `state` | `started` (not submitted yet) · `in-progress` · `returned` · `rejected` · `closed` |
| `current_step` | A step id of the specification |
| `requester` | Who made it |
| `started_at`, `submitted_at` | |

### `engine.participant`: who may open a procedure

`(procedure, person)`. This is the requester plus every candidate of every task the procedure has had. Add a row for each candidate when you create a task. "May this person open this request" means a row exists.

### `engine.request_data`: the answers, one version per submission

`(procedure, version)`, `answers` (jsonb, by field id), `submitted_at`. Version 1 is written when the request is submitted, and one more is added each time it is corrected and sent again (UC20). **Versions are never changed or deleted**: the database refuses it. The current answers are the highest version. To see what changed, compare it with the version before. This replaces `previousAnswers` in `Resubmission`.

### `engine.task` and `engine.task_candidate`: a step waiting for people

| Column | What it is |
|---|---|
| `id`, `procedure`, `step` | |
| `assignee_rule` | The rule the candidates were chosen by, kept only to show it (format below) |
| `due_on` | A date, from the step's deadline in working days |
| `created_at` | |
| `completed_at`, `outcome`, `completed_by` | **All empty while the task is open, all set once someone acts.** The database refuses anything in between. |

`engine.task_candidate (task, person)` holds the specific people who may act, **chosen when the task is created**. Changes to groups after that don't move the task. On a review task the requester is never a candidate (CPA art. 69). The first candidate to act completes the task for everyone.

### `engine.audit_entry`: the history (UC25)

| Column | What it is |
|---|---|
| `id` | Increasing; gives the order |
| `procedure`, `at` | |
| `actor`, `actor_role` | `requester` · `reviewer` (a final validator counts as a reviewer) |
| `action` | `submitted` · `approved` · `returned` · `rejected` · `resubmitted` · `closed` · `failed` |
| `details` | jsonb: the actor's name and job title at the time, reasons, message, … **Never the value of a protected field** (NIF, IBAN). |

**Entries are only ever added**: the database refuses updates and deletes.

## From `feature/UC17-approve-document` to these tables

| Your branch | This branch |
|---|---|
| `procedure.requester_id` (Entra ID, text) | `procedure.requester` (`app_user` id) |
| `procedure.current_step_id` | `current_step` |
| `procedure.status` `in_progress` | `state` `in-progress` (hyphen) |
| `procedure.reference`, required when the procedure is created | empty until the request is submitted |
| `procedure.created_at` | `started_at`, plus `submitted_at` |
| `procedure.closed_at`, `is_overdue` | not there (open point 3) |
| foreign key `(draft, version)` → `specification_version` | `specification`, its own copy; `draft` and `version` are kept |
| `document` of the request step (`data`) | `request_data` (`answers`, `version`) |
| `document` of a review step (notes) | not there yet (open point 1) |
| `task.group_id` / `assigned_user_id` | `task.assignee_rule` plus one `task_candidate` row per person |
| `task.status` | open while `completed_at` is empty |
| `task.decision`, `decided_at` | `outcome`, `completed_at`, plus `completed_by` |
| `task.notes` | the message, in the `details` of the `returned` or `rejected` entry |
| `task.due_at` (timestamp) | `due_on` (date) |
| `audit_log.actor_id` (text) | `audit_entry.actor` (`app_user` id), plus `actor_role` |
| `audit_log.entity_type`, `entity_id` | not there (open point 2) |
| `audit_log.occurred_at` | `at` |
| `file`, `file_content` | not on this branch: keep yours. UC15 replaces `file_content` with the File store. |

## Common operations

**Inbox: the open tasks I may act on**
```sql
select t.* from engine.task t
join engine.task_candidate c on c.task = t.id
where c.person = :me and t.completed_at is null
order by t.created_at;
```

**Act on a task: the first candidate wins**
```sql
update engine.task set completed_at = now(), outcome = :outcome, completed_by = :me
where id = :task and completed_at is null
  and exists (select 1 from engine.task_candidate where task = :task and person = :me);
```
If it changes 0 rows, someone else already acted or this person is not a candidate. These are your `TakenByOtherException` and `NotAllowedException`.

**Current answers**
```sql
select answers from engine.request_data where procedure = :procedure order by version desc limit 1;
```

**After the decision**, in the same transaction:
- **Approve:** the procedure moves to the next step, which gets its own task. After the last step, `state = 'closed'`, with a `closed` entry after the `approved` one.
- **Return:** `state = 'returned'`, and `current_step` goes back to the request step. A task for the requester, with rule `{"type": "requester"}` and the requester as its only candidate. A `returned` entry with the reasons and the message in `details`.
- **Reject:** `state = 'rejected'`, and a `rejected` entry with the reasons and the message.
- **Undo:** the history can't be changed, so an undo is a new entry (open point 2). The task reopens by setting `completed_at`, `outcome` and `completed_by` back to empty.

## A submitted request

This is what UC13's submit will write, and what `ProcedureSeed.submit` can write until then, all in one transaction:

```sql
insert into engine.procedure (id, draft, version, specification, reference, state, current_step, requester, submitted_at)
values (:procedure, :draft, :version, cast(:specification as jsonb), :reference, 'in-progress', :nextStep, :requester, now());

insert into engine.request_data (procedure, version, answers)
values (:procedure, 1, cast(:answers as jsonb));

insert into engine.participant (procedure, person) values (:procedure, :requester);

insert into engine.task (id, procedure, step, assignee_rule, due_on)
values (:task, :procedure, :nextStep, cast(:rule as jsonb), :dueOn);

-- for each candidate of the next step, never the requester
insert into engine.task_candidate (task, person) values (:task, :candidate);
insert into engine.participant (procedure, person) values (:procedure, :candidate) on conflict do nothing;

insert into engine.audit_entry (procedure, actor, actor_role, action, details)
values (:procedure, :requester, 'requester', 'submitted', cast(:details as jsonb)); -- {"name": …, "jobTitle": …}
```

**`assignee_rule`** (proposal), one per `AssigneeRule`:
- `{"type": "group", "groupId": "<identity.app_group id>"}`
- `{"type": "managers-of-requester-unit"}`
- `{"type": "requester"}`

UC13's submit will give back the procedure's id, its reference and the first task's id. Tell me if your tests need anything else.

## The plugin contract (`packages/plugin-sdk`)

`StepPlugin`: `descriptor()`, `enter(context)`, `open(context)`, `check(context, data)`, `complete(context, decision)`. Plugins never read or write the database and never call the Engine. The Engine stores what they return. If a plugin throws, the Engine records a `failed` entry and changes nothing.

As a `review` plugin, a review step would be:
- **`descriptor`:** outcomes `approved`, `returned` and `rejected`.
- **`enter`:** `new Entry.Wait(new AssigneeRule.Group(groupId))`, or `new AssigneeRule.ManagersOfRequesterUnit()`.
- **`open`:** a `StepView` with the request's fields locked, the automatic checks (for example the certificate dates against the trip, UC18) and the field notes.
- **`complete`:** gives the outcome from the `Decision`:
  - `action`: `approve`, `return` or `reject`
  - `reasons`
  - `message`: what was written to the requester
  - `notes`: a list of `FieldNote(field, text, author)`

Whether the review moves behind this contract now or stays Engine code for the MVP is open point 5. Either way, check that `Decision`, `FieldNote` and `AssigneeRule` hold everything UC17–UC19 need.

## Open points to agree

1. **Field notes (UC19).** Where are they stored? It's your table to design, for example `engine.field_note` with procedure, data version, field, text, author and task. The architecture gives them to the Instance Runtime.
2. **History.** Add an `undone` action, and a way to link an entry to the task it is about (your `entity_id`), either as a column or in `details`.
3. **`closed_at` and `is_overdue` on the procedure.** Are they needed? Being overdue can be worked out from the open task's `due_on`.
4. **The reference** is given when the request is submitted, not when the procedure is created, because files can be attached before submitting (UC15).
5. **The review as a plugin** now, or Engine code for the MVP.
6. **Merge order.** This branch first (tables and contract only), then yours rebased onto it.
