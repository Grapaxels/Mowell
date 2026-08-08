import "dotenv/config";
import bcrypt from "bcryptjs";
import cors from "cors";
import express from "express";
import { OAuth2Client } from "google-auth-library";
import helmet from "helmet";
import jwt from "jsonwebtoken";
import mongoose from "mongoose";
import nodemailer from "nodemailer";
import crypto from "node:crypto";
import { resolveMx } from "node:dns/promises";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { CallRoom, CallSignal, Contact, Conversation, GroupInvitation, Media, Message, TypingState, User } from "./models/index.js";

const mongoUri = process.env.MONGODB_URI || process.env.MONGO_URI;
if (!mongoUri) throw new Error("MONGODB_URI (or MONGO_URI) is required");
if (!process.env.JWT_SECRET) throw new Error("JWT_SECRET is required");
if (process.env.JWT_SECRET.length < 32) throw new Error("JWT_SECRET must contain at least 32 characters");

if (mongoose.connection.readyState === 0) await mongoose.connect(mongoUri, { autoIndex: true });
await Contact.updateMany({ status: { $exists: false } }, { $set: { status: "accepted" } });
const app = express();
app.use(helmet());
app.use(cors({ origin: process.env.ALLOWED_ORIGIN || "*" }));
app.use(express.json({ limit: "4mb" }));

const publicUser = (user) => ({
  id: user._id.toString(), username: user.username, email: user.email,
  displayName: user.displayName, avatarUrl: user.avatarUrl || null, lastSeenAt: user.lastSeenAt,
  emailVerified: Boolean(user.emailVerified)
});
const directoryUser = (user) => ({
  id: user._id.toString(), username: user.username, displayName: user.displayName,
  avatarUrl: user.avatarUrl || null, lastSeenAt: user.lastSeenAt
});
// Mobile sessions intentionally persist until the user chooses Log out.
// Rotate JWT_SECRET to revoke all sessions after a security incident.
const issueToken = (user) => jwt.sign({ sub: user._id.toString(), username: user.username }, process.env.JWT_SECRET, { issuer: "mowell-api" });
const usernamePattern = /^[a-z0-9_]{3,24}$/;
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const contactPair = (first, second) => [String(first), String(second)].sort().join(":");
const isGroupAdmin = (conversation, userId) => conversation.createdBy.toString() === String(userId)
  || (conversation.admins || []).some((id) => id.toString() === String(userId));
const directContact = async (conversation) => {
  if (!conversation || conversation.isGroup || conversation.members.length !== 2) return null;
  return Contact.findOne({ pairKey: contactPair(conversation.members[0], conversation.members[1]) });
};
const directMessagingBlocked = async (conversation) => {
  if (!conversation || conversation.isGroup) return false;
  const contact = await directContact(conversation);
  return !contact || contact.status !== "accepted" || Boolean(contact.blockedBy?.length);
};
const disposableDomains = new Set([
  "10minutemail.com", "guerrillamail.com", "guerrillamailblock.com", "mailinator.com", "temp-mail.org",
  "tempmail.com", "throwawaymail.com", "yopmail.com", "sharklasers.com", "getnada.com", "dispostable.com",
  ...String(process.env.BLOCKED_EMAIL_DOMAINS || "").split(",").map((v) => v.trim().toLowerCase()).filter(Boolean)
]);
const emailCodeHash = (email, code) => crypto.createHmac("sha256", process.env.JWT_SECRET).update(`${email}:${code}`).digest("hex");
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
    from: process.env.SMTP_FROM || `Mowell from Grapaxels <${smtp.user}>`, to: user.email,
    subject: `${code} is your Mowell verification code`,
    text: `Your Mowell verification code is ${code}. It expires in 10 minutes. If you did not request this, ignore this email.`,
    html: `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:28px"><h1 style="margin:0">Mowell</h1><p style="color:#7357f6">from Grapaxels</p><p>Use this code to verify your email:</p><div style="font-size:34px;font-weight:800;letter-spacing:8px;padding:18px;background:#ede8ff;border-radius:16px;text-align:center">${code}</div><p>This code expires in 10 minutes.</p></div>`
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
    from: process.env.SMTP_FROM || `Mowell from Grapaxels <${smtp.user}>`,
    to: user.email,
    subject: `${code} is your Mowell password reset code`,
    text: `Your Mowell password reset code is ${code}. It expires in 10 minutes. If you did not request this, ignore this email.`,
    html: `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:28px"><h1 style="margin:0">Mowell</h1><p style="color:#7357f6">from Grapaxels</p><p>Use this code to reset your password:</p><div style="font-size:34px;font-weight:800;letter-spacing:8px;padding:18px;background:#ede8ff;border-radius:16px;text-align:center">${code}</div><p>This code expires in 10 minutes.</p></div>`
  });
  await user.save();
};

