const encoder = new TextEncoder();

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders() });
    }

    try {
      if (url.pathname === "/signup" && request.method === "POST") {
        return await withRateLimit(request, env, "signup", function () { return handleSignup(request, env); });
      }
      if (url.pathname === "/login" && request.method === "POST") {
        return await withRateLimit(request, env, "login", function () { return handleLogin(request, env); });
      }
      if (url.pathname === "/google-signin" && request.method === "POST") {
        return await withRateLimit(request, env, "google", function () { return handleGoogleSignIn(request, env); });
      }
      if (url.pathname === "/verify-email" && request.method === "POST") {
        return await withRateLimit(request, env, "verify", function () { return handleVerifyEmail(request, env); });
      }
      if (url.pathname === "/resend-code" && request.method === "POST") {
        return await withRateLimit(request, env, "resend", function () { return handleResendCode(request, env); });
      }
      if (url.pathname === "/refresh" && request.method === "POST") {
        return await handleRefresh(request, env);
      }
      if (url.pathname === "/logout" && request.method === "POST") {
        return await handleLogout(request, env);
      }
    } catch (e) {
      return jsonResponse({ error: "Something went wrong. Please try again." }, 500);
    }

    return jsonResponse({ error: "Not found" }, 404);
  },
};

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "content-type": "application/json",
  };
}

function jsonResponse(body, status) {
  return new Response(JSON.stringify(body), { status: status || 200, headers: corsHeaders() });
}

async function withRateLimit(request, env, action, handler) {
  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  const windowStart = Date.now() - 10 * 60 * 1000;

  await env.DB.prepare("DELETE FROM rate_limits WHERE created_at < ?").bind(windowStart).run();

  const row = await env.DB.prepare(
    "SELECT COUNT(*) as count FROM rate_limits WHERE ip = ? AND action = ? AND created_at > ?"
  ).bind(ip, action, windowStart).first();

  if (row.count >= 8) {
    return jsonResponse({ error: "Too many attempts. Please wait a few minutes." }, 429);
  }

  await env.DB.prepare("INSERT INTO rate_limits (ip, action, created_at) VALUES (?, ?, ?)")
    .bind(ip, action, Date.now()).run();

  return handler();
}

async function hashPassword(password, saltHex) {
  const salt = saltHex
    ? new Uint8Array(saltHex.match(/.{2}/g).map(function (b) { return parseInt(b, 16); }))
    : crypto.getRandomValues(new Uint8Array(16));

  const keyMaterial = await crypto.subtle.importKey(
    "raw", encoder.encode(password), "PBKDF2", false, ["deriveBits"]
  );
  const derivedBits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt: salt, iterations: 100000, hash: "SHA-256" },
    keyMaterial,
    256
  );
  const hashHex = Array.from(new Uint8Array(derivedBits))
    .map(function (b) { return b.toString(16).padStart(2, "0"); }).join("");
  const saltHexOut = Array.from(salt)
    .map(function (b) { return b.toString(16).padStart(2, "0"); }).join("");

  return { hash: hashHex, salt: saltHexOut };
}

async function logAuditEvent(request, env, email, eventType) {
  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  try {
    await env.DB.prepare(
      "INSERT INTO audit_log (email, ip, event, created_at) VALUES (?, ?, ?, ?)"
    ).bind(email || null, ip, eventType, Date.now()).run();
  } catch (e) {
    // Never let a logging failure break the actual auth flow.
  }
}

function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let result = 0;
  for (let i = 0; i < a.length; i++) {
    result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return result === 0;
}

function isValidEmail(email) {
  return typeof email === "string" && email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}
function isValidPassword(password) {
  return typeof password === "string" && password.length >= 6 && password.length <= 128;
}
function sanitizeDisplayName(name) {
  if (typeof name !== "string") return "";
  return name.slice(0, 60).replace(/[<>]/g, "");
}

