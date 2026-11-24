-- The people of the organization, what they may do and the groups they belong to. The identity
-- provider only says who someone is: everything else about them is here. Nobody is ever deleted,
-- only made inactive.
create table identity.app_user (
    id            uuid         primary key default gen_random_uuid(),
    name          varchar(150) not null,
    email         varchar(255),
    department    varchar(150),
    job_title     varchar(150),
    manager_id    uuid         references identity.app_user (id),
    active        boolean      not null default true,
    created_at    timestamptz  not null default now(),
    last_login_at timestamptz
);

-- An email links a person registered in advance to their account, so it names one person only.
create unique index app_user_email on identity.app_user (lower(email));

-- The accounts people sign in with, by what the identity provider never changes: its issuer and the
-- account's subject there.
create table identity.external_identity (
    id        uuid        primary key default gen_random_uuid(),
    user_id   uuid        not null references identity.app_user (id),
    issuer    text        not null,
    subject   text        not null,
    linked_at timestamptz not null default now(),
    unique (issuer, subject)
);

create index external_identity_user on identity.external_identity (user_id);

create table identity.user_role (
    user_id uuid        not null references identity.app_user (id),
    role    varchar(30) not null check (role in ('WORKFLOW_MANAGER', 'ADMINISTRATOR')),
    primary key (user_id, role)
);

-- The designer keeps a step's group by its name, so a name is one group only.
create table identity.app_group (
    id   uuid         primary key default gen_random_uuid(),
    name varchar(150) not null unique
);

create table identity.group_member (
    group_id uuid not null references identity.app_group (id),
    user_id  uuid not null references identity.app_user (id),
    primary key (group_id, user_id)
);

create index group_member_user on identity.group_member (user_id);

-- Who changed what about people, roles and groups. Triggers write it, so that a change made in SQL
-- is recorded like any other.
create table identity.audit_log (
    id           bigserial   primary key,
    actor_id     uuid        references identity.app_user (id),
    procedure_id uuid, -- its foreign key comes with engine.procedure (UC27)
    action       varchar(50) not null,
    details      jsonb,
    occurred_at  timestamptz not null default now()
);

-- The actor is the app_user in the transaction's defiro.actor setting; null when nobody set it (a
-- change made in SQL).
create function identity.audit(action text, details jsonb) returns void
    language sql as $$
    insert into identity.audit_log (actor_id, action, details)
    values (nullif(current_setting('defiro.actor', true), '')::uuid, action, details);
$$;

create function identity.audit_people() returns trigger
    language plpgsql as $$
begin
    case tg_table_name
        when 'app_user' then
            if tg_op = 'INSERT' then
                perform identity.audit('USER_CREATED', jsonb_build_object('user_id', new.id));
            elsif old.active and not new.active then
                perform identity.audit('USER_DEACTIVATED', jsonb_build_object('user_id', new.id));
            end if;
        when 'external_identity' then
            perform identity.audit('USER_LINKED', jsonb_build_object('user_id', new.user_id, 'issuer', new.issuer));
        when 'user_role' then
            if tg_op in ('DELETE', 'UPDATE') then
                perform identity.audit('ROLE_REVOKED', jsonb_build_object('user_id', old.user_id, 'role', old.role));
            end if;
            if tg_op in ('INSERT', 'UPDATE') then
                perform identity.audit('ROLE_GRANTED', jsonb_build_object('user_id', new.user_id, 'role', new.role));
            end if;
        when 'group_member' then
            if tg_op in ('DELETE', 'UPDATE') then
                perform identity.audit('GROUP_MEMBER_REMOVED',
                    jsonb_build_object('user_id', old.user_id, 'group_id', old.group_id));
            end if;
            if tg_op in ('INSERT', 'UPDATE') then
                perform identity.audit('GROUP_MEMBER_ADDED',
                    jsonb_build_object('user_id', new.user_id, 'group_id', new.group_id));
            end if;
    end case;
    return null;
end;
$$;

create trigger audit after insert or update of active on identity.app_user
    for each row execute function identity.audit_people();

create trigger audit after insert on identity.external_identity
    for each row execute function identity.audit_people();

create trigger audit after insert or update or delete on identity.user_role
    for each row execute function identity.audit_people();

create trigger audit after insert or update or delete on identity.group_member
    for each row execute function identity.audit_people();

create function identity.audit_log_append_only() returns trigger
    language plpgsql as $$
begin
    raise exception 'audit_log is append-only';
end;
$$;

create trigger append_only before update or delete on identity.audit_log
    for each row execute function identity.audit_log_append_only();