const auth = async (req, res, next) => {
  try {
    const token = req.headers.authorization?.replace(/^Bearer\s+/i, "");
    if (!token) return res.status(401).json({ error: "Authentication required" });
    req.auth = jwt.verify(token, process.env.JWT_SECRET, { issuer: "mowell-api" });
    const user = await User.findById(req.auth.sub).select("email emailVerified");
    if (!user) return res.status(401).json({ error: "Account not found" });
    if (!user.emailVerified) return res.status(403).json({ error: "Verify your email to continue", verificationRequired: true, email: user.email });
    next();
  } catch { res.status(401).json({ error: "Invalid or expired session" }); }
};

app.get("/health", (_req, res) => res.json({ ok: true, service: "mowell-api" }));
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
const packagedApkPath = fileURLToPath(new URL("../assets/Mowell.apk", import.meta.url));
app.get("/v1/app/apk", (_req, res) => {
  if (!existsSync(packagedApkPath)) return res.status(503).json({ error: "The update APK has not been published yet" });
  res.set({
    "Content-Type": "application/vnd.android.package-archive",
    "Content-Disposition": "attachment; filename=\"Mowell.apk\"",
    "Cache-Control": "public, max-age=300"
  });
  return res.sendFile(packagedApkPath);
});

app.get("/v1/app/version", (req, res) => res.json({
  versionCode: Number(process.env.ANDROID_VERSION_CODE || 1),
  versionName: process.env.ANDROID_VERSION_NAME || "0.1.0",
  apkUrl: process.env.ANDROID_APK_URL || `${process.env.PUBLIC_BASE_URL || `${req.protocol}://${req.get("host")}`}/v1/app/apk`,
  sha256: process.env.ANDROID_APK_SHA256 || null,
  required: String(process.env.ANDROID_UPDATE_REQUIRED).toLowerCase() === "true"
}));

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
  const user = await User.findOne({ $or: [{ email: identity }, { username: identity }] }).select("+passwordHash +verificationCodeHash +verificationExpiresAt +verificationLastSentAt +verificationAttempts");
  if (!user?.passwordHash || !(await bcrypt.compare(String(req.body.password || ""), user.passwordHash))) {
    return res.status(401).json({ error: "Incorrect email, username, or password" });
  }
  if (!user.emailVerified) {
    try { await sendVerification(user); }
    catch (error) { logMailError(error); return res.status(503).json({ error: publicMailError(error) }); }
    return res.status(403).json({ error: "Verify your email to continue", verificationRequired: true, email: user.email });
  }
  user.lastSeenAt = new Date(); await user.save();
  res.json({ token: issueToken(user), user: publicUser(user) });
});

app.post("/v1/auth/verify-email", async (req, res) => {
  const email = String(req.body.email || "").trim().toLowerCase();
  const code = String(req.body.code || "").trim();
  const user = await User.findOne({ email }).select("+verificationCodeHash +verificationExpiresAt +verificationAttempts");
  if (!user || user.emailVerified) return res.status(400).json({ error: "Verification request is invalid" });
  if (!/^\d{6}$/.test(code) || !user.verificationCodeHash || !user.verificationExpiresAt || user.verificationExpiresAt < new Date()) return res.status(400).json({ error: "Code is invalid or expired" });
  user.verificationAttempts = (user.verificationAttempts || 0) + 1;
  if (user.verificationAttempts > 5) { user.verificationCodeHash = undefined; await user.save(); return res.status(429).json({ error: "Too many attempts. Request a new code" }); }
  const supplied = Buffer.from(emailCodeHash(email, code), "hex");
  const expected = Buffer.from(user.verificationCodeHash, "hex");
  if (supplied.length !== expected.length || !crypto.timingSafeEqual(supplied, expected)) { await user.save(); return res.status(400).json({ error: "Incorrect verification code" }); }
  user.emailVerified = true; user.verificationCodeHash = undefined; user.verificationExpiresAt = undefined; user.verificationAttempts = 0;
  await user.save();
  res.json({ token: issueToken(user), user: publicUser(user) });
});

