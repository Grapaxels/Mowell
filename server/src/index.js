import "dotenv/config";
import bcrypt from "bcryptjs";
import cors from "cors";
import express from "express";
import { OAuth2Client } from "google-auth-library";
import helmet from "helmet";
import jwt from "jsonwebtoken";
import mongoose from "mongoose";
import nodemailer from "nodemailer";
import QRCode from "qrcode";
import crypto from "node:crypto";
import { resolveMx } from "node:dns/promises";
import { fileURLToPath } from "node:url";
import { CallRoom, CallSignal, ConnectionRequest, Conversation, DeviceLink, LinkedDevice, Media, Message, TypingState, User } from "./models/index.js";

const mongoUri = process.env.MONGODB_URI || process.env.MONGO_URI;
if (!mongoUri) throw new Error("MONGODB_URI (or MONGO_URI) is required");
if (!process.env.JWT_SECRET) throw new Error("JWT_SECRET is required");
if (process.env.JWT_SECRET.length < 32) throw new Error("JWT_SECRET must contain at least 32 characters");

let mongoPromise = null;
const ensureMongo = () => {
  if (mongoose.connection.readyState === 1) return Promise.resolve();
  if (!mongoPromise) mongoPromise = mongoose.connect(mongoUri, { autoIndex: true, serverSelectionTimeoutMS: 10_000 }).catch((error) => { mongoPromise = null; throw error; });
  return mongoPromise;
};
const app = express();
app.use((_req, res, next) => { res.setHeader("Permissions-Policy", "camera=(self), microphone=(self), display-capture=(self), geolocation=(self)"); next(); });
const webRoot = fileURLToPath(new URL("../web", import.meta.url));
const webIndex = fileURLToPath(new URL("../web/index.html", import.meta.url));
const publicRoot = fileURLToPath(new URL("../public", import.meta.url));
app.use(helmet({
  crossOriginResourcePolicy: { policy: "cross-origin" },
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'"],
      styleSrc: ["'self'"],
      imgSrc: ["'self'", "data:", "blob:", "https://mowell-api.grapaxels.in"],
      mediaSrc: ["'self'", "blob:"],
      connectSrc: ["'self'", "https://mowell-api.grapaxels.in", "https://mowellweb.grapaxels.in"],
      workerSrc: ["'self'", "blob:"],
      objectSrc: ["'none'"],
      frameAncestors: ["'none'"]
    }
  }
}));
app.use(cors({ origin: process.env.ALLOWED_ORIGIN || "*" }));
app.use(express.json({ limit: "4mb" }));
app.use(express.static(publicRoot, {
  index: false,
  setHeaders: (res, path) => {
    if (path.endsWith(".apk")) {
      res.setHeader("Content-Type", "application/vnd.android.package-archive");
      res.setHeader("Content-Disposition", "attachment; filename=Mowell-v2.5.3.apk");
      res.setHeader("Cache-Control", "public, max-age=300, immutable");
    }
  }
}));
// Keep health checks and signed APK/update metadata available during a
// temporary MongoDB cold-start failure. All data routes connect lazily.
app.use(async (req, res, next) => {
  if (req.path === "/health" || req.path === "/v1/app/version") return next();
  try { await ensureMongo(); next(); }
  catch { res.status(503).json({ error: "Mowell data service is temporarily reconnecting. Please retry." }); }
});
const publicUser = (user) => ({
  id: user._id.toString(), username: user.username, email: user.email,
  displayName: user.displayName, avatarUrl: user.avatarUrl || null, lastSeenAt: user.lastSeenAt,
  emailVerified: Boolean(user.emailVerified)
});
// Mobile sessions intentionally persist until the user chooses Log out.
// Rotate JWT_SECRET to revoke all sessions after a security incident.
const issueToken = (user, extra = {}) => jwt.sign({ sub: user._id.toString(), username: user.username, ...extra }, process.env.JWT_SECRET, { issuer: "mowell-api" });
const usernamePattern = /^[a-z0-9_]{3,24}$/;
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
// Fixed Metered ICE configuration requested for Mowell. The route exposing it
// is authenticated so it is not included in the public web bundle.
const callIceServers = [
  { urls: ["stun:35.154.86.33:3478"] },
  {
    urls: ["turn:35.154.86.33:3478?transport=udp", "turn:35.154.86.33:3478?transport=tcp"],
    username: "turnuser",
    credential: "@Grapaxels1338"
  }
];
const disposableDomains = new Set([
  "10minutemail.com", "guerrillamail.com", "guerrillamailblock.com", "mailinator.com", "temp-mail.org",
  "tempmail.com", "throwawaymail.com", "yopmail.com", "sharklasers.com", "getnada.com", "dispostable.com",
  ...String(process.env.BLOCKED_EMAIL_DOMAINS || "").split(",").map((v) => v.trim().toLowerCase()).filter(Boolean)
]);
const emailCodeHash = (email, code) => crypto.createHmac("sha256", process.env.JWT_SECRET).update(`${email}:${code}`).digest("hex");
const deviceHash = (value) => crypto.createHmac("sha256", process.env.JWT_SECRET).update(`device:${String(value || "")}`).digest("hex");
const trustedForMs = 15 * 24 * 60 * 60 * 1000;
const trustDevice = (user, value) => {
  if (!value) return;
  const hash = deviceHash(value), now = new Date();
  user.trustedDevices = (user.trustedDevices || []).filter((item) => item.deviceHash !== hash && now - new Date(item.verifiedAt) < trustedForMs).slice(-9);
  user.trustedDevices.push({ deviceHash: hash, verifiedAt: now, lastUsedAt: now });
};
const smtpSettings = () => {
  const user = String(process.env.SMTP_USER || process.env.EMAIL_USER || process.env.MAIL_USER || "").trim();
  // Google displays App Passwords in groups. Removing whitespace makes either
  // the grouped or ungrouped value safe to paste into Vercel.
  const pass = String(process.env.SMTP_PASS || process.env.EMAIL_APP_PASSWORD || process.env.MAIL_PASS || "").replace(/\s/g, "");
  const host = String(process.env.SMTP_HOST || "smtp.gmail.com").trim();
  const parsedPort = Number(process.env.SMTP_PORT || 465);
  const port = Number.isInteger(parsedPort) && parsedPort > 0 ? parsedPort : 465;
  const secure = process.env.SMTP_SECURE == null
    ? port === 465
    : String(process.env.SMTP_SECURE).toLowerCase() === "true";
  return { user, pass, host, port, secure };
};
const mailer = () => {
  const smtp = smtpSettings();
  if (!smtp.user || !smtp.pass) {
    const error = new Error("Email verification is not configured");
    error.code = "SMTP_NOT_CONFIGURED";
    throw error;
  }
  return nodemailer.createTransport({
    host: smtp.host,
    port: smtp.port,
    secure: smtp.secure,
    auth: { user: smtp.user, pass: smtp.pass },
    connectionTimeout: 10000,
    greetingTimeout: 10000,
    socketTimeout: 15000
  });
};
const publicMailError = (error) => {
  const code = String(error?.code || "").toUpperCase();
  const responseCode = Number(error?.responseCode || 0);
  if (code === "SMTP_NOT_CONFIGURED") return "Email is not configured on the server. Add SMTP_USER and SMTP_PASS in Vercel, then redeploy";
  if (code === "EAUTH" || responseCode === 535) return "Gmail rejected the account or App Password. Use a Google App Password, not the normal Gmail password";
  if (["ETIMEDOUT", "ECONNECTION", "ESOCKET", "ECONNREFUSED"].includes(code)) return "The mail server could not be reached. Check SMTP_HOST, SMTP_PORT, and SMTP_SECURE";
  return "Verification email could not be sent. Check the Vercel function log for the SMTP error";
};
const logMailError = (error) => console.error("Mowell verification mail failed", {
  code: error?.code || null,
  command: error?.command || null,
  responseCode: error?.responseCode || null,
  message: error?.message || "Unknown SMTP error"
});
const validateEmailDomain = async (email) => {
  const domain = email.split("@")[1]?.toLowerCase();
  if (!domain || disposableDomains.has(domain) || [...disposableDomains].some((d) => domain.endsWith(`.${d}`))) throw new Error("Temporary email addresses are not allowed");
  const mx = await Promise.race([resolveMx(domain), new Promise((_, reject) => setTimeout(() => reject(new Error("Email domain check timed out")), 4000))]);
  if (!mx?.length) throw new Error("Email domain cannot receive verification mail");
};
const sendVerification = async (user, force = false) => {
  const now = Date.now();
  if (!force && user.verificationLastSentAt && now - new Date(user.verificationLastSentAt).getTime() < 60000) return;
  const code = String(crypto.randomInt(100000, 1000000));
  user.verificationCodeHash = emailCodeHash(user.email, code);
  user.verificationExpiresAt = new Date(now + 10 * 60 * 1000);
  user.verificationLastSentAt = new Date(now);
  user.verificationAttempts = 0;
  const smtp = smtpSettings();
  await mailer().sendMail({
    from: process.env.SMTP_FROM || `Mowell by Grapaxels <${smtp.user}>`, to: user.email,
    subject: `${code} is your Mowell verification code`,
    text: `Your Mowell verification code is ${code}. It expires in 10 minutes. If you did not request this, ignore this email.`,
    html: `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:28px"><h1 style="margin:0">Mowell</h1><p style="color:#7357f6">by Grapaxels</p><p>Use this code to verify your email:</p><div style="font-size:34px;font-weight:800;letter-spacing:8px;padding:18px;background:#ede8ff;border-radius:16px;text-align:center">${code}</div><p>This code expires in 10 minutes.</p></div>`
  });
  await user.save();
};
const sendLoginCode = async (user, force = false) => {
  const now = Date.now();
  if (!force && user.loginLastSentAt && now - new Date(user.loginLastSentAt).getTime() < 60000) return;
  const code = String(crypto.randomInt(100000, 1000000));
  user.loginCodeHash = emailCodeHash(user.email, `login:${code}`);
  user.loginExpiresAt = new Date(now + 10 * 60 * 1000);
  user.loginLastSentAt = new Date(now);
  user.loginAttempts = 0;
  const smtp = smtpSettings();
  await mailer().sendMail({
    from: process.env.SMTP_FROM || `Mowell from Grapaxels <${smtp.user}>`, to: user.email,
    subject: `${code} is your Mowell login code`,
    text: `Your Mowell login code is ${code}. It expires in 10 minutes. If you did not try to sign in, change your password.`,
    html: `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:28px"><h1 style="margin:0">Mowell</h1><p style="color:#6d55e7">from Grapaxels</p><p>Use this code to finish signing in:</p><div style="font-size:34px;font-weight:800;letter-spacing:8px;padding:18px;background:#eeeaff;border-radius:16px;text-align:center">${code}</div><p>This code expires in 10 minutes.</p></div>`
  });
  await user.save();
};
const sendPasswordReset = async (user) => {
  const code = String(crypto.randomInt(100000, 1000000));
  user.passwordResetCodeHash = emailCodeHash(user.email, `reset:${code}`);
  user.passwordResetExpiresAt = new Date(Date.now() + 10 * 60 * 1000);
  user.passwordResetAttempts = 0;
  const smtp = smtpSettings();
  await mailer().sendMail({
    from: process.env.SMTP_FROM || `Mowell by Grapaxels <${smtp.user}>`,
    to: user.email,
    subject: `${code} is your Mowell password reset code`,
    text: `Your Mowell password reset code is ${code}. It expires in 10 minutes. If you did not request this, ignore this email.`,
    html: `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:28px"><h1 style="margin:0">Mowell</h1><p style="color:#7357f6">by Grapaxels</p><p>Use this code to reset your password:</p><div style="font-size:34px;font-weight:800;letter-spacing:8px;padding:18px;background:#ede8ff;border-radius:16px;text-align:center">${code}</div><p>This code expires in 10 minutes.</p></div>`
  });
  await user.save();
};

