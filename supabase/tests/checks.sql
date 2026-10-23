\set ON_ERROR_STOP on
-- Execute only after bootstrap.sql and the migration in the same disposable DB.
do $$
begin
  if current_database() <> 'auron_security_test' or to_regnamespace('auron_test_checks') is null then
    raise exception 'Refusing checks: use the dedicated disposable bootstrap fixture.';
  end if;
end;
$$;
begin;

select auron_test_checks.assert_true(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.auron_snapshots'::regclass),
  'Snapshots must enable and force RLS');
select auron_test_checks.assert_true(
  (select count(*) = 1 from pg_policies where schemaname = 'public' and tablename = 'auron_snapshots'),
  'Applying the migration twice must keep one SELECT policy');

set local role supabase_auth_admin;
select auron_test_checks.assert_true(
  public.auron_before_user_created('{"user":{"email":"owner@example.invalid"}}') #>> '{error,http_code}' = '403',
  'Unconfigured owner allowlist must deny every initial registration');
reset role;
insert into auron_private.owner_allowlist(singleton, email) values (true, 'owner@example.invalid');

set local role supabase_auth_admin;
select auron_test_checks.assert_true(
  public.auron_before_user_created('{"user":{"email":" OWNER@example.invalid "}}') = '{}'::jsonb,
  'The authorized normalized owner email can register');
select auron_test_checks.assert_true(
  public.auron_before_user_created('{"user":{"email":"other@example.invalid"}}') #>> '{error,http_code}' = '403',
  'An unlisted email must be denied by the Auth hook');
select auron_test_checks.assert_true(
  public.auron_before_user_created('{"user":{}}') #>> '{error,http_code}' = '403',
  'Missing email must be denied by the Auth hook');
reset role;
\echo 'PASS: Auth hook fails closed and permits only configured email.'

set local role anon;
select auron_test_checks.expect_error('select public.auron_snapshot_get()', '42501');
select auron_test_checks.expect_error('select public.auron_snapshot_save(auron_test_checks.payload(),0)', '42501');
select auron_test_checks.expect_error('select public.auron_snapshot_delete(1)', '42501');
select auron_test_checks.expect_error('select public.is_auron_owner()', '42501');
select auron_test_checks.expect_error('select * from public.auron_snapshots', '42501');
select auron_test_checks.expect_error('select * from auron_private.owner_allowlist', '42501');
select auron_test_checks.expect_error('select public.auron_before_user_created(''{}''::jsonb)', '42501');
reset role;
\echo 'PASS: anonymous clients have no snapshot, owner-table or hook access.'

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-4000-8000-000000000001', true);
select auron_test_checks.assert_true(public.is_auron_owner(), 'Confirmed owner must be recognized');
select auron_test_checks.assert_true(public.auron_snapshot_get() is null, 'Initial cloud state is empty');
select auron_test_checks.expect_error('select * from auron_private.owner_allowlist', '42501');
select auron_test_checks.expect_error('select public.auron_before_user_created(''{}''::jsonb)', '42501');
select auron_test_checks.expect_error(
  'insert into public.auron_snapshots(owner_id,revision,schema_version,payload) values (auth.uid(),9000,2,''{}''::jsonb)', '42501');
select auron_test_checks.expect_error('update public.auron_snapshots set revision = 9000', '42501');
select auron_test_checks.expect_error('delete from public.auron_snapshots', '42501');
select auron_test_checks.expect_error('select nextval(''auron_private.snapshot_revisions'')', '42501');

do $$
declare payload jsonb := auron_test_checks.payload(); first_result jsonb; second_result jsonb;
        revision_one bigint; revision_two bigint;
begin
  first_result := public.auron_snapshot_save(payload, 0);
  revision_one := (first_result ->> 'revision')::bigint;
  perform auron_test_checks.assert_true(revision_one > 0, 'First revision must be positive');
  perform auron_test_checks.assert_true(first_result -> 'payload' = payload, 'All snapshot sections must round-trip');
  perform auron_test_checks.assert_true(public.auron_snapshot_get() = first_result, 'RPC read returns stored snapshot');
  perform auron_test_checks.assert_true((select count(*) = 1 from public.auron_snapshots), 'Owner SELECT is allowed by RLS');
  perform auron_test_checks.expect_error('select public.auron_snapshot_save(auron_test_checks.payload(),0)', 'PT409');
  second_result := public.auron_snapshot_save(jsonb_set(payload, '{settings,userName}', '"Updated fixture"'), revision_one);
  revision_two := (second_result ->> 'revision')::bigint;
  perform auron_test_checks.assert_true(revision_two > revision_one, 'Updating requires and advances the observed revision');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_save(auron_test_checks.payload(),%s)', revision_one), 'PT409');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_delete(%s)', revision_one), 'PT409');
  perform auron_test_checks.assert_true(public.auron_snapshot_get() = second_result, 'Stale save/delete cannot change stored data');
  perform set_config('auron_test.current_revision', revision_two::text, true);