app.post("/v1/auth/resend-verification", async (req, res) => {
  const email = String(req.body.email || "").trim().toLowerCase();
  const user = await User.findOne({ email }).select("+verificationLastSentAt +verificationCodeHash +verificationExpiresAt +verificationAttempts");
  if (user && !user.emailVerified) {
    try { await sendVerification(user); }
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
  res.send(user.avatarData);
});

app.get("/v1/users/search", auth, async (req, res) => {
  const query = String(req.query.q || "").trim().toLowerCase().slice(0, 40);
  if (query.length < 2) return res.json({ users: [] });
  const users = await User.find({ _id: { $ne: req.auth.sub }, username: { $regex: `^${escapeRegex(query)}`, $options: "i" } }).limit(20);
  res.json({ users: users.map(directoryUser) });
});

app.get("/v1/contacts", auth, async (req, res) => {
  const contacts = await Contact.find({ users: req.auth.sub, status: "accepted" }).populate("users", "username email displayName avatarUrl lastSeenAt");
  const users = contacts.flatMap((contact) => contact.users.filter((user) => user._id.toString() !== req.auth.sub).map(directoryUser));
  res.json({ users });
});

app.get("/v1/contacts/requests", auth, async (req, res) => {
  const contacts = await Contact.find({ users: req.auth.sub, status: "pending" })
    .populate("users", "username email displayName avatarUrl lastSeenAt").sort({ createdAt: -1 });
  const requests = contacts.map((contact) => ({
    _id: contact._id.toString(),
    direction: contact.addedBy.toString() === req.auth.sub ? "outgoing" : "incoming",
    user: directoryUser(contact.users.find((user) => user._id.toString() !== req.auth.sub))
  }));
  res.json({ requests });
});

app.post("/v1/contacts/requests", auth, async (req, res) => {
  const requestedId = String(req.body.userId || "");
  if (!mongoose.isValidObjectId(requestedId) || requestedId === req.auth.sub) return res.status(400).json({ error: "Choose another Mowell user" });
  const requested = await User.findById(requestedId);
  if (!requested) return res.status(404).json({ error: "User not found" });
  const pairKey = contactPair(req.auth.sub, requestedId);
  const existing = await Contact.findOne({ pairKey });
  if (existing?.status === "accepted") return res.status(409).json({ error: "You are already connected" });
  if (existing?.status === "pending") {
    const incoming = existing.addedBy.toString() !== req.auth.sub;
    return res.status(409).json({ error: incoming ? "This person already sent you a request. Accept it from People" : "Connection request already sent" });
  }
  // A declined request must get a new ID so Android can notify the recipient
  // again if the person sends a fresh request later.
  if (existing?.status === "declined") await existing.deleteOne();
  const request = await Contact.findOneAndUpdate(
    { pairKey },
    { $set: { users: [req.auth.sub, requestedId], addedBy: req.auth.sub, status: "pending", blockedBy: [] } },
    { upsert: true, new: true }
  );
  res.status(201).json({ request: { _id: request._id.toString(), direction: "outgoing", user: directoryUser(requested) } });
});

app.post("/v1/contacts/requests/:id/accept", auth, async (req, res) => {
  const request = await Contact.findOne({ _id: req.params.id, users: req.auth.sub, status: "pending" });
  if (!request) return res.sendStatus(404);
  if (request.addedBy.toString() === req.auth.sub) return res.status(403).json({ error: "Only the recipient can accept this request" });
  request.status = "accepted"; await request.save();
  let conversation = await Conversation.findOne({ isGroup: false, members: { $all: request.users, $size: 2 } });
  if (!conversation) conversation = await Conversation.create({ isGroup: false, members: request.users, createdBy: request.addedBy });
  res.json({ ok: true, conversation });
});

app.delete("/v1/contacts/requests/:id", auth, async (req, res) => {
  const request = await Contact.findOne({ _id: req.params.id, users: req.auth.sub, status: "pending" });
  if (!request) return res.sendStatus(404);
  if (request.addedBy.toString() === req.auth.sub) await request.deleteOne();
  else { request.status = "declined"; await request.save(); }
  res.json({ ok: true });
});

app.get("/v1/groups/invitations", auth, async (req, res) => {
  const invitations = await GroupInvitation.find({ invitee: req.auth.sub, status: "pending" })
    .populate("conversation", "title").populate("inviter", "username displayName avatarUrl").sort({ createdAt: -1 });
  res.json({ invitations: invitations.map((invite) => ({
    _id: invite._id.toString(),
    groupId: invite.conversation._id.toString(),
    groupTitle: invite.conversation.title || "Mowell group",
    inviter: directoryUser(invite.inviter)
  })) });
});

app.post("/v1/groups/invitations/:id/accept", auth, async (req, res) => {
  const invitation = await GroupInvitation.findOne({ _id: req.params.id, invitee: req.auth.sub, status: "pending" });
  if (!invitation) return res.sendStatus(404);
  const conversation = await Conversation.findOneAndUpdate(
    { _id: invitation.conversation, isGroup: true }, { $addToSet: { members: req.auth.sub } }, { new: true }
  );
  if (!conversation) return res.sendStatus(404);
  invitation.status = "accepted"; await invitation.save();
  res.json({ ok: true, conversation });
});

app.delete("/v1/groups/invitations/:id", auth, async (req, res) => {
  const invitation = await GroupInvitation.findOne({ _id: req.params.id, invitee: req.auth.sub, status: "pending" });
  if (!invitation) return res.sendStatus(404);
  invitation.status = "declined"; await invitation.save();
  res.json({ ok: true });
});

app.get("/v1/conversations", auth, async (req, res) => {
  const contacts = await Contact.find({ users: req.auth.sub, status: "accepted" }).lean();
  const contactByPair = new Map(contacts.map((contact) => [contact.pairKey, contact]));
  const conversations = await Conversation.find({ members: req.auth.sub }).populate("members", "username displayName avatarUrl lastSeenAt").sort({ lastMessageAt: -1 }).limit(100);
  const visible = conversations.flatMap((conversation) => {
    if (conversation.isGroup) return [conversation.toObject()];
    const ids = conversation.members.map((member) => String(member?._id || member));
    if (ids.length !== 2) return [];
    const contact = contactByPair.get(contactPair(ids[0], ids[1]));
    if (!contact) return [];
    const blockedBy = (contact.blockedBy || []).map(String);
    return [{ ...conversation.toObject(), blocked: blockedBy.length > 0, blockedByMe: blockedBy.includes(req.auth.sub) }];
  });
  res.json({ conversations: visible });
});

app.post("/v1/conversations", auth, async (req, res) => {
  const requestedMemberIds = [...new Set((req.body.memberIds || []).map(String).filter((id) => id !== req.auth.sub))];
  const inviteIds = [...new Set((req.body.inviteIds || []).map(String).filter((id) => id !== req.auth.sub))];
  const memberIds = [req.auth.sub, ...requestedMemberIds];
  if (memberIds.length < 2 && inviteIds.length === 0) return res.status(400).json({ error: "Select or invite at least one other user" });
  const validCount = await User.countDocuments({ _id: { $in: memberIds } });
  if (validCount !== memberIds.length) return res.status(400).json({ error: "One or more users do not exist" });
  const isGroup = memberIds.length > 2 || Boolean(req.body.isGroup);
  if (!isGroup) {
    const pairKey = contactPair(memberIds[0], memberIds[1]);
    const contact = await Contact.findOne({ pairKey, status: "accepted" });
    if (!contact) return res.status(403).json({ error: "Both people must accept the connection before chatting" });
    const existing = await Conversation.findOne({ isGroup: false, members: { $all: memberIds, $size: 2 } });
    if (existing) return res.json({ conversation: existing });
  } else {
    const accepted = await Contact.countDocuments({ users: req.auth.sub, status: "accepted", pairKey: { $in: requestedMemberIds.map((id) => contactPair(req.auth.sub, id)) } });
    if (accepted !== requestedMemberIds.length) return res.status(403).json({ error: "Only accepted contacts can be added directly to a group" });
  }
  const conversation = await Conversation.create({ title: req.body.title, isGroup, members: memberIds, createdBy: req.auth.sub, admins: isGroup ? [req.auth.sub] : [] });
  if (isGroup && inviteIds.length) {
    const validInvites = await User.find({ _id: { $in: inviteIds } }).select("_id");
    await Promise.all(validInvites.map((user) => GroupInvitation.findOneAndUpdate(
      { conversation: conversation._id, invitee: user._id },
      { $set: { inviter: req.auth.sub, status: "pending" } },
      { upsert: true, new: true }
    )));
  }
  res.status(201).json({ conversation });
});

// Group identity is shared by every member. Admins may update it, while direct
// contact labels remain deliberately local to each member's device.
app.patch("/v1/conversations/:id", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, isGroup: true, members: req.auth.sub });
  if (!conversation || !isGroupAdmin(conversation, req.auth.sub)) return res.status(403).json({ error: "Only group admins can edit the group" });
  const title = String(req.body.title || "").trim();
  if (title.length < 2 || title.length > 80) return res.status(400).json({ error: "Group name must be 2 to 80 characters" });
  conversation.title = title;
  await conversation.save();
  res.json({ conversation: conversation.toObject() });
});