const auth = async (req, res, next) => {
  try {
    const token = req.headers.authorization?.replace(/^Bearer\s+/i, "");
    if (!token) return res.status(401).json({ error: "Authentication required" });
    req.auth = jwt.verify(token, process.env.JWT_SECRET, { issuer: "mowell-api" });
    if (req.auth.sid) {
      const linked = await LinkedDevice.exists({ sessionId: req.auth.sid, user: req.auth.sub, revokedAt: null });
      if (!linked) return res.status(401).json({ error: "This linked device was logged out" });
    }
    const user = await User.findById(req.auth.sub).select("email emailVerified");
    if (!user) return res.status(401).json({ error: "Account not found" });
    if (!user.emailVerified) return res.status(403).json({ error: "Verify your email to continue", verificationRequired: true, email: user.email });
    next();
  } catch { res.status(401).json({ error: "Invalid or expired session" }); }
};

app.get("/health", (_req, res) => res.json({ ok: true, service: "mowell-api" }));
app.get("/v1/web/qr", async (_req, res, next) => {
  try {
    const svg = await QRCode.toString("https://mowellweb.grapaxels.in", {
      type: "svg", errorCorrectionLevel: "M", margin: 2,
      color: { dark: "#17131f", light: "#ffffff" }, width: 320
    });
    res.setHeader("Cache-Control", "public, max-age=3600");
    res.type("image/svg+xml").send(svg);
  } catch (error) { next(error); }
});

