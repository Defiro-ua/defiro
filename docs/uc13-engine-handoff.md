# Procedures, tasks and history: what the review stories build on

**For UC17–UC20, from UC13.** These are the Engine's tables and the plugin contract on `feature/UC13-step-contracts` (#65), as the architecture design gives them (2026-10-19, §5 and §7.1), with the proposals below. **They are proposals until UC17 confirms them.** `feature/UC17-approve-document` also creates `engine.procedure` and `engine.task`, and only one of the two can merge: this document says what changes for UC17–UC19 when they move onto these tables.

**The plan.** Once the proposals are confirmed and #65 merges, `ProcedureSeed` writes these tables instead (see [A request, from start to submission](#a-request-from-start-to-submission)). When UC13's start and submit land, the acceptance tests submit through them and the seed goes away.

`ProcedureTablesIT` shows every rule below working against Postgres.

## What is proposed, and what it changes

### 1. The answers are versions that never change

**Proposed.** Each submission of a request is a new row of `engine.request_data`: version 1 when it is submitted, version 2 when it is corrected and sent again (UC20), and so on. A version is never changed or deleted: the database refuses it.

**Why.** UC20 #5 says *"Resubmitting creates a new version of the request"*, and UC17 #2 needs *"what changed in the new version"*. With versions, the current answers are the highest version, and what changed is version N compared with N−1. A submitted request is also a record: no code, and no one with SQL access, can change it afterwards.

**What it changes for UC17–UC19.**
- Read the answers from `request_data` (the highest version), not from `engine.document`.
- What changed on a resubmitted request is the comparison of the last two versions, not `Resubmission.previousAnswers` in the history. `Resubmission` and the `document` table are not needed.
- The reviewers' notes (UC19) no longer have a `document` row to live in: they need their own table (see [Still open](#still-open)).

**For UC20.** Correcting a request adds the next version. Nothing is overwritten.

### 2. Answers by field id, with typed values; the requester as they were

**Proposed.** `request_data.answers` is an object with one entry per field, by the field's id in the specification. The values keep their type: text, numbers, dates and times as ISO 8601, lists. Next to it, `request_data.requester` keeps the requester as they were at that submission. The database refuses anything that is not an object.

```json
"answers":   { "motivo": "Formação", "destino": "Lisboa", "partida": "2026-11-08T07:30",
               "regresso": "2026-11-10T19:40", "nif": "234567418", "iban": "PT50003500001234567890154" }
"requester": { "name": "Ana Ferreira", "jobTitle": "Técnica Superior",
               "unit": "Divisão de Obras Municipais", "manager": "Rui Matos" }
```

**Why.**
- **Labels** come from the procedure's own copy of the specification (`specification.steps[0].fields[].label`), which never changes, so they are not copied into every answer.
- **Typed values** let the checks work on the values themselves (the return after the departure, the certificate dates against the trip), and let expenses and files be lists. Writing a date as dd/mm/aaaa is the screen's job.
- **The plugin contract** already passes answers this way: `check(context, Map<String, Object> data)` and `StepContext.data`.
- **The requester as they were**: UC25 shows the requester's job title at that moment, and someone can change unit later. A new version takes a new copy, so a correction shows the requester as they are when they correct it.

**What it changes for UC17–UC19.**
- `RequestData(requester, List<Answer>)` becomes the `answers` object plus the `requester` object.
- To show an answer, take its label from the specification's field with the same id.
- Dates arrive as `2026-11-08T07:30`: the web writes them as `08/11/2026 07:30`.
- Until the form fields of «Ajudas de custo» are in the specification (UC04, Candeias), the field ids are the prototype's keys: `motivo`, `destino`, `desc`, `partida`, `regresso`, `nif`, `iban`, and so on.
- NIF and IBAN are protected fields (legal requirements §7.2): later in UC13 their values are kept encrypted, so read them through the Engine, never straight from the JSON.

### 3. A task goes to specific people, chosen when it is created