app.post("/v1/conversations/:id/avatar", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, isGroup: true, members: req.auth.sub });
  if (!conversation || !isGroupAdmin(conversation, req.auth.sub)) return res.status(403).json({ error: "Only group admins can change the group icon" });
  const mimeType = String(req.body.mimeType || "image/jpeg").toLowerCase();
  if (!/^image\/(jpeg|png|webp)$/.test(mimeType)) return res.status(400).json({ error: "Use a JPEG, PNG, or WebP image" });
  let data;
  try { data = Buffer.from(String(req.body.data || ""), "base64"); } catch (_) { return res.status(400).json({ error: "Invalid group icon data" }); }
  if (!data.length || data.length > 1_572_864) return res.status(400).json({ error: "Group icon must be 1.5 MB or smaller" });
  conversation.avatarData = data;
  conversation.avatarMime = mimeType;
  conversation.avatarUrl = `/v1/conversations/${conversation._id}/avatar?v=${Date.now()}`;
  await conversation.save();
  res.json({ avatarUrl: conversation.avatarUrl });
});

app.get("/v1/conversations/:id/avatar", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, isGroup: true, members: req.auth.sub }).select("+avatarData +avatarMime avatarUrl");
  if (!conversation || !conversation.avatarData?.length) return res.sendStatus(404);
  res.type(conversation.avatarMime || "image/jpeg").set("Cache-Control", "private, max-age=86400").send(conversation.avatarData);
});