const linkTokenHash = (token) => crypto.createHash("sha256").update(String(token || "")).digest("hex");
app.post("/v1/web-link/session", async (req, res) => {
  const token = crypto.randomBytes(32).toString("base64url");
  const deviceName = String(req.body?.deviceName || req.headers["user-agent"] || "Mowell Web").slice(0, 120);
  await DeviceLink.create({ tokenHash: linkTokenHash(token), deviceId: String(req.body?.deviceId || "").slice(0, 100), deviceName, expiresAt: new Date(Date.now() + 2 * 60 * 1000) });
  res.status(201).json({ token, qrUrl: `/v1/web-link/qr?token=${encodeURIComponent(token)}`, expiresIn: 120 });
});
app.get("/v1/web-link/qr", async (req, res, next) => {
  try {
    const token = String(req.query.token || "");
    const link = await DeviceLink.findOne({ tokenHash: linkTokenHash(token), expiresAt: { $gt: new Date() } });
    if (!link) return res.status(410).json({ error: "This linking code expired" });
    const png = await QRCode.toBuffer(`mowell://link-device?token=${encodeURIComponent(token)}`, { type: "png", width: 360, margin: 2, errorCorrectionLevel: "M" });
    res.type("png").set("Cache-Control", "no-store").send(png);
  } catch (error) { next(error); }
});
app.get("/v1/web-link/session/:token", async (req, res) => {
  const tokenHash = linkTokenHash(req.params.token);
  const link = await DeviceLink.findOne({ tokenHash, expiresAt: { $gt: new Date() } }).populate("user");
  if (!link) return res.status(410).json({ error: "This linking code expired" });
  if (link.status !== "approved" || !link.user) return res.json({ status: "pending" });
  const user = link.user;
  const sessionId = crypto.randomUUID();
  await LinkedDevice.create({ user: user._id, sessionId, deviceId: link.deviceId, deviceName: link.deviceName, lastSeenAt: new Date() });
  await DeviceLink.deleteOne({ _id: link._id });
  res.json({ status: "approved", token: issueToken(user, { sid: sessionId, kind: "web" }), user: publicUser(user) });
});
app.post("/v1/web-link/approve", auth, async (req, res) => {
  const token = String(req.body.token || "");
  const link = await DeviceLink.findOne({ tokenHash: linkTokenHash(token), status: "pending", expiresAt: { $gt: new Date() } });
  if (!link) return res.status(410).json({ error: "This linking code is invalid or expired" });
  link.user = req.auth.sub; link.status = "approved"; link.approvedAt = new Date(); await link.save();
  res.json({ ok: true, message: "Mowell Web linked" });
});
app.get("/v1/linked-devices", auth, async (req, res) => {
  const devices = await LinkedDevice.find({ user: req.auth.sub, revokedAt: null }).sort({ createdAt: -1 }).lean();
  res.json({ devices: devices.map((device) => ({ id: device._id.toString(), name: device.deviceName, linkedAt: device.createdAt, lastSeenAt: device.lastSeenAt })) });
});
app.delete("/v1/linked-devices/:id", auth, async (req, res) => {
  const device = await LinkedDevice.findOneAndUpdate({ _id: req.params.id, user: req.auth.sub, revokedAt: null }, { revokedAt: new Date() });
  if (!device) return res.sendStatus(404);
  res.json({ ok: true });
});
app.get("/health/email", (_req, res) => {
  const smtp = smtpSettings();
  res.json({
    ok: Boolean(smtp.user && smtp.pass),
    configured: Boolean(smtp.user && smtp.pass),
    host: smtp.host,
    port: smtp.port,
    secure: smtp.secure,
    fromConfigured: Boolean(process.env.SMTP_FROM)
  });
});
app.get("/v1/app/version", (_req, res) => { res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate"); res.json({
  versionCode: 53,
  versionName: "2.5.3",
  apkUrl: "https://mowell-api.grapaxels.in/Mowell-v2.5.3.apk",
    sha256: "1FA77A2D89D3F214F4FC6377B5895BA8167FBC09E1E7113A2EDF25867652DC64",
  required: String(process.env.ANDROID_UPDATE_REQUIRED).toLowerCase() === "true"
}); });

app.get("/v1/calls/ice-servers", auth, (_req, res) => {
  res.setHeader("Cache-Control", "private, no-store");
  res.json({ iceServers: callIceServers });
});

app.post("/v1/auth/register", async (req, res) => {
  try {
    const email = String(req.body.email || "").trim().toLowerCase();
    const username = String(req.body.username || "").trim().toLowerCase();
    const displayName = String(req.body.displayName || username).trim();
    const password = String(req.body.password || "");
    if (!emailPattern.test(email)) return res.status(400).json({ error: "Enter a valid email" });
    try { await validateEmailDomain(email); } catch (error) { return res.status(400).json({ error: error.message }); }
    if (!usernamePattern.test(username)) return res.status(400).json({ error: "Username must be 3–24 lowercase letters, numbers, or underscores" });
    if (password.length < 8) return res.status(400).json({ error: "Password must have at least 8 characters" });
    if (await User.exists({ $or: [{ email }, { username }] })) return res.status(409).json({ error: "Email or username is already in use" });
    const user = await User.create({ email, username, displayName, passwordHash: await bcrypt.hash(password, 12), emailVerified: false });
    try { await sendVerification(user, true); }
    catch (error) {
      logMailError(error);
      return res.status(503).json({ verificationRequired: true, email, error: publicMailError(error) });
    }
    res.status(202).json({ verificationRequired: true, email, message: "Verification code sent" });
  } catch (error) { res.status(500).json({ error: "Could not create account" }); }
});

