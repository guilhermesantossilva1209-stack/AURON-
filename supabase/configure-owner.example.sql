-- Administrative example ONLY. Replace with your own real email in SQL Editor.
-- Run AFTER the migration. This is not an APK credential and is not app code.
-- Does not overwrite an existing owner; changing ownership needs deliberate review.
insert into auron_private.owner_allowlist(singleton, email)
values (true, lower(trim('SUBSTITUA-PELO-SEU-EMAIL@example.invalid')))
on conflict (singleton) do nothing;

-- In Authentication > Hooks, activate Before User Created with
-- public.auron_before_user_created. Enable email signup only AFTER that hook
-- is active. Keep email confirmation enabled. After registering and confirming
-- the owner, disable new signups again. Never commit your edited copy.