app.post("/v1/conversations/:id/members", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, isGroup: true, members: req.auth.sub });
  if (!conversation || !isGroupAdmin(conversation, req.auth.sub)) return res.status(403).json({ error: "Only group admins can add people" });
  const memberIds = [...new Set((req.body.memberIds || []).map(String).filter((id) => id !== req.auth.sub))];
  const inviteIds = [...new Set((req.body.inviteIds || []).map(String).filter((id) => id !== req.auth.sub))];
  if (!memberIds.length && !inviteIds.length) return res.status(400).json({ error: "Select or invite at least one person" });
  const accepted = await Contact.countDocuments({ users: req.auth.sub, status: "accepted", pairKey: { $in: memberIds.map((id) => contactPair(req.auth.sub, id)) } });
  if (accepted !== memberIds.length) return res.status(403).json({ error: "Only accepted contacts can be added directly" });
  if (memberIds.length) await Conversation.updateOne({ _id: conversation._id }, { $addToSet: { members: { $each: memberIds } } });
  if (inviteIds.length) {
    const validInvites = await User.find({ _id: { $in: inviteIds } }).select("_id");
    await Promise.all(validInvites.map((user) => GroupInvitation.findOneAndUpdate(
      { conversation: conversation._id, invitee: user._id },
      { $set: { inviter: req.auth.sub, status: "pending" } },
      { upsert: true, new: true }
    )));
  }
  res.json({ ok: true });
});

app.get("/v1/conversations/:id/members", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, isGroup: true, members: req.auth.sub })
    .populate("members", "username displayName avatarUrl lastSeenAt");
  if (!conversation) return res.sendStatus(404);
  const creatorId = conversation.createdBy.toString();
  const adminIds = new Set((conversation.admins || []).map(String));
  adminIds.add(creatorId);
  res.json({
    creatorId,
    viewerIsAdmin: isGroupAdmin(conversation, req.auth.sub),
    members: conversation.members.map((user) => ({ ...directoryUser(user), isCreator: user._id.toString() === creatorId, isAdmin: adminIds.has(user._id.toString()) }))
  });
});