app.post("/v1/auth/login", async (req, res) => {
  const identity = String(req.body.identity || "").trim().toLowerCase();
  const deviceId = String(req.body.deviceId || "").trim();
  const user = await User.findOne({ $or: [{ email: identity }, { username: identity }] }).select("+passwordHash +verificationCodeHash +verificationExpiresAt +verificationLastSentAt +verificationAttempts +loginCodeHash +loginExpiresAt +loginLastSentAt +loginAttempts");
  if (!user?.passwordHash || !(await bcrypt.compare(String(req.body.password || ""), user.passwordHash))) {
    return res.status(401).json({ error: "Incorrect email, username, or password" });
  }
  if (!user.emailVerified) {
    try { await sendVerification(user); }
    catch (error) { logMailError(error); return res.status(503).json({ error: publicMailError(error) }); }
    return res.status(403).json({ error: "Verify your email to continue", verificationRequired: true, email: user.email });
  }
  const now = new Date();
  const trusted = deviceId && (user.trustedDevices || []).find((item) => item.deviceHash === deviceHash(deviceId) && now - new Date(item.verifiedAt) < trustedForMs);
  if (trusted) {
    trusted.lastUsedAt = now; user.lastSeenAt = now; await user.save();
    return res.json({ token: issueToken(user), user: publicUser(user), trustedDevice: true });
  }
  try { await sendLoginCode(user, true); }
  catch (error) { logMailError(error); return res.status(503).json({ error: publicMailError(error) }); }
  res.status(202).json({ verificationRequired: true, loginVerificationRequired: true, email: user.email, message: "Login code sent" });
});

app.post("/v1/auth/verify-email", async (req, res) => {
  const email = String(req.body.email || "").trim().toLowerCase();
  const code = String(req.body.code || "").trim();
  const deviceId = String(req.body.deviceId || "").trim();
  const user = await User.findOne({ email }).select("+verificationCodeHash +verificationExpiresAt +verificationAttempts +loginCodeHash +loginExpiresAt +loginAttempts");
  if (!user) return res.status(400).json({ error: "Verification request is invalid" });
  if (user.emailVerified && user.loginCodeHash) {
    if (!/^\d{6}$/.test(code) || !user.loginExpiresAt || user.loginExpiresAt < new Date()) return res.status(400).json({ error: "Login code is invalid or expired" });
    user.loginAttempts = (user.loginAttempts || 0) + 1;
    if (user.loginAttempts > 5) { user.loginCodeHash = undefined; await user.save(); return res.status(429).json({ error: "Too many attempts. Sign in again for a new code" }); }
    const suppliedLogin = Buffer.from(emailCodeHash(email, `login:${code}`), "hex");
    const expectedLogin = Buffer.from(user.loginCodeHash, "hex");
    if (suppliedLogin.length !== expectedLogin.length || !crypto.timingSafeEqual(suppliedLogin, expectedLogin)) { await user.save(); return res.status(400).json({ error: "Incorrect login code" }); }
    user.loginCodeHash = undefined; user.loginExpiresAt = undefined; user.loginAttempts = 0; user.lastSeenAt = new Date(); trustDevice(user, deviceId);
    await user.save();
    return res.json({ token: issueToken(user), user: publicUser(user) });
  }
  if (user.emailVerified) return res.status(400).json({ error: "Verification request is invalid" });
  if (!/^\d{6}$/.test(code) || !user.verificationCodeHash || !user.verificationExpiresAt || user.verificationExpiresAt < new Date()) return res.status(400).json({ error: "Code is invalid or expired" });
  user.verificationAttempts = (user.verificationAttempts || 0) + 1;
  if (user.verificationAttempts > 5) { user.verificationCodeHash = undefined; await user.save(); return res.status(429).json({ error: "Too many attempts. Request a new code" }); }
  const supplied = Buffer.from(emailCodeHash(email, code), "hex");
  const expected = Buffer.from(user.verificationCodeHash, "hex");
  if (supplied.length !== expected.length || !crypto.timingSafeEqual(supplied, expected)) { await user.save(); return res.status(400).json({ error: "Incorrect verification code" }); }
  user.emailVerified = true; user.verificationCodeHash = undefined; user.verificationExpiresAt = undefined; user.verificationAttempts = 0; trustDevice(user, deviceId);
  await user.save();
  res.json({ token: issueToken(user), user: publicUser(user) });
});

app.post("/v1/auth/resend-verification", async (req, res) => {
  const email = String(req.body.email || "").trim().toLowerCase();
  const user = await User.findOne({ email }).select("+verificationLastSentAt +verificationCodeHash +verificationExpiresAt +verificationAttempts +loginCodeHash +loginExpiresAt +loginLastSentAt +loginAttempts");
  if (user && !user.emailVerified) {
    try { await sendVerification(user); }
    catch (error) { logMailError(error); return res.status(503).json({ error: publicMailError(error) }); }
  } else if (user?.emailVerified && user.loginCodeHash) {
    try { await sendLoginCode(user); }
    catch (error) { logMailError(error); return res.status(503).json({ error: publicMailError(error) }); }
  }
  res.json({ ok: true, message: "If the account is pending, a verification code has been sent" });
});

app.post("/v1/auth/request-password-reset", async (req, res) => {
  const email = String(req.body.email || "").trim().toLowerCase();
  if (!emailPattern.test(email)) return res.status(400).json({ error: "Enter a valid email" });
  const user = await User.findOne({ email }).select("+passwordResetCodeHash +passwordResetExpiresAt +passwordResetAttempts");
  if (user) {
    try { await sendPasswordReset(user); }
    catch (error) { logMailError(error); return res.status(503).json({ error: publicMailError(error) }); }
  }
  res.json({ ok: true, message: "If this email is registered, a reset code has been sent" });
});