async function createAccessToken(userId, env) {
  const payload = JSON.stringify({ uid: userId, exp: Date.now() + 15 * 60 * 1000 });
  const payloadB64 = btoa(payload);
  const key = await crypto.subtle.importKey(
    "raw", encoder.encode(env.SESSION_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, encoder.encode(payloadB64));
  const sigB64 = btoa(String.fromCharCode.apply(null, new Uint8Array(sig)));
  return payloadB64 + "." + sigB64;
}

async function createRefreshToken(userId, env) {
  const token = crypto.randomUUID() + crypto.randomUUID();
  const now = Date.now();
  const expiresAt = now + 30 * 24 * 60 * 60 * 1000;

  await env.REFRESH_TOKENS.put(token, userId, { expirationTtl: 30 * 24 * 60 * 60 });
  await env.DB.prepare(
    "INSERT INTO sessions (refresh_token, user_id, created_at, expires_at, used) VALUES (?, ?, ?, ?, 0)"
  ).bind(token, userId, now, expiresAt).run();

  return token;
}

async function issueTokenPair(userId, env) {
  const accessToken = await createAccessToken(userId, env);
  const refreshToken = await createRefreshToken(userId, env);
  return { accessToken: accessToken, refreshToken: refreshToken };
}

async function revokeAllSessions(userId, env) {
  const rows = await env.DB.prepare("SELECT refresh_token FROM sessions WHERE user_id = ?")
    .bind(userId).all();
  for (const row of rows.results) {
    await env.REFRESH_TOKENS.delete(row.refresh_token);
  }
  await env.DB.prepare("DELETE FROM sessions WHERE user_id = ?").bind(userId).run();
}

async function sendVerificationEmail(email, code, env) {
  const html = "<div style=\"font-family: -apple-system, sans-serif; max-width: 480px; margin: 0 auto; background: #0A0B0F; padding: 40px 32px; border-radius: 16px;\">" +
    "<div style=\"text-align: center; margin-bottom: 24px;\">" +
    "<span style=\"font-size: 28px; font-weight: bold; background: linear-gradient(135deg, #4DB6AC, #3B82F6); -webkit-background-clip: text; -webkit-text-fill-color: transparent;\">Thallo</span>" +
    "</div>" +
    "<h2 style=\"color: #F6F7F9; text-align: center; font-size: 20px; margin-bottom: 12px;\">Verify your email</h2>" +
    "<p style=\"color: #8B8E98; text-align: center; font-size: 14px; margin-bottom: 32px;\">Enter this code to verify your Thallo account:</p>" +
    "<div style=\"background: #15171E; border: 1px solid #23262F; border-radius: 12px; padding: 20px; text-align: center; margin-bottom: 24px;\">" +
    "<span style=\"font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #4DB6AC;\">" + code + "</span>" +
    "</div>" +
    "<p style=\"color: #656B78; text-align: center; font-size: 12px;\">This code expires in 15 minutes. If you did not request this, you can safely ignore this email.</p>" +
    "</div>";

  await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": "Bearer " + env.RESEND_API_KEY,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: "Thallo <noreply@notify.bkbshop.ir>",
      to: [email],
      subject: "Verify your Thallo account",
      html: html,
    }),
  });
}

async function handleSignup(request, env) {
  const body = await request.json().catch(function () { return null; });
  if (!body) return jsonResponse({ error: "Invalid request body" }, 400);
  const email = body.email;
  const password = body.password;
  const displayName = body.displayName;

  if (!isValidEmail(email)) {
    return jsonResponse({ error: "Please enter a valid email address." }, 400);
  }
  if (!isValidPassword(password)) {
    return jsonResponse({ error: "Password must be 6-128 characters." }, 400);
  }
  const cleanName = sanitizeDisplayName(displayName);
  const cleanEmail = email.toLowerCase().trim();

  const existing = await env.DB.prepare("SELECT id FROM users WHERE email = ?")
    .bind(cleanEmail).first();
  if (existing) {
    return jsonResponse({ error: "An account with that email already exists." }, 409);
  }

  const id = crypto.randomUUID();
  const hashed = await hashPassword(password);
  const now = Date.now();

  await env.DB.prepare(
    "INSERT INTO users (id, email, password_hash, password_salt, display_name, created_at) VALUES (?, ?, ?, ?, ?, ?)"
  ).bind(id, cleanEmail, hashed.hash, hashed.salt, cleanName, now).run();

  const code = Math.floor(100000 + Math.random() * 900000).toString();
  await env.DB.prepare(
    "INSERT OR REPLACE INTO verification_codes (email, code, expires_at) VALUES (?, ?, ?)"
  ).bind(cleanEmail, code, Date.now() + 15 * 60 * 1000).run();
  await sendVerificationEmail(cleanEmail, code, env);

  await logAuditEvent(request, env, cleanEmail, "signup");

  const tokens = await issueTokenPair(id, env);
  return jsonResponse({
    userId: id, email: cleanEmail, displayName: cleanName, emailVerified: false,
    accessToken: tokens.accessToken, refreshToken: tokens.refreshToken,
  });
}