**Proposed** (unchanged from the first draft). When a request reaches a step that waits, the Engine turns the step's rule (a group, the heads of the requester's unit, or the requester) into specific people. They become the task's candidates (`engine.task_candidate`) and the procedure's participants (`engine.participant`). The requester is never a candidate of a review task (CPA art. 69).

**Why.** It is the architecture's decision 5: the inbox is "tasks where I'm a candidate". If someone changes group later, the task stays with the people it was given to. And the rule that nobody decides their own request is applied once, when the task is created, instead of in every query.

**What it changes for UC17–UC19.**
- `Candidates.canHandle` becomes a lookup: is this person a candidate of this task? See [Common operations](#common-operations).
- The inbox is the open tasks where the person is a candidate. `task.group_id` and `assigned_user_id` are not needed. The rule the people were chosen by stays in `task.assignee_rule`, only to show it.
- Who may open a request is a row in `engine.participant`: the requester, and every candidate of every task it had.
- `Candidates.canHandle` does not exclude the requester today. Here, the requester is never in `task_candidate` for a review task.

### 4. People are defiro's internal ids

**Proposed.** Every column that names a person holds the `id` (uuid) of `identity.app_user`, which UC01 adds (`feature/UC01-change-entra-id-to-keycloak`, Daniel). It is never an Entra ID or another identity-provider account. There is no foreign key to it, because each module owns its own schema.

**What it changes for UC17–UC19.** `requester_id`, `actor_id` and the task's people stop being Entra IDs as text: they are uuids. A name and job title come from `app_user`, or, as they were at that moment, from the `requester` of the data version and the `details` of the history entry.

### 5. The reference is given when the request is started

**Proposed.** `procedure.reference` is set when the procedure is created, and can never be empty. It is the workflow's code, the year and a counter per workflow that starts again every year: `AC-2026-0147`. UC13 gives it (the counter comes with UC13's start and submit).

**What it means.** A request that is started and never submitted keeps its number, so the numbering can have gaps: `AC-2026-0146`, then `AC-2026-0148`.

**What it changes for UC17–UC19.** Nothing: `ProcedureSeed` already sets the reference when it creates the procedure.

### 6. The states

**Proposed.** `procedure.state` is one of `started` (not submitted yet) · `in-progress` · `returned` · `rejected` · `closed`, written with a hyphen.

**What it changes for UC17–UC19.** `status` is called `state`, and `in_progress` is written `in-progress`. A procedure in `started` was never submitted: no reviewer sees it.

### 7. The history records undoing a decision, and the task an entry is about

**Proposed.** `audit_entry.action` also takes `undone`, and `audit_entry.task` says which task an entry is about. The database refuses a task that does not exist. Entries are still only ever added.