app.post("/v1/auth/reset-password", async (req, res) => {
  const email = String(req.body.email || "").trim().toLowerCase();
  const code = String(req.body.code || "").trim();
  const password = String(req.body.password || "");
  if (password.length < 8) return res.status(400).json({ error: "Password must have at least 8 characters" });
  const user = await User.findOne({ email }).select("+passwordHash +passwordResetCodeHash +passwordResetExpiresAt +passwordResetAttempts");
  if (!user || !/^\d{6}$/.test(code) || !user.passwordResetCodeHash || !user.passwordResetExpiresAt || user.passwordResetExpiresAt < new Date()) {
    return res.status(400).json({ error: "Reset code is invalid or expired" });
  }
  user.passwordResetAttempts = (user.passwordResetAttempts || 0) + 1;
  if (user.passwordResetAttempts > 5) {
    user.passwordResetCodeHash = undefined; await user.save();
    return res.status(429).json({ error: "Too many attempts. Request a new code" });
  }
  const supplied = Buffer.from(emailCodeHash(email, `reset:${code}`), "hex");
  const expected = Buffer.from(user.passwordResetCodeHash, "hex");
  if (supplied.length !== expected.length || !crypto.timingSafeEqual(supplied, expected)) {
    await user.save(); return res.status(400).json({ error: "Incorrect reset code" });
  }
  user.passwordHash = await bcrypt.hash(password, 12);
  user.passwordResetCodeHash = undefined; user.passwordResetExpiresAt = undefined; user.passwordResetAttempts = 0;
  await user.save();
  res.json({ ok: true, message: "Password updated. You can now sign in" });
});

app.post("/v1/auth/google", async (req, res) => {
  if (!process.env.GOOGLE_CLIENT_ID) return res.status(503).json({ error: "Google sign-in is not configured" });
  try {
    const ticket = await new OAuth2Client(process.env.GOOGLE_CLIENT_ID).verifyIdToken({ idToken: req.body.idToken, audience: process.env.GOOGLE_CLIENT_ID });
    const payload = ticket.getPayload();
    if (!payload.email_verified) return res.status(401).json({ error: "Google email is not verified" });
    await validateEmailDomain(payload.email.toLowerCase());
    let user = await User.findOne({ $or: [{ googleSub: payload.sub }, { email: payload.email.toLowerCase() }] }).select("+googleSub");
    if (!user) {
      let base = payload.email.split("@")[0].toLowerCase().replace(/[^a-z0-9_]/g, "").slice(0, 18) || "mowell";
      let username = base, suffix = 0;
      while (await User.exists({ username })) username = `${base}${++suffix}`;
      user = await User.create({ email: payload.email, username, displayName: payload.name || username, avatarUrl: payload.picture, googleSub: payload.sub, emailVerified: true });
    } else { user.googleSub = payload.sub; user.emailVerified = true; await user.save(); }
    res.json({ token: issueToken(user), user: publicUser(user) });
  } catch { res.status(401).json({ error: "Google identity could not be verified" }); }
});

app.get("/v1/me", auth, async (req, res) => {
  const user = await User.findById(req.auth.sub); if (!user) return res.sendStatus(404);
  res.json({ user: publicUser(user) });
});

app.patch("/v1/me", auth, async (req, res) => {
  const displayName = String(req.body.displayName || "").trim();
  if (displayName.length < 2 || displayName.length > 60) return res.status(400).json({ error: "Name must contain 2–60 characters" });
  const user = await User.findByIdAndUpdate(req.auth.sub, { displayName }, { new: true });
  if (!user) return res.sendStatus(404);
  res.json({ user: publicUser(user) });
});

app.post("/v1/me/avatar", auth, async (req, res) => {
  try {
    const mime = String(req.body.mimeType || "image/jpeg").toLowerCase();
    if (!["image/jpeg", "image/png", "image/webp"].includes(mime)) return res.status(400).json({ error: "Use a JPG, PNG or WebP image" });
    const data = Buffer.from(String(req.body.data || ""), "base64");
    if (!data.length || data.length > 1572864) return res.status(413).json({ error: "Profile photo must be 1.5 MB or smaller" });
    const avatarUrl = `/v1/users/${req.auth.sub}/avatar?v=${Date.now()}`;
    const user = await User.findByIdAndUpdate(req.auth.sub, { avatarData: data, avatarMime: mime, avatarUrl }, { new: true });
    if (!user) return res.sendStatus(404);
    res.json({ user: publicUser(user) });
  } catch { res.status(400).json({ error: "Could not update profile photo" }); }
});

app.get("/v1/users/:id/avatar", async (req, res) => {
  if (!mongoose.isValidObjectId(req.params.id)) return res.sendStatus(404);
  const user = await User.findById(req.params.id).select("+avatarData +avatarMime");
  if (!user?.avatarData) return res.sendStatus(404);
  res.set("Content-Type", user.avatarMime || "image/jpeg");
  res.set("Cache-Control", "public, max-age=300");
  res.set("Cross-Origin-Resource-Policy", "cross-origin");
  res.send(user.avatarData);
});

app.get("/v1/users/search", auth, async (req, res) => {
  const query = String(req.query.q || "").trim().toLowerCase().slice(0, 40);
  if (query.length < 2) return res.json({ users: [] });
  const users = await User.find({ _id: { $ne: req.auth.sub }, username: { $regex: `^${escapeRegex(query)}`, $options: "i" } }).limit(20);
  res.json({ users: users.map(publicUser) });
});

app.get("/v1/connections/requests", auth, async (req, res) => {
  const requests = await ConnectionRequest.find({ recipient: req.auth.sub, status: "pending" })
    .populate("requester", "username displayName avatarUrl")
    .sort({ createdAt: -1 }).limit(50).lean();
  res.json({ requests: requests.map((item) => ({
    id: item._id.toString(), createdAt: item.createdAt,
    user: item.requester ? publicUser(item.requester) : null
  })).filter((item) => item.user) });
});