async function handleLogin(request, env) {
  const body = await request.json().catch(function () { return null; });
  if (!body) return jsonResponse({ error: "Invalid request body" }, 400);
  const email = body.email;
  const password = body.password;

  if (!isValidEmail(email) || !isValidPassword(password)) {
    return jsonResponse({ error: "Invalid email or password." }, 401);
  }
  const cleanEmail = email.toLowerCase().trim();

  const user = await env.DB.prepare("SELECT * FROM users WHERE email = ?")
    .bind(cleanEmail).first();

  if (user && user.locked_until && user.locked_until > Date.now()) {
    const minutesLeft = Math.ceil((user.locked_until - Date.now()) / 60000);
    return jsonResponse({ error: "Account temporarily locked. Try again in " + minutesLeft + " minute(s)." }, 423);
  }

  const hashed = await hashPassword(password, user ? user.password_salt : undefined);
  const passwordOk = user && user.password_hash && timingSafeEqual(hashed.hash, user.password_hash);

  if (!passwordOk) {
    if (user) {
      const attempts = (user.failed_attempts || 0) + 1;
      const lockUntil = attempts >= 5 ? Date.now() + 15 * 60 * 1000 : 0;
      await env.DB.prepare("UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?")
        .bind(attempts, lockUntil, user.id).run();
      await logAuditEvent(request, env, cleanEmail, lockUntil > 0 ? "account_locked" : "login_failed");
    } else {
      await logAuditEvent(request, env, cleanEmail, "login_failed_unknown_email");
    }
    return jsonResponse({ error: "Invalid email or password." }, 401);
  }

  await env.DB.prepare("UPDATE users SET failed_attempts = 0, locked_until = 0 WHERE id = ?")
    .bind(user.id).run();
  await logAuditEvent(request, env, cleanEmail, "login_success");

  const tokens = await issueTokenPair(user.id, env);
  return jsonResponse({
    userId: user.id, email: user.email, displayName: user.display_name,
    emailVerified: user.email_verified === 1,
    accessToken: tokens.accessToken, refreshToken: tokens.refreshToken,
  });
}

async function handleGoogleSignIn(request, env) {
  const body = await request.json().catch(function () { return null; });
  if (!body || typeof body.idToken !== "string") {
    return jsonResponse({ error: "Invalid request body" }, 400);
  }

  const verifyUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + encodeURIComponent(body.idToken);
  const verifyResponse = await fetch(verifyUrl);
  if (!verifyResponse.ok) {
    return jsonResponse({ error: "Google sign-in failed. Please try again." }, 401);
  }
  const payload = await verifyResponse.json();

  if (payload.aud !== env.GOOGLE_CLIENT_ID) {
    return jsonResponse({ error: "Google sign-in failed. Please try again." }, 401);
  }

  const googleId = payload.sub;
  const email = (payload.email || "").toLowerCase();
  const displayName = sanitizeDisplayName(payload.name || "");

  let user = await env.DB.prepare("SELECT * FROM users WHERE google_id = ?").bind(googleId).first();

  if (!user) {
    const existingByEmail = email ? await env.DB.prepare("SELECT * FROM users WHERE email = ?").bind(email).first() : null;

    if (existingByEmail) {
      await env.DB.prepare("UPDATE users SET google_id = ? WHERE id = ?").bind(googleId, existingByEmail.id).run();
      user = existingByEmail;
    } else {
      const id = crypto.randomUUID();
      const now = Date.now();
      await env.DB.prepare(
        "INSERT INTO users (id, email, google_id, display_name, created_at, email_verified) VALUES (?, ?, ?, ?, ?, 1)"
      ).bind(id, email, googleId, displayName, now).run();
      user = { id: id, email: email, display_name: displayName, email_verified: 1 };
    }
  }

  const tokens = await issueTokenPair(user.id, env);
  return jsonResponse({
    userId: user.id, email: user.email, displayName: user.display_name || displayName,
    emailVerified: true,
    accessToken: tokens.accessToken, refreshToken: tokens.refreshToken,
  });
}

