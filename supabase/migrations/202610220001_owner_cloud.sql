-- AURON personal edition. Run as project administrator in Supabase SQL Editor.
-- Additive: does not read, migrate or delete Android records or existing Auth users.
begin;

create schema if not exists auron_private;
revoke all on schema auron_private from public, anon, authenticated;

create table if not exists auron_private.owner_allowlist (
  singleton boolean primary key default true check (singleton),
  email text not null unique check (email = lower(trim(email)) and length(email) between 3 and 320)
);
revoke all on auron_private.owner_allowlist from public, anon, authenticated;

-- No owner is guessed or created by this migration. Configure exactly one email
-- using the separate administrative setup example; never expose this table.
create or replace function public.auron_before_user_created(event jsonb)
returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not exists (
    select 1 from auron_private.owner_allowlist o
    where o.email = lower(trim(event -> 'user' ->> 'email'))
  ) then
    return jsonb_build_object('error', jsonb_build_object(
      'http_code', 403, 'message', 'Cadastro restrito ao proprietário autorizado do AURON.'));
  end if;
  return '{}'::jsonb;
end;
$$;
revoke all on function public.auron_before_user_created(jsonb) from public, anon, authenticated;
grant execute on function public.auron_before_user_created(jsonb) to supabase_auth_admin;

-- Checks the current database Auth identity and confirmed email, not an email
-- supplied by the client or merely the unverified JWT email claim.
create or replace function public.is_auron_owner()
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from auth.users u
    join auron_private.owner_allowlist o on o.email = lower(trim(u.email))
    where u.id = (select auth.uid()) and u.email_confirmed_at is not null
  );
$$;
revoke all on function public.is_auron_owner() from public, anon;
grant execute on function public.is_auron_owner() to authenticated;

create sequence if not exists auron_private.snapshot_revisions as bigint;
revoke all on sequence auron_private.snapshot_revisions from public, anon, authenticated;

-- A document snapshot is deliberate: local Room remains the operational store.
-- Payload contains the existing export v2 sections, without session credentials.
create table if not exists public.auron_snapshots (
  owner_id uuid primary key references auth.users(id) on delete restrict,
  revision bigint not null unique check (revision > 0),
  schema_version integer not null check (schema_version = 2),
  payload jsonb not null check (jsonb_typeof(payload) = 'object'),
  updated_at timestamptz not null default now(),
  check (octet_length(payload::text) <= 1048576)
);
alter table public.auron_snapshots enable row level security;
alter table public.auron_snapshots force row level security;
revoke all on public.auron_snapshots from public, anon, authenticated;
grant select on public.auron_snapshots to authenticated;
do $$ begin
  if not exists (select 1 from pg_policies where schemaname = 'public'
      and tablename = 'auron_snapshots' and policyname = 'auron_owner_select') then
    create policy auron_owner_select on public.auron_snapshots for select to authenticated
      using (owner_id = (select auth.uid()) and (select public.is_auron_owner()));
  end if;
end $$;

create or replace function public.auron_snapshot_get()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare result jsonb;
begin
  if not public.is_auron_owner() then raise sqlstate 'PT403' using message = 'Acesso restrito ao proprietário confirmado.'; end if;
  select jsonb_build_object('revision', s.revision, 'updatedAt', s.updated_at, 'payload', s.payload)
    into result from public.auron_snapshots s where s.owner_id = (select auth.uid());
  return result;
end;
$$;

create or replace function public.auron_snapshot_save(p_payload jsonb, p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare owner uuid := (select auth.uid()); stored_revision bigint; section text;
begin
  if not public.is_auron_owner() then raise sqlstate 'PT403' using message = 'Acesso restrito ao proprietário confirmado.'; end if;
  if p_expected_revision is null or p_expected_revision < 0 then raise sqlstate 'PT400' using message = 'Revisão inválida.'; end if;
  if p_payload is null or jsonb_typeof(p_payload) <> 'object' or octet_length(p_payload::text) > 1048576 then
    raise sqlstate 'PT413' using message = 'Cópia inválida ou acima de 1 MiB.';
  end if;
  if p_payload ->> 'format' is distinct from 'auron-export' or p_payload ->> 'version' is distinct from '2'
    or jsonb_typeof(p_payload -> 'settings') is distinct from 'object'
    or jsonb_typeof(p_payload -> 'exportedAt') is distinct from 'string'
    or exists (select 1 from jsonb_object_keys(p_payload) k where k not in
      ('format','version','exportedAt','tasks','routineItems','routineCompletions','messages','settings','memories','readings')) then
    raise sqlstate 'PT400' using message = 'Formato de exportação não suportado.';
  end if;
  foreach section in array array['tasks','routineItems','routineCompletions','messages','memories','readings'] loop
    if jsonb_typeof(p_payload -> section) is distinct from 'array' then
      raise sqlstate 'PT400' using message = 'Seção de exportação inválida.';
    end if;
  end loop;
  -- Serializes first creation and replacement for this owner. Revision sequence
  -- remains monotonic across deletion/recreation, preventing stale-write ABA.
  perform pg_advisory_xact_lock(hashtextextended(owner::text, 701));
  select revision into stored_revision from public.auron_snapshots where owner_id = owner for update;
  if coalesce(stored_revision, 0) <> p_expected_revision then
    raise sqlstate 'PT409' using message = 'A cópia mudou. Consulte novamente antes de substituir.';
  end if;
  insert into public.auron_snapshots(owner_id, revision, schema_version, payload, updated_at)
  values (owner, nextval('auron_private.snapshot_revisions'), 2, p_payload, now())
  on conflict (owner_id) do update set revision = excluded.revision, payload = excluded.payload, updated_at = excluded.updated_at;
  return public.auron_snapshot_get();
end;
$$;

create or replace function public.auron_snapshot_delete(p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare owner uuid := (select auth.uid()); stored_revision bigint;
begin
  if not public.is_auron_owner() then raise sqlstate 'PT403' using message = 'Acesso restrito ao proprietário confirmado.'; end if;
  perform pg_advisory_xact_lock(hashtextextended(owner::text, 701));
  select revision into stored_revision from public.auron_snapshots where owner_id = owner for update;
  if p_expected_revision is null or p_expected_revision <= 0 or stored_revision is distinct from p_expected_revision then
    raise sqlstate 'PT409' using message = 'A cópia mudou. Consulte novamente antes de excluir.';
  end if;
  delete from public.auron_snapshots where owner_id = owner;
  return jsonb_build_object('deleted', true);
end;
$$;

revoke all on function public.auron_snapshot_get() from public, anon;
revoke all on function public.auron_snapshot_save(jsonb, bigint) from public, anon;
revoke all on function public.auron_snapshot_delete(bigint) from public, anon;
grant execute on function public.auron_snapshot_get() to authenticated;
grant execute on function public.auron_snapshot_save(jsonb, bigint) to authenticated;
grant execute on function public.auron_snapshot_delete(bigint) to authenticated;
commit;

-- Required outside SQL: enable Auth Hook "Before User Created" and select
-- public.auron_before_user_created. Until configured, keep signups disabled.
