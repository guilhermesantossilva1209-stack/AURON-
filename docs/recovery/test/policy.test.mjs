import test from "node:test";
import assert from "node:assert/strict";
import { validateSettings, recoveryFromFragment, validatePassword } from "../policy.mjs";

test("only explicit recovery tokens are accepted, never signup sessions", () => {
  const token = "eyJ.header.fixtureToken1234567890";
  assert.equal(recoveryFromFragment(`#type=recovery&access_token=${token}&refresh_token=discard-me`), token);
  assert.equal(recoveryFromFragment(`#type=signup&access_token=${token}`), null);
  assert.equal(recoveryFromFragment(`#access_token=${token}`), null);
  assert.equal(recoveryFromFragment("#type=recovery&access_token=bad%0Atoken"), null);
  assert.equal(recoveryFromFragment("#type=recovery&access_token=" + "x".repeat(32769)), null);
});

test("configuration requires HTTPS root URL and public key", () => {
  const key = "sb_publishable_fixture123456789";
  assert.equal(validateSettings("https://project.supabase.co", key), "https://project.supabase.co");
  for (const url of ["http://project.supabase.co", "https://user:password@project.supabase.co", "https://project.supabase.co/path", "https://project.supabase.co?token=x", "https://project.supabase.co#private"]) {
    assert.throws(() => validateSettings(url, key));
  }
  assert.throws(() => validateSettings("https://seu-projeto.supabase.co", key));
  assert.throws(() => validateSettings("https://project.supabase.co", "sb_secret_neverForTheBrowser123"));
});

test("legacy anon key is accepted and service_role key rejected", () => {
  const fixture = role => `header.${Buffer.from(JSON.stringify({ role })).toString("base64url")}.fixtureSignature`;
  assert.equal(validateSettings("https://project.supabase.co", fixture("anon")), "https://project.supabase.co");
  assert.throws(() => validateSettings("https://project.supabase.co", fixture("service_role")));
});

test("new password must meet length and confirmation without trimming", () => {
  assert.equal(validatePassword("long-password", "long-password"), null);
  assert.equal(validatePassword(" spaces ", " spaces "), null);
  assert.ok(validatePassword("short", "short"));
  assert.ok(validatePassword("long-password", "different"));
  assert.ok(validatePassword("x".repeat(4097), "x".repeat(4097)));
});