**What it changes for UC17–UC19.**
- `entity_type` and `entity_id` become `task`: a decision (`approved`, `returned`, `rejected`) and its undoing (`undone`) name the task. `AuditLog.last(action, entity)` becomes a query on `task`.
- Undoing a decision does not change or remove its entry: it adds an `undone` entry for the same task, and reopens the task (see [Common operations](#common-operations)).
- The `submitted` and `resubmitted` entries are about the request, not a task: put the version in `details`, for example `{"version": 2, "name": "Ana Ferreira", "jobTitle": "Técnica Superior"}`.

### 8. When a procedure ended; nothing stored about being overdue

**Proposed.** `procedure.closed_at` is the moment the procedure was rejected or closed, and is empty while it goes on. The database keeps the two together: a `rejected` or `closed` procedure has a `closed_at`, and no other has. There is no `is_overdue` column.

**Why.** `ProcedureSeed` and `Procedures` already set `closed_at` when they close or reject, and clear it when that is undone. It is also the date retention periods will count from. `is_overdue` is only in the UC17 migration, and nothing reads it. A stored flag would need a job to keep it right: an open task is overdue when its `due_on` is before today.

**What it changes for UC17–UC19.** Nothing for `closed_at`. Drop `is_overdue`, and ask the open task's `due_on` instead.

## The tables

### `engine.procedure`: one request, from the moment it is started

| Column | What it is |
|---|---|
| `id` | uuid |
| `draft`, `version` | The workflow version it started on |
| `specification` | **Its own copy** of that version. Read the steps, fields, reasons and transitions from here, not from `engine.specification_version`. |
| `reference` | Unique and never empty, for example `AC-2026-0147` (proposal 5) |
| `state` | `started` · `in-progress` · `returned` · `rejected` · `closed` (proposal 6) |
| `current_step` | A step id of the specification |
| `requester` | `app_user` id |
| `started_at`, `submitted_at` | |
| `closed_at` | Set exactly when `rejected` or `closed` (proposal 8) |

### `engine.participant`: who may open a procedure

`(procedure, person)`: the requester and every candidate of every task the procedure had (proposal 3).

### `engine.request_data`: the answers, one version per submission

`(procedure, version)`, `answers` (an object, by field id), `requester` (an object: name, job title, unit, manager), `submitted_at`. Never changed or deleted (proposals 1 and 2).

### `engine.task` and `engine.task_candidate`: a step waiting for people

| Column | What it is |
|---|---|
| `id`, `procedure`, `step` | |
| `assignee_rule` | The rule the candidates were chosen by, only to show it |
| `due_on` | A date, from the step's deadline in working days |
| `created_at` | |
| `completed_at`, `outcome`, `completed_by` | **All empty while the task is open, all set once someone acts.** The database refuses anything in between. |

`engine.task_candidate (task, person)`: the people who may act, chosen when the task is created (proposal 3). The first of them to act completes the task for everyone.

### `engine.audit_entry`: the history (UC25)

| Column | What it is |
|---|---|
| `id` | Increasing; gives the order |
| `procedure`, `at` | |
| `actor`, `actor_role` | `requester` · `reviewer` (a final validator counts as a reviewer) |
| `action` | `submitted` · `approved` · `returned` · `rejected` · `resubmitted` · `closed` · `failed` · `undone` |
| `task` | The task the entry is about, if any (proposal 7) |
| `details` | jsonb: the actor's name and job title at the time, reasons, message, version, … **Never the value of a protected field** (NIF, IBAN). |

**Entries are only ever added**: the database refuses updates and deletes.

## From `feature/UC17-approve-document` to these tables

| Your branch | These tables | Proposal |
|---|---|---|
| `procedure.requester_id` (Entra ID, text) | `procedure.requester` (`app_user` id) | 4 |
| `procedure.current_step_id` | `current_step` | |
| `procedure.status` `in_progress` | `state` `in-progress` | 6 |
| `procedure.reference`, set when created | the same | 5 |
| `procedure.created_at` | `started_at`, plus `submitted_at` | |
| `procedure.closed_at` | the same | 8 |
| `procedure.is_overdue` | dropped: the open task's `due_on` before today | 8 |
| foreign key `(draft, version)` → `specification_version` | `specification`, its own copy; `draft` and `version` are kept | |
| `document` of the request step, `data` = `RequestData` | `request_data`: `answers` by field id, plus `requester` | 1, 2 |
| `document` of a review step (notes) | its own table, to define (UC19) | 1 |
| `Resubmission.previousAnswers` | the version before, in `request_data` | 1 |
| `task.group_id` / `assigned_user_id` | `task_candidate`, one row per person; the rule in `assignee_rule` | 3 |
| `Candidates.canHandle` | a row in `task_candidate` | 3 |
| `task.status` | open while `completed_at` is empty | |
| `task.decision`, `decided_at` | `outcome`, `completed_at`, plus `completed_by` | |
| `task.notes` | the message, in the `details` of the `returned` or `rejected` entry | |
| `task.due_at` (timestamp) | `due_on` (date) | |
| `audit_log.actor_id` (text) | `audit_entry.actor` (`app_user` id), plus `actor_role` | 4 |
| `audit_log.entity_type`, `entity_id` | `audit_entry.task` | 7 |
| `audit_log.occurred_at` | `at` | |
| `file`, `file_content` | not on this branch: keep yours. UC15 replaces `file_content` with the File store. | |

## Common operations

**Inbox: the open tasks I may act on**
```sql
select t.* from engine.task t
join engine.task_candidate c on c.task = t.id
where c.person = :me and t.completed_at is null
order by t.created_at;
```

**May I open this request?**
```sql
select exists (select 1 from engine.participant where procedure = :procedure and person = :me);
```

**Act on a task: the first candidate wins**
```sql
update engine.task set completed_at = now(), outcome = :outcome, completed_by = :me
where id = :task and completed_at is null
  and exists (select 1 from engine.task_candidate where task = :task and person = :me);
```
If it changes 0 rows, someone else already acted or this person is not a candidate: your `TakenByOtherException` and `NotAllowedException`.

**The current answers, and the ones before**
```sql
select version, answers, requester from engine.request_data
where procedure = :procedure order by version desc limit 2;
```

**After the decision**, in the same transaction:
- **Approve:** an `approved` entry with the task. The procedure moves to the next step, which gets its own task. After the last step: `state = 'closed'`, `closed_at = now()`, and a `closed` entry.
- **Return:** `state = 'returned'`, `current_step` back to the request step, and a `returned` entry with the task, the reasons and the message in `details`. Whether the requester gets a task is still open (see [Still open](#still-open)).
- **Reject:** `state = 'rejected'`, `closed_at = now()`, and a `rejected` entry with the task, the reasons and the message.
- **Undo:**
  ```sql
  update engine.task set completed_at = null, outcome = null, completed_by = null where id = :task;
  update engine.procedure set state = 'in-progress', closed_at = null, current_step = :step where id = :procedure;
  insert into engine.audit_entry (procedure, actor, actor_role, action, task)
  values (:procedure, :me, 'reviewer', 'undone', :task);
  ```

## A request, from start to submission

What UC13 will write, and what `ProcedureSeed.submit` can write until then, in one transaction:

```sql
-- started: the procedure, its reference and its requester
insert into engine.procedure (id, draft, version, specification, reference, current_step, requester)
values (:procedure, :draft, :version, cast(:specification as jsonb), :reference, :requestStep, :requester);
insert into engine.participant (procedure, person) values (:procedure, :requester);

-- submitted: version 1 of the answers, and the procedure moves to the first review step
insert into engine.request_data (procedure, version, answers, requester)
values (:procedure, 1, cast(:answers as jsonb), cast(:requesterAsTheyAre as jsonb));
update engine.procedure set state = 'in-progress', current_step = :nextStep, submitted_at = now()
where id = :procedure;

insert into engine.task (id, procedure, step, assignee_rule, due_on)
values (:task, :procedure, :nextStep, cast(:rule as jsonb), :dueOn);
-- for each candidate of the next step, never the requester
insert into engine.task_candidate (task, person) values (:task, :candidate);
insert into engine.participant (procedure, person) values (:procedure, :candidate) on conflict do nothing;

insert into engine.audit_entry (procedure, actor, actor_role, action, details)
values (:procedure, :requester, 'requester', 'submitted', cast(:details as jsonb));
-- details: {"version": 1, "name": "Ana Ferreira", "jobTitle": "Técnica Superior"}
```

**`assignee_rule`**, one per `AssigneeRule`:
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

## Still open

1. **Field notes (UC19).** Where are they stored, now that there is no `document` row for them? It's UC19's table to design, for example `engine.field_note` with the procedure, the data version, the field id, the text, the author and the task. The architecture gives them to the Instance Runtime.
2. **A return and the requester's task.** Does a return give the requester a task (the request step is entered again, with `AssigneeRule.Requester`, and the returned request is in their inbox as a task), or no task (the UC18 draft: the returned request in Ana's inbox is UC20 and UC27)? It decides how UC20 finds the requests to correct.
3. **The review as a plugin** behind `StepPlugin` now, or Engine code for the MVP. Either way, check that `Decision`, `FieldNote` and `AssigneeRule` hold everything UC17–UC19 need.
4. **The file table (UC15).** `engine.file` and the File store come with UC15, behind the `FileStore` interface of the UC17 branch, replacing `TemporaryFileStore`.
5. **Merge order.** #65 first (tables and contract only), then UC17 rebased onto it.