app.post("/v1/connections/requests", auth, async (req, res) => {
  const recipientId = String(req.body.userId || "");
  if (!mongoose.isValidObjectId(recipientId) || recipientId === req.auth.sub) return res.status(400).json({ error: "Choose another Mowell user" });
  const recipient = await User.findById(recipientId);
  if (!recipient) return res.status(404).json({ error: "User not found" });
  const members = [req.auth.sub, recipientId];
  const conversation = await Conversation.findOne({ isGroup: false, members: { $all: members, $size: 2 } });
  if (conversation) return res.json({ connected: true, conversationId: conversation._id.toString() });
  const reverse = await ConnectionRequest.findOne({ requester: recipientId, recipient: req.auth.sub, status: "pending" });
  if (reverse) return res.status(409).json({ error: "This person already requested to connect. Accept the request from Chats." });
  const request = await ConnectionRequest.findOneAndUpdate(
    { requester: req.auth.sub, recipient: recipientId },
    { $set: { status: "pending", respondedAt: null, createdAt: new Date() } },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );
  res.status(202).json({ connected: false, requestId: request._id.toString(), message: "Connection request sent" });
});

app.post("/v1/connections/requests/:id/respond", auth, async (req, res) => {
  const action = String(req.body.action || "").toLowerCase();
  if (!['accept', 'decline'].includes(action)) return res.status(400).json({ error: "Choose accept or decline" });
  const request = await ConnectionRequest.findOne({ _id: req.params.id, recipient: req.auth.sub, status: "pending" });
  if (!request) return res.status(404).json({ error: "Connection request is no longer available" });
  request.status = action === 'accept' ? 'accepted' : 'declined'; request.respondedAt = new Date(); await request.save();
  if (action === 'decline') return res.json({ ok: true, accepted: false });
  const members = [request.requester, request.recipient];
  let conversation = await Conversation.findOne({ isGroup: false, members: { $all: members, $size: 2 } });
  if (!conversation) conversation = await Conversation.create({ isGroup: false, members, createdBy: request.requester });
  res.json({ ok: true, accepted: true, conversationId: conversation._id.toString() });
});

app.get("/v1/conversations", auth, async (req, res) => {
  const conversations = await Conversation.find({ members: req.auth.sub })
    .populate("members", "username displayName avatarUrl lastSeenAt")
    .sort({ lastMessageAt: -1 }).limit(100).lean();
  const latest = conversations.length ? await Message.aggregate([
    { $match: { conversation: { $in: conversations.map((item) => item._id) } } },
    { $sort: { sentAt: -1 } },
    { $group: { _id: "$conversation", message: { $first: "$$ROOT" } } }
  ]) : [];
  const latestByConversation = new Map(latest.map((item) => [item._id.toString(), item.message]));
  res.json({ conversations: conversations.map((item) => ({ ...item, lastMessage: latestByConversation.get(item._id.toString()) || null })) });
});

app.post("/v1/conversations", auth, async (req, res) => {
  const memberIds = [...new Set([req.auth.sub, ...(req.body.memberIds || []).map(String)])];
  if (memberIds.length < 2) return res.status(400).json({ error: "Select at least one other user" });
  const validCount = await User.countDocuments({ _id: { $in: memberIds } });
  if (validCount !== memberIds.length) return res.status(400).json({ error: "One or more users do not exist" });
  const isGroup = memberIds.length > 2 || Boolean(req.body.isGroup);
  if (!isGroup) {
    const existing = await Conversation.findOne({ isGroup: false, members: { $all: memberIds, $size: 2 } });
    if (existing) return res.json({ conversation: existing });
    const connected = await ConnectionRequest.exists({
      status: "accepted",
      $or: [
        { requester: memberIds[0], recipient: memberIds[1] },
        { requester: memberIds[1], recipient: memberIds[0] }
      ]
    });
    if (!connected) return res.status(403).json({ error: "Both people must accept the connection before chatting" });
  }
  const conversation = await Conversation.create({ title: req.body.title, isGroup, members: memberIds, createdBy: req.auth.sub });
  res.status(201).json({ conversation });
});

app.get("/v1/conversations/:id/messages", auth, async (req, res) => {
  const allowed = await Conversation.exists({ _id: req.params.id, members: req.auth.sub });
  if (!allowed) return res.sendStatus(404);
  if (String(req.query.markRead).toLowerCase() === "true") await Message.updateMany({ conversation: req.params.id, sender: { $ne: req.auth.sub }, readBy: { $ne: req.auth.sub } }, { $addToSet: { readBy: req.auth.sub } });
  const filter = { conversation: req.params.id };
  if (req.query.after) filter.sentAt = { $gt: new Date(String(req.query.after)) };
  else filter.sentAt = { $lt: req.query.before ? new Date(String(req.query.before)) : new Date() };
  const messages = await Message.find(filter).sort({ sentAt: req.query.after ? 1 : -1 }).limit(100)
    .populate("sender", "username displayName avatarUrl")
    .populate("attachment", "fileName mimeType size");
  res.json({ messages: req.query.after ? messages : messages.reverse() });
});

app.post("/v1/conversations/:id/messages", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, members: req.auth.sub });
  if (!conversation) return res.sendStatus(404);
  if (!conversation.isGroup) {
    const otherId = conversation.members.find((id) => id.toString() !== req.auth.sub);
    const blocked = await User.exists({ $or: [
      { _id: req.auth.sub, blockedUsers: otherId },
      { _id: otherId, blockedUsers: req.auth.sub }
    ] });
    if (blocked) return res.status(403).json({ error: "Messages are unavailable because this user is blocked" });
  }
  const body = String(req.body.body || "").trim();
  const clientId = String(req.body.clientId || "").trim();
  if (!body || !clientId) return res.status(400).json({ error: "clientId and body are required" });
  const message = await Message.findOneAndUpdate(
    { conversation: conversation._id, clientId },
    { $setOnInsert: { sender: req.auth.sub, body, kind: req.body.kind || "text", sentAt: new Date() } },
    { upsert: true, new: true }
  ).populate("sender", "username displayName avatarUrl");
  conversation.lastMessageAt = message.sentAt; await conversation.save();
  res.status(201).json({ message });
});

app.delete("/v1/conversations/:id/messages/:clientId", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, members: req.auth.sub }).select("_id");
  if (!conversation) return res.sendStatus(404);
  // Delete-for-me is intentionally local to the phone. The server only needs
  // to mutate shared history when the sender chooses Delete for everyone.
  if (String(req.query.everyone) !== "true") return res.json({ ok: true, scope: "me" });
  const message = await Message.findOne({ conversation: conversation._id, clientId: req.params.clientId });
  if (!message) return res.sendStatus(404);
  if (message.sender.toString() !== req.auth.sub) return res.status(403).json({ error: "Only the sender can delete this message for everyone" });
  if (Date.now() - message.sentAt.getTime() > 4 * 60 * 1000) return res.status(409).json({ error: "Delete for everyone is available for four minutes" });
  if (message.kind !== "text") return res.status(409).json({ error: "Only text messages can be deleted for everyone" });
  message.body = "This message was deleted";
  message.kind = "system";
  message.sentAt = new Date();
  await message.save();
  res.json({ ok: true, scope: "everyone" });
});

