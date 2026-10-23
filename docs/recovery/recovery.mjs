import { SUPABASE_URL, SUPABASE_PUBLIC_KEY } from "./settings.mjs";
import { validateSettings, recoveryFromFragment, validatePassword } from "./policy.mjs";

const status = document.querySelector("#status");
const form = document.querySelector("#recovery");
const password = document.querySelector("#password");
const confirmation = document.querySelector("#confirmation");
const submit = document.querySelector("#submit");
let token = recoveryFromFragment(location.hash);
// Fragments never reach the host. Remove tokens before any asynchronous work;
// they live only in this page's memory, never storage, logs or subsequent URLs.
history.replaceState(null, "", location.pathname);
let base;

async function request(path, method = "GET", body) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    return await fetch(`${base}/auth/v1/${path}`, {
      method,
      headers: { apikey: SUPABASE_PUBLIC_KEY, Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
      credentials: "omit", redirect: "error", cache: "no-store", referrerPolicy: "no-referrer", signal: controller.signal,
    });
  } finally { clearTimeout(timeout); }
}

async function initialize() {
  try {
    if (location.protocol !== "https:") throw new Error("Abra esta página hospedada em HTTPS.");
    base = validateSettings(SUPABASE_URL, SUPABASE_PUBLIC_KEY);
    if (!token) throw new Error("Abra o link de recuperação recebido por e-mail. Se você acabou de confirmar o cadastro, volte ao AURON para entrar.");
    const response = await request("user");
    if (!response.ok) throw new Error("O link expirou ou não foi aceito. Solicite outro link no AURON.");
    const user = await response.json();
    if (typeof user.email !== "string" || !user.email) throw new Error("Não foi possível confirmar a conta deste link.");
    status.textContent = `Conta: ${user.email}. Confira se este é seu e-mail.`;
    form.hidden = false;
  } catch (error) {
    token = null;
    status.textContent = error instanceof TypeError || error.name === "AbortError"
      ? "Não foi possível acessar o serviço com segurança. Confira a conexão ou solicite outro link."
      : error.message;
  }
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!token || submit.disabled) return;
  const validation = validatePassword(password.value, confirmation.value);
  if (validation) { status.textContent = validation; return; }
  let newPassword = password.value;
  password.value = "";
  confirmation.value = "";
  submit.disabled = true;
  status.textContent = "Atualizando a senha…";
  try {
    const response = await request("user", "PUT", { password: newPassword });
    newPassword = "";
    if (!response.ok) {
      status.textContent = response.status === 401 || response.status === 403
        ? "O link expirou. Solicite outro link no AURON."
        : "A senha não foi aceita. Use uma senha mais forte ou solicite outro link e tente novamente.";
      if (response.status === 401 || response.status === 403) { token = null; form.hidden = true; }
      return;
    }
    form.hidden = true;
    status.textContent = "Senha atualizada. Volte ao aplicativo AURON e entre com a nova senha.";
    // Revoke the browser recovery session when possible; never persist its tokens.
    await request("logout?scope=local", "POST", {}).catch(() => {});
    token = null;
  } catch {
    status.textContent = "Não foi possível confirmar a alteração. Tente entrar com a nova senha ou solicite outro link no AURON.";
  } finally {
    newPassword = "";
    submit.disabled = false;
  }
});

window.addEventListener("pagehide", () => { token = null; password.value = ""; confirmation.value = ""; });
initialize();
