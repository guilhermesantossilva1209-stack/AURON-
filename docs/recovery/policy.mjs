export function validateSettings(base, key) {
  const url = new URL(base);
  if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash || url.pathname !== "/") {
    throw new Error("Configure a URL HTTPS raiz do projeto.");
  }
  if (url.hostname === "seu-projeto.supabase.co" || key.startsWith("sb_secret_")) throw new Error("A página ainda não tem uma configuração pública válida.");
  const publishable = /^sb_publishable_[A-Za-z0-9_-]{10,}$/.test(key);
  let anon = false;
  if (!publishable && key.split(".").length === 3 && key.length <= 8192) {
    try {
      const part = key.split(".")[1].replaceAll("-", "+").replaceAll("_", "/");
      anon = JSON.parse(atob(part.padEnd(Math.ceil(part.length / 4) * 4, "="))).role === "anon";
    } catch { /* invalid public key */ }
  }
  if (!publishable && !anon) throw new Error("Use uma chave pública publishable ou anon.");
  return url.origin;
}

export function recoveryFromFragment(fragment) {
  const fields = new URLSearchParams(fragment.replace(/^#/, ""));
  if (fields.get("type") !== "recovery") return null;
  const token = fields.get("access_token") || "";
  if (token.length < 20 || token.length > 32768 || !/^[A-Za-z0-9_.-]+$/.test(token)) return null;
  return token;
}

export function validatePassword(password, confirmation) {
  if (password.length < 8 || password.length > 4096) return "Use uma senha de 8 a 4096 caracteres; o serviço pode exigir critérios adicionais.";
  if (password !== confirmation) return "As senhas precisam ser iguais.";
  return null;
}