app.post("/v1/conversations/:id/attachments", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, members: req.auth.sub });
  if (!conversation) return res.sendStatus(404);
  const clientId = String(req.body.clientId || "").trim();
  const fileName = String(req.body.fileName || "attachment").trim().slice(0, 180);
  const mimeType = String(req.body.mimeType || "application/octet-stream").trim().slice(0, 120);
  const encoded = String(req.body.data || "");
  if (!clientId || !encoded) return res.status(400).json({ error: "clientId and attachment data are required" });
  const data = Buffer.from(encoded, "base64");
  if (!data.length || data.length > 2621440) return res.status(413).json({ error: "Attachment must be 2.5 MB or smaller" });
  const kind = mimeType.startsWith("image/") ? "image" : mimeType.startsWith("video/") ? "video" : mimeType.startsWith("audio/") ? "audio" : "file";
  try {
    const media = await Media.create({ conversation: conversation._id, uploader: req.auth.sub, fileName, mimeType, size: data.length, data });
    const message = await Message.findOneAndUpdate(
      { conversation: conversation._id, clientId },
      { $setOnInsert: { sender: req.auth.sub, body: fileName, kind, attachment: media._id, sentAt: new Date() } },
      { upsert: true, new: true }
    ).populate("sender", "username displayName avatarUrl").populate("attachment", "fileName mimeType size");
    conversation.lastMessageAt = message.sentAt; await conversation.save();
    res.status(201).json({ message });
  } catch (error) {
    res.status(500).json({ error: "Could not upload attachment" });
  }
});

app.get("/v1/attachments/:id", auth, async (req, res) => {
  const media = await Media.findById(req.params.id).select("+data");
  if (!media) return res.sendStatus(404);
  const allowed = await Conversation.exists({ _id: media.conversation, members: req.auth.sub });
  if (!allowed) return res.sendStatus(404);
  const safeName = media.fileName.replace(/[\r\n"\\]/g, "_");
  res.set("Content-Type", media.mimeType);
  res.set("Content-Length", String(media.size));
  res.set("Content-Disposition", `inline; filename="${safeName}"`);
  res.send(media.data);
});

// Short-lived typing state. It is never written to message history and MongoDB
// automatically removes stale rows when a phone disconnects unexpectedly.
app.post("/v1/conversations/:id/typing", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, members: req.auth.sub }).select("_id");
  if (!conversation) return res.sendStatus(404);
  if (req.body.active === false) {
    await TypingState.deleteOne({ conversation: conversation._id, user: req.auth.sub });
  } else {
    await TypingState.updateOne(
      { conversation: conversation._id, user: req.auth.sub },
      { $set: { expiresAt: new Date(Date.now() + 7000) } },
      { upsert: true }
    );
  }
  res.json({ ok: true });
});

app.get("/v1/conversations/:id/typing", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, members: req.auth.sub }).select("_id");
  if (!conversation) return res.sendStatus(404);
  const states = await TypingState.find({
    conversation: conversation._id,
    user: { $ne: req.auth.sub },
    expiresAt: { $gt: new Date() }
  }).populate("user", "displayName username").limit(8).lean();
  res.json({ users: states.map((state) => state.user?.displayName || state.user?.username).filter(Boolean) });
});

const validRoom = (room) => /^[A-Za-z0-9-]{8,100}$/.test(room);
const endCall = async (call, reason = "ended", senderId = null) => {
  if (call.status === "ended") return;
  call.status = "ended"; call.endedAt = new Date(); await call.save();
  await Message.findOneAndUpdate(
    { conversation: call.conversation, clientId: `call-end-${call.room}` },
    { $setOnInsert: { sender: senderId || call.createdBy, body: JSON.stringify({ room: call.room, reason }), kind: "call_end", sentAt: new Date() } },
    { upsert: true, new: true }
  );
};

// Create/join a short-lived call room. Only identities already invited may join.
app.post("/v1/calls/:room/join", auth, async (req, res) => {
  try {
    const room = String(req.params.room || "").trim();
    if (!validRoom(room)) return res.status(400).json({ error: "Invalid call room" });
    let call = await CallRoom.findOne({ room });
    if (!call) {
      if (!req.body.initiator) return res.status(404).json({ error: "Call is no longer available" });
      const conversation = await Conversation.findOne({ _id: req.body.conversationId, members: req.auth.sub });
      if (!conversation) return res.sendStatus(404);
      const busy = await CallRoom.exists({ status: "active", participants: { $in: conversation.members }, expiresAt: { $gt: new Date() } });
      if (busy) {
        await Message.findOneAndUpdate(
          { conversation: conversation._id, clientId: `call-end-${room}` },
          { $setOnInsert: { sender: req.auth.sub, body: JSON.stringify({ room, reason: "busy" }), kind: "call_end", sentAt: new Date() } },
          { upsert: true }
        );
        return res.status(409).json({ error: "User is in another call", code: "USER_BUSY" });
      }
      call = await CallRoom.create({ room, conversation: conversation._id, participants: conversation.members, createdBy: req.auth.sub, video: Boolean(req.body.video), group: Boolean(conversation.isGroup) });
    }
    if (!call.participants.some((id) => id.toString() === req.auth.sub)) return res.sendStatus(403);
    if (call.status === "ended") return res.status(410).json({ error: "Call has ended" });
    if (call.status === "ringing" && call.unansweredExpiresAt < new Date()) {
      await endCall(call, "no_answer");
      return res.status(410).json({ error: "User didn't respond", code: "NO_ANSWER" });
    }
    if (call.status === "ringing" && call.createdBy.toString() !== req.auth.sub) {
      call.status = "active"; call.answeredAt = new Date(); await call.save();
    }
    call.participantHeartbeats = { ...(call.participantHeartbeats || {}), [req.auth.sub]: Date.now() };
    call.markModified("participantHeartbeats");
    await call.save();
    res.json({ ok: true, video: call.video, group: Boolean(call.group || call.participants.length > 2), status: call.status });
  } catch { res.status(400).json({ error: "Could not join call" }); }
});