async function handleVerifyEmail(request, env) {
  const body = await request.json().catch(function () { return null; });
  if (!body || !body.email || !body.code) {
    return jsonResponse({ error: "Invalid request body" }, 400);
  }
  const cleanEmail = body.email.toLowerCase().trim();

  const row = await env.DB.prepare(
    "SELECT * FROM verification_codes WHERE email = ?"
  ).bind(cleanEmail).first();

  if (!row || row.expires_at < Date.now()) {
    return jsonResponse({ error: "Code expired. Please request a new one." }, 400);
  }
  if (!timingSafeEqual(row.code, body.code)) {
    return jsonResponse({ error: "Incorrect code." }, 400);
  }

  await env.DB.prepare("UPDATE users SET email_verified = 1 WHERE email = ?").bind(cleanEmail).run();
  await env.DB.prepare("DELETE FROM verification_codes WHERE email = ?").bind(cleanEmail).run();

  return jsonResponse({ ok: true });
}

async function handleResendCode(request, env) {
  const body = await request.json().catch(function () { return null; });
  if (!body || !body.email) {
    return jsonResponse({ error: "Invalid request body" }, 400);
  }
  const cleanEmail = body.email.toLowerCase().trim();

  const user = await env.DB.prepare("SELECT id FROM users WHERE email = ?").bind(cleanEmail).first();
  if (!user) {
    return jsonResponse({ error: "No account found with that email." }, 404);
  }

  const code = Math.floor(100000 + Math.random() * 900000).toString();
  await env.DB.prepare(
    "INSERT OR REPLACE INTO verification_codes (email, code, expires_at) VALUES (?, ?, ?)"
  ).bind(cleanEmail, code, Date.now() + 15 * 60 * 1000).run();
  await sendVerificationEmail(cleanEmail, code, env);

  return jsonResponse({ ok: true });
}

async function handleRefresh(request, env) {
  const body = await request.json().catch(function () { return null; });
  if (!body || typeof body.refreshToken !== "string") {
    return jsonResponse({ error: "Invalid request body" }, 400);
  }

  const presentedToken = body.refreshToken;
  const session = await env.DB.prepare(
    "SELECT * FROM sessions WHERE refresh_token = ?"
  ).bind(presentedToken).first();

  if (!session) {
    return jsonResponse({ error: "Session expired. Please sign in again." }, 401);
  }

  if (session.used) {
    const user = await env.DB.prepare("SELECT email FROM users WHERE id = ?").bind(session.user_id).first();
    await logAuditEvent(request, env, user ? user.email : null, "refresh_token_reuse_detected");
    await revokeAllSessions(session.user_id, env);
    return jsonResponse({ error: "Session expired. Please sign in again." }, 401);
  }

  if (session.expires_at < Date.now()) {
    await env.DB.prepare("DELETE FROM sessions WHERE refresh_token = ?").bind(presentedToken).run();
    await env.REFRESH_TOKENS.delete(presentedToken);
    return jsonResponse({ error: "Session expired. Please sign in again." }, 401);
  }

  await env.DB.prepare("UPDATE sessions SET used = 1 WHERE refresh_token = ?").bind(presentedToken).run();
  await env.REFRESH_TOKENS.delete(presentedToken);

  const user = await env.DB.prepare("SELECT email_verified FROM users WHERE id = ?").bind(session.user_id).first();
  const tokens = await issueTokenPair(session.user_id, env);
  return jsonResponse({
    accessToken: tokens.accessToken, refreshToken: tokens.refreshToken,
    emailVerified: user ? user.email_verified === 1 : false,
  });
}

async function handleLogout(request, env) {
  const body = await request.json().catch(function () { return null; });
  if (body && typeof body.refreshToken === "string") {
    await env.REFRESH_TOKENS.delete(body.refreshToken);
    await env.DB.prepare("DELETE FROM sessions WHERE refresh_token = ?").bind(body.refreshToken).run();
  }
  return jsonResponse({ ok: true });
}