app.post("/v1/conversations/:id/admins/:userId", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, isGroup: true, members: req.auth.sub });
  if (!conversation || !isGroupAdmin(conversation, req.auth.sub)) return res.status(403).json({ error: "Only group admins can promote members" });
  if (!conversation.members.some((id) => id.toString() === req.params.userId)) return res.status(404).json({ error: "This user is not in the group" });
  await Conversation.updateOne({ _id: conversation._id }, { $addToSet: { admins: req.params.userId } });
  res.json({ ok: true });
});

app.delete("/v1/conversations/:id/admins/:userId", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, isGroup: true, members: req.auth.sub });
  if (!conversation || conversation.createdBy.toString() !== req.auth.sub) return res.status(403).json({ error: "Only the group creator can demote an admin" });
  if (conversation.createdBy.toString() === req.params.userId) return res.status(409).json({ error: "The group creator is always super-admin" });
  await Conversation.updateOne({ _id: conversation._id }, { $pull: { admins: req.params.userId } });
  res.json({ ok: true });
});

app.delete("/v1/conversations/:id/members/:userId", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, isGroup: true, members: req.auth.sub });
  if (!conversation || !isGroupAdmin(conversation, req.auth.sub)) return res.status(403).json({ error: "Only group admins can remove members" });
  if (conversation.createdBy.toString() === req.params.userId) return res.status(409).json({ error: "The group creator cannot be removed" });
  const targetIsAdmin = (conversation.admins || []).some((id) => id.toString() === req.params.userId);
  if (targetIsAdmin && conversation.createdBy.toString() !== req.auth.sub) return res.status(403).json({ error: "Only the group creator can remove another admin" });
  await Conversation.updateOne({ _id: conversation._id }, { $pull: { members: req.params.userId, admins: req.params.userId } });
  res.json({ ok: true });
});

// Leaving is intentionally separate from hiding a conversation. Leaving removes
// membership on the server; hiding is a local-only Android SQLite preference.
app.post("/v1/conversations/:id/leave", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, isGroup: true, members: req.auth.sub });
  if (!conversation) return res.sendStatus(404);
  if (conversation.createdBy.toString() === req.auth.sub) {
    return res.status(409).json({ error: "The group creator manages this group. Delete the group instead." });
  }
  await Promise.all([
    Conversation.updateOne({ _id: conversation._id }, { $pull: { members: req.auth.sub, admins: req.auth.sub } }),
    TypingState.deleteMany({ conversation: conversation._id, user: req.auth.sub })
  ]);
  res.json({ ok: true });
});

// A group can be permanently deleted only by the account that created it.
// Associated messages, uploads, invitations, typing state, and call signalling
// are removed so nobody can open the deleted group through an old local ID.
app.delete("/v1/conversations/:id", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, isGroup: true, createdBy: req.auth.sub });
  if (!conversation) return res.status(403).json({ error: "Only the group creator can delete this group" });
  const rooms = await CallRoom.find({ conversation: conversation._id }).select("room").lean();
  const roomIds = rooms.map((room) => room.room);
  await Promise.all([
    Message.deleteMany({ conversation: conversation._id }),
    Media.deleteMany({ conversation: conversation._id }),
    TypingState.deleteMany({ conversation: conversation._id }),
    GroupInvitation.deleteMany({ conversation: conversation._id }),
    CallRoom.deleteMany({ conversation: conversation._id }),
    roomIds.length ? CallSignal.deleteMany({ room: { $in: roomIds } }) : Promise.resolve(),
    conversation.deleteOne()
  ]);
  res.json({ ok: true });
});

app.post("/v1/conversations/:id/block", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, members: req.auth.sub, isGroup: false });
  if (!conversation || conversation.members.length !== 2) return res.sendStatus(404);
  const pairKey = contactPair(conversation.members[0], conversation.members[1]);
  const contact = await Contact.findOneAndUpdate(
    { pairKey },
    {
      $setOnInsert: { pairKey, users: conversation.members, addedBy: req.auth.sub, status: "accepted" },
      $addToSet: { blockedBy: req.auth.sub }
    },
    { upsert: true, new: true }
  );
  await TypingState.deleteMany({ conversation: conversation._id });
  res.json({ ok: true, blocked: true, blockedByMe: true, blockedBy: contact.blockedBy.length });
});

