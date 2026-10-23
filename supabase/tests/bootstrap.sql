\set ON_ERROR_STOP on
-- TEST ONLY. This file must never run in a Supabase project or production database.
-- Use a fresh disposable PostgreSQL 16 database named exactly auron_security_test.
begin;
do $$
begin
  if current_database() <> 'auron_security_test' then
    raise exception 'Refusing bootstrap: use a disposable database named auron_security_test.';
  end if;
  if exists (select 1 from pg_namespace where nspname = 'auth')
     or to_regclass('auth.users') is not null
     or to_regnamespace('auron_private') is not null then
    raise exception 'Refusing bootstrap: Auth/AURON objects already exist. Use a fresh disposable database.';
  end if;
end;
$$;

do $$
declare role_name text;
begin
  foreach role_name in array array['anon', 'authenticated', 'supabase_auth_admin'] loop
    if not exists (select 1 from pg_roles where rolname = role_name) then
      execute format('create role %I nologin nosuperuser nobypassrls', role_name);
    end if;
    if exists (select 1 from pg_roles where rolname = role_name and (rolsuper or rolbypassrls)) then
      raise exception 'Refusing privileged test role: %', role_name;
    end if;
  end loop;
end;
$$;

-- Minimal substitutes for the Auth schema/identity supplied by hosted Supabase.
-- They exercise database permissions, not signup, JWT validation, SMTP or Auth API.
create schema auth;
create table auth.users (
  id uuid primary key,
  email text not null,
  email_confirmed_at timestamptz
);
create function auth.uid()
returns uuid language sql stable set search_path = '' as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;
revoke all on schema auth from public;
revoke all on table auth.users from public, anon, authenticated, supabase_auth_admin;
grant usage on schema auth, public to anon, authenticated, supabase_auth_admin;
revoke all on function auth.uid() from public;
grant execute on function auth.uid() to anon, authenticated, supabase_auth_admin;

create schema auron_test_checks;
comment on schema auron_test_checks is 'AURON disposable PostgreSQL test fixture; never production.';
grant usage on schema auron_test_checks to anon, authenticated, supabase_auth_admin;
create function auron_test_checks.assert_true(condition boolean, description text)
returns void language plpgsql set search_path = '' as $$
begin
  if condition is distinct from true then
    raise exception 'ASSERTION FAILED: %', description;
  end if;
end;
$$;
create function auron_test_checks.expect_error(statement text, expected_sqlstate text)
returns void language plpgsql set search_path = '' as $$
declare actual_sqlstate text;
begin
  begin
    execute statement;
  exception when others then
    get stacked diagnostics actual_sqlstate = returned_sqlstate;
    if actual_sqlstate = expected_sqlstate then return; end if;
    raise exception 'Expected SQLSTATE %, got % for statement: %',
      expected_sqlstate, actual_sqlstate, statement;
  end;
  raise exception 'Expected SQLSTATE %, statement unexpectedly succeeded: %', expected_sqlstate, statement;
end;
$$;
create function auron_test_checks.payload()
returns jsonb language sql immutable set search_path = '' as $$
  select jsonb_build_object(
    'format', 'auron-export', 'version', 2, 'exportedAt', '2026-10-22T12:00:00Z',
    'tasks', jsonb_build_array(jsonb_build_object('id','test-task','title','Tarefa de ensaio')),
    'routineItems', '[]'::jsonb, 'routineCompletions', '[]'::jsonb,
    'messages', '[]'::jsonb, 'settings', jsonb_build_object('userName','Perfil de ensaio'),
    'memories', '[]'::jsonb, 'readings', '[]'::jsonb
  );
$$;
grant execute on all functions in schema auron_test_checks to anon, authenticated, supabase_auth_admin;

-- Fixture data is not real account information. Unconfirmed owner-email fixture
-- is deliberate so confirmation is tested independently of email equality.
insert into auth.users(id, email, email_confirmed_at) values
 ('10000000-0000-4000-8000-000000000001','owner@example.invalid',now()),
 ('10000000-0000-4000-8000-000000000002','other@example.invalid',now()),
 ('10000000-0000-4000-8000-000000000003','owner@example.invalid',null);
commit;
\echo 'Bootstrap ready. Apply 202610220001_owner_cloud.sql twice, then checks.sql.'