end;
$$;
reset role;
\echo 'PASS: confirmed owner can read/write through RPC; direct mutation and stale writes are rejected.'

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-4000-8000-000000000002', true);
select set_config('request.jwt.claims', '{"email":"owner@example.invalid"}', true);
select auron_test_checks.assert_true(not public.is_auron_owner(), 'Unlisted UID cannot spoof owner by email claim');
select auron_test_checks.assert_true((select count(*) = 0 from public.auron_snapshots), 'RLS hides the owner snapshot from another account');
select auron_test_checks.expect_error('select public.auron_snapshot_get()', 'PT403');
select auron_test_checks.expect_error('select public.auron_snapshot_save(auron_test_checks.payload(),0)', 'PT403');
select auron_test_checks.expect_error('select public.auron_snapshot_delete(1)', 'PT403');

select set_config('request.jwt.claim.sub', '10000000-0000-4000-8000-000000000003', true);
select auron_test_checks.assert_true(not public.is_auron_owner(), 'Owner email without confirmation must have no access');
select auron_test_checks.assert_true((select count(*) = 0 from public.auron_snapshots), 'RLS hides snapshots from unconfirmed identity');
select auron_test_checks.expect_error('select public.auron_snapshot_get()', 'PT403');
select auron_test_checks.expect_error('select public.auron_snapshot_save(auron_test_checks.payload(),0)', 'PT403');
select auron_test_checks.expect_error('select public.auron_snapshot_delete(1)', 'PT403');

select set_config('request.jwt.claim.sub', '', true);
select auron_test_checks.assert_true(not public.is_auron_owner(), 'Missing identity fails closed');
select auron_test_checks.expect_error('select public.auron_snapshot_get()', 'PT403');
reset role;
\echo 'PASS: other accounts, unconfirmed owner and missing identity cannot read/write owner data.'

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-4000-8000-000000000001', true);
do $$
declare revision bigint := current_setting('auron_test.current_revision')::bigint;
        payload jsonb := auron_test_checks.payload(); before_snapshot jsonb := public.auron_snapshot_get();
        recreated jsonb; new_revision bigint;
begin
  perform auron_test_checks.expect_error('select public.auron_snapshot_save(auron_test_checks.payload(),null)', 'PT400');
  perform auron_test_checks.expect_error('select public.auron_snapshot_save(auron_test_checks.payload(),-1)', 'PT400');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_save(null,%s)', revision), 'PT413');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_save(''[]''::jsonb,%s)', revision), 'PT413');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_save(%L::jsonb,%s)', payload - 'memories', revision), 'PT400');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_save(%L::jsonb,%s)', jsonb_set(payload, '{version}', '3'), revision), 'PT400');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_save(%L::jsonb,%s)', payload || '{"access_token":"dummy-test-only"}'::jsonb, revision), 'PT400');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_save(%L::jsonb,%s)', jsonb_set(payload, '{memories}', jsonb_build_array(repeat('x',1048577))), revision), 'PT413');
  perform auron_test_checks.assert_true(public.auron_snapshot_get() = before_snapshot, 'Invalid payloads preserve existing cloud copy');

  perform auron_test_checks.assert_true(public.auron_snapshot_delete(revision) = '{"deleted":true}'::jsonb, 'Current revision can be explicitly deleted');
  perform auron_test_checks.assert_true(public.auron_snapshot_get() is null, 'Cloud deletion takes effect');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_delete(%s)', revision), 'PT409');
  recreated := public.auron_snapshot_save(payload, 0);
  new_revision := (recreated ->> 'revision')::bigint;
  perform auron_test_checks.assert_true(new_revision > revision, 'Recreation must not reuse a deleted revision (ABA protection)');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_save(auron_test_checks.payload(),%s)', revision), 'PT409');
  perform auron_test_checks.expect_error(format('select public.auron_snapshot_delete(%s)', revision), 'PT409');
  perform auron_test_checks.assert_true(public.auron_snapshot_get() = recreated, 'Old observers cannot overwrite or delete recreated snapshot');
end;
$$;
reset role;
\echo 'PASS: payload validation, explicit deletion and delete/recreate revision protection.'

-- No test account/snapshot state is committed. Sequence values may advance,
-- which is expected for PostgreSQL sequences and intentional ABA protection.
rollback;
\echo 'PASS: all AURON PostgreSQL assertions completed. Hosted Supabase Auth/API remains a separate integration test.'