app.delete("/v1/conversations/:id/block", auth, async (req, res) => {
  const conversation = await Conversation.findOne({ _id: req.params.id, members: req.auth.sub, isGroup: false });
  if (!conversation || conversation.members.length !== 2) return res.sendStatus(404);
  const pairKey = contactPair(conversation.members[0], conversation.members[1]);
  const contact = await Contact.findOneAndUpdate({ pairKey }, { $pull: { blockedBy: req.auth.sub } }, { new: true });
  const blockedBy = contact?.blockedBy || [];
  res.json({ ok: true, blocked: blockedBy.length > 0, blockedByMe: blockedBy.some((id) => String(id) === req.auth.sub) });
});

app.get("/v1/conversations/:id/messages", auth, async (req, res) => {
  const allowed = await Conversation.exists({ _id: req.params.id, members: req.auth.sub });
  if (!allowed) return res.sendStatus(404);
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
  if (await directMessagingBlocked(conversation)) return res.status(403).json({ error: "Messaging is unavailable because this contact is blocked" });
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
  if (await directMessagingBlocked(conversation)) return res.status(403).json({ error: "Messaging is unavailable because this contact is blocked" });
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
  const conversation = await Conversation.findOne({ _id: req.params.id, members: req.auth.sub });
  if (!conversation) return res.sendStatus(404);
  if (await directMessagingBlocked(conversation)) return res.status(403).json({ error: "This contact is blocked" });
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
  const conversation = await Conversation.findOne({ _id: req.params.id, members: req.auth.sub });
  if (!conversation) return res.sendStatus(404);
  if (await directMessagingBlocked(conversation)) return res.json({ users: [] });
  const states = await TypingState.find({
    conversation: conversation._id,
    user: { $ne: req.auth.sub },
    expiresAt: { $gt: new Date() }
  }).populate("user", "displayName username").limit(8).lean();
  res.json({ users: states.map((state) => state.user?.displayName || state.user?.username).filter(Boolean) });
});

const validRoom = (room) => /^[A-Za-z0-9-]{8,100}$/.test(room);
const callIceServers = () => [{
  // Free STUN discovery keeps media peer-to-peer and needs no credentials or
  // deployment variables. Networks that block direct WebRTC still cannot be
  // traversed without a relay, so the Android client reports that case clearly.
  urls: [
    "stun:stun.l.google.com:19302",
    "stun:stun1.l.google.com:19302",
    "stun:stun.cloudflare.com:3478"
  ]
}];
const endCall = async (call, reason = "ended", senderId = null) => {
  if (call.status === "ended") return;
  const endedAt = new Date();
  const durationSeconds = call.answeredAt ? Math.max(0, Math.floor((endedAt.getTime() - call.answeredAt.getTime()) / 1000)) : 0;
  call.status = "ended"; call.endedAt = endedAt; await call.save();
  await Message.findOneAndUpdate(
    { conversation: call.conversation, clientId: `call-end-${call.room}` },
    { $setOnInsert: { sender: senderId || call.createdBy, body: JSON.stringify({ room: call.room, reason, durationSeconds }), kind: "call_end", sentAt: endedAt } },
    { upsert: true, new: true }
  );
};