// Add a username to an active room and place the invitation in a private chat.
app.post("/v1/calls/:room/invite", auth, async (req, res) => {
  try {
    const room = String(req.params.room || "").trim();
    const username = String(req.body.username || "").trim().toLowerCase();
    const call = await CallRoom.findOne({ room, participants: req.auth.sub });
    if (!call) return res.sendStatus(404);
    const invited = await User.findOne({ username });
    if (!invited || invited._id.toString() === req.auth.sub) return res.status(404).json({ error: "Username not found" });
    if (call.participants.length >= 6 && !call.participants.some((id) => id.toString() === invited._id.toString())) return res.status(409).json({ error: "A direct mesh call supports up to 6 members" });
    if (!call.participants.some((id) => id.toString() === invited._id.toString())) {
      call.participants.push(invited._id); await call.save();
    }
    if (!call.group) { call.group = true; await call.save(); }
    const members = [req.auth.sub, invited._id];
    let conversation = await Conversation.findOne({ isGroup: false, members: { $all: members, $size: 2 } });
    if (!conversation) conversation = await Conversation.create({ isGroup: false, members, createdBy: req.auth.sub });
    const body = JSON.stringify({ room, video: call.video, group: true });
    const message = await Message.create({ clientId: new mongoose.Types.ObjectId().toString(), conversation: conversation._id, sender: req.auth.sub, body, kind: "call", sentAt: new Date() });
    conversation.lastMessageAt = message.sentAt; await conversation.save();
    res.json({ ok: true, displayName: invited.displayName, username: invited.username });
  } catch { res.status(400).json({ error: "Could not add this member" }); }
});

// Ephemeral WebRTC signaling. Audio and video never pass through this API.
app.post("/v1/calls/:room/signals", auth, async (req, res) => {
  try {
    const room = String(req.params.room || "").trim();
    const type = String(req.body.type || "");
    if (!validRoom(room)) return res.status(400).json({ error: "Invalid call room" });
    if (!["join", "offer", "answer", "ice", "leave", "media", "heartbeat"].includes(type)) return res.status(400).json({ error: "Invalid call signal" });
    const call = await CallRoom.findOne({ room, participants: req.auth.sub });
    if (!call) return res.sendStatus(404);
    if (type === "media" && req.body.payload?.video && !call.video) {
      call.video = true;
      await call.save();
    }
    if (type === "heartbeat") {
      call.participantHeartbeats = { ...(call.participantHeartbeats || {}), [req.auth.sub]: Date.now() };
      call.markModified("participantHeartbeats");
      await call.save();
      return res.status(204).end();
    }
    const target = req.body.target ? String(req.body.target) : null;
    if (target && !call.participants.some((id) => id.toString() === target)) return res.sendStatus(403);
    const signal = await CallSignal.create({ room, sender: req.auth.sub, target, type, payload: req.body.payload || {} });
    if (type === "leave") {
      if (!call.group && call.participants.length <= 2) {
        await endCall(call, req.body.payload?.reason || "ended", req.auth.sub);
      } else {
        // A member declining or leaving a group call must not terminate the
        // room for everyone else. Remove only that identity from this room.
        call.participants = call.participants.filter((id) => id.toString() !== req.auth.sub);
        await call.save();
      }
    }
    res.status(201).json({ id: signal._id.toString() });
  } catch { res.status(400).json({ error: "Could not send call signal" }); }
});

app.get("/v1/calls/:room/signals", auth, async (req, res) => {
  try {
    const room = String(req.params.room || "").trim();
    const call = await CallRoom.findOne({ room, participants: req.auth.sub });
    if (!call) return res.sendStatus(404);
    if (call.status === "ended") return res.status(410).json({ error: "Call has ended" });
    if (call.status === "ringing" && call.unansweredExpiresAt < new Date()) {
      await endCall(call, "no_answer");
      return res.status(410).json({ error: "User didn't respond", code: "NO_ANSWER" });
    }
    const filter = { room, sender: { $ne: req.auth.sub }, $or: [{ target: null }, { target: req.auth.sub }] };
    if (req.query.afterId) {
      if (!mongoose.isValidObjectId(req.query.afterId)) return res.status(400).json({ error: "Invalid call cursor" });
      filter._id = { $gt: new mongoose.Types.ObjectId(String(req.query.afterId)) };
    }
    const signals = await CallSignal.find(filter).sort({ _id: 1 }).limit(100).populate("sender", "displayName username avatarUrl").lean();
    res.json({ signals: signals.map((s) => ({ id: s._id.toString(), senderId: s.sender._id.toString(), senderName: s.sender.displayName || s.sender.username, senderAvatar: s.sender.avatarUrl || null, type: s.type, payload: s.payload })) });
  } catch { res.status(400).json({ error: "Could not receive call signals" }); }
});

app.post("/v1/conversations/:id/block", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, members: req.auth.sub });
  if (!conversation || conversation.isGroup) return res.status(400).json({ error: "Only direct contacts can be blocked" });
  const otherId = conversation.members.find((id) => id.toString() !== req.auth.sub);
  if (!otherId) return res.sendStatus(400);
  const blocked = req.body.blocked !== false;
  await User.updateOne({ _id: req.auth.sub }, blocked ? { $addToSet: { blockedUsers: otherId } } : { $pull: { blockedUsers: otherId } });
  res.json({ ok: true, blocked });
});

// API routes must always return API responses. Keeping the SPA after every
// /v1 route prevents Android's WebRTC client from receiving index.html when a
// request is missing or mistyped.
app.use("/v1", (_req, res) => res.status(404).json({ error: "Mowell API endpoint not found", code: "API_NOT_FOUND" }));
app.use(express.static(webRoot, {
  index: "index.html",
  maxAge: "5m",
  setHeaders: (res, path) => {
    if (path.endsWith("index.html")) res.setHeader("Cache-Control", "no-store");
  }
}));
app.use((req, res) => {
  if (req.method !== "GET" && req.method !== "HEAD") return res.status(404).json({ error: "Route not found" });
  res.setHeader("Cache-Control", "no-store");
  return res.sendFile(webIndex);
});

export default app;
