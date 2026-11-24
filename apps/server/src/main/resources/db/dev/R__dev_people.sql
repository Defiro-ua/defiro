-- The people of the development set-up (dev profile only). Sofia is registered in advance: her account
-- in deploy/keycloak/defiro-realm.json is linked to her by the email, the first time she signs in. The
-- ids here are defiro's own and are not those of the realm.
insert into identity.app_user (id, name, email, department, job_title)
values ('0c9d2f4e-1b7a-4e55-8f3c-6d2a9b10e701', 'Sofia Almeida', 'sofia.almeida@defiro.test',
        'Modernização Administrativa', 'Gestora de workflows')
on conflict do nothing;

insert into identity.user_role (user_id, role)
select u.id, r.role
from identity.app_user u
cross join (values ('WORKFLOW_MANAGER'), ('ADMINISTRATOR')) as r (role)
where lower(u.email) = 'sofia.almeida@defiro.test'
on conflict do nothing;

insert into identity.app_group (id, name)
values ('7b1e6a30-0000-4000-8000-000000000001', 'Chefias'),
       ('7b1e6a30-0000-4000-8000-000000000002', 'Divisão de Obras Municipais'),
       ('7b1e6a30-0000-4000-8000-000000000003', 'Divisão Financeira'),
       ('7b1e6a30-0000-4000-8000-000000000004', 'Recursos Humanos'),
       ('7b1e6a30-0000-4000-8000-000000000005', 'RH · Formação')
on conflict do nothing;

insert into identity.group_member (group_id, user_id)
select g.id, u.id
from identity.app_group g, identity.app_user u
where g.name = 'Divisão Financeira' and lower(u.email) = 'sofia.almeida@defiro.test'
on conflict do nothing;