// Supply credential-free peer-to-peer discovery configuration to authenticated
// clients. No calling-service account or additional deployment variable is used.
app.get("/v1/calls/ice", auth, (req, res) => {
  res.json({ iceServers: callIceServers(), peerToPeerOnly: true });
});

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
      if (await directMessagingBlocked(conversation)) return res.status(403).json({ error: "Calling is unavailable because this contact is blocked" });
      const busy = await CallRoom.exists({ status: "active", participants: { $in: conversation.members }, expiresAt: { $gt: new Date() } });
      if (busy) {
        await Message.findOneAndUpdate(
          { conversation: conversation._id, clientId: `call-end-${room}` },
          { $setOnInsert: { sender: req.auth.sub, body: JSON.stringify({ room, reason: "busy" }), kind: "call_end", sentAt: new Date() } },
          { upsert: true }
        );
        return res.status(409).json({ error: "User is in another call", code: "USER_BUSY" });
      }
      call = await CallRoom.create({ room, conversation: conversation._id, participants: conversation.members, activeParticipants: [req.auth.sub], createdBy: req.auth.sub, video: Boolean(req.body.video) });
    }
    const callConversation = await Conversation.findById(call.conversation);
    if (await directMessagingBlocked(callConversation)) return res.status(403).json({ error: "Calling is unavailable because this contact is blocked" });
    if (!call.participants.some((id) => id.toString() === req.auth.sub)) return res.sendStatus(403);
    if (call.status === "ended") return res.status(410).json({ error: "Call has ended" });
    if (call.status === "ringing" && call.unansweredExpiresAt < new Date()) {
      await endCall(call, "no_answer");
      return res.status(410).json({ error: "User didn't respond", code: "NO_ANSWER" });
    }
    const justJoined = !(call.activeParticipants || []).some((id) => id.toString() === req.auth.sub);
    if (justJoined) call.activeParticipants.push(req.auth.sub);
    if (call.status === "ringing" && call.createdBy.toString() !== req.auth.sub) {
      call.status = "active"; call.answeredAt = new Date();
    }
    if (justJoined || call.isModified()) await call.save();
    res.json({ ok: true, video: call.video, group: call.participants.length > 2, status: call.status });
  } catch { res.status(400).json({ error: "Could not join call" }); }
});

// The caller creates the room before the call invitation is delivered. This
// prevents a recipient from opening the call before camera/WebView startup has
// finished on the caller's phone.
app.post("/v1/calls/:room/ring", auth, async (req, res) => {
  try {
    const room = String(req.params.room || "").trim();
    if (!validRoom(room)) return res.status(400).json({ error: "Invalid call room" });
    const existing = await CallRoom.findOne({ room });
    if (existing) return res.json({ ok: true, status: existing.status });
    const conversation = await Conversation.findOne({ _id: req.body.conversationId, members: req.auth.sub });
    if (!conversation) return res.sendStatus(404);
    if (await directMessagingBlocked(conversation)) return res.status(403).json({ error: "Calling is unavailable because this contact is blocked" });
    const busy = await CallRoom.exists({ status: "active", participants: { $in: conversation.members }, expiresAt: { $gt: new Date() } });
    if (busy) return res.status(409).json({ error: "User is in another call", code: "USER_BUSY" });
    await CallRoom.create({
      room, conversation: conversation._id, participants: conversation.members,
      activeParticipants: [], createdBy: req.auth.sub, video: Boolean(req.body.video)
    });
    res.status(201).json({ ok: true, group: conversation.members.length > 2 });
  } catch { res.status(400).json({ error: "Could not start call" }); }
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
    const pairKey = contactPair(req.auth.sub, invited._id);
    const contact = await Contact.findOne({ pairKey });
    if (!contact || contact.status !== "accepted") return res.status(403).json({ error: "Connect with this person before adding them to a call" });
    if (contact.blockedBy?.length) return res.status(403).json({ error: "This contact is blocked" });
    if (call.participants.length >= 6 && !call.participants.some((id) => id.toString() === invited._id.toString())) return res.status(409).json({ error: "A direct mesh call supports up to 6 members" });
    if (!call.participants.some((id) => id.toString() === invited._id.toString())) {
      call.participants.push(invited._id); await call.save();
    }
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
    if (!["join", "offer", "answer", "ice", "leave", "media"].includes(type)) return res.status(400).json({ error: "Invalid call signal" });
    const call = await CallRoom.findOne({ room, participants: req.auth.sub });
    if (!call) return res.sendStatus(404);
    if (type === "media" && req.body.payload?.video && !call.video) {
      call.video = true;
      await call.save();
    }
    const target = req.body.target ? String(req.body.target) : null;
    if (target && !call.participants.some((id) => id.toString() === target)) return res.sendStatus(403);
    const signal = await CallSignal.create({ room, sender: req.auth.sub, target, type, payload: req.body.payload || {} });
    if (type === "leave") {
      call.activeParticipants = (call.activeParticipants || []).filter((id) => id.toString() !== req.auth.sub);
      // A 3+ person call continues for the remaining 3+ connected members.
      // As soon as only two (or fewer) active users remain, it becomes a
      // one-to-one call and closes for everyone, as requested.
      if (call.activeParticipants.length <= 2 || req.body.payload?.reason === "no_answer") {
        await endCall(call, req.body.payload?.reason || "ended", req.auth.sub);
      } else {
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

export default app;
