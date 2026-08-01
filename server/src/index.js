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
import { CallRoom, CallSignal, Conversation, Media, Message, User } from "./models/index.js";

const mongoUri = process.env.MONGODB_URI || process.env.MONGO_URI;
if (!mongoUri) throw new Error("MONGODB_URI (or MONGO_URI) is required");
if (!process.env.JWT_SECRET) throw new Error("JWT_SECRET is required");
if (process.env.JWT_SECRET.length < 32) throw new Error("JWT_SECRET must contain at least 32 characters");

if (mongoose.connection.readyState === 0) await mongoose.connect(mongoUri, { autoIndex: true });
const app = express();
app.use(helmet());
app.use(cors({ origin: process.env.ALLOWED_ORIGIN || "*" }));
app.use(express.json({ limit: "4mb" }));

const publicUser = (user) => ({
  id: user._id.toString(), username: user.username, email: user.email,
  displayName: user.displayName, avatarUrl: user.avatarUrl || null, lastSeenAt: user.lastSeenAt,
  emailVerified: Boolean(user.emailVerified)
});
// Mobile sessions intentionally persist until the user chooses Log out.
// Rotate JWT_SECRET to revoke all sessions after a security incident.
const issueToken = (user) => jwt.sign({ sub: user._id.toString(), username: user.username }, process.env.JWT_SECRET, { issuer: "mowell-api" });
const usernamePattern = /^[a-z0-9_]{3,24}$/;
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
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
    from: process.env.SMTP_FROM || `Mowell by Grapaxels <${smtp.user}>`, to: user.email,
    subject: `${code} is your Mowell verification code`,
    text: `Your Mowell verification code is ${code}. It expires in 10 minutes. If you did not request this, ignore this email.`,
    html: `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:28px"><h1 style="margin:0">Mowell</h1><p style="color:#7357f6">by Grapaxels</p><p>Use this code to verify your email:</p><div style="font-size:34px;font-weight:800;letter-spacing:8px;padding:18px;background:#ede8ff;border-radius:16px;text-align:center">${code}</div><p>This code expires in 10 minutes.</p></div>`
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
app.get("/v1/app/version", (_req, res) => res.json({
  versionCode: Number(process.env.ANDROID_VERSION_CODE || 1),
  versionName: process.env.ANDROID_VERSION_NAME || "0.1.0",
  apkUrl: process.env.ANDROID_APK_URL || null,
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
  res.json({ users: users.map(publicUser) });
});

app.get("/v1/conversations", auth, async (req, res) => {
  const conversations = await Conversation.find({ members: req.auth.sub }).populate("members", "username displayName avatarUrl lastSeenAt").sort({ lastMessageAt: -1 }).limit(100);
  res.json({ conversations });
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
  }
  const conversation = await Conversation.create({ title: req.body.title, isGroup, members: memberIds, createdBy: req.auth.sub });
  res.status(201).json({ conversation });
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

const validRoom = (room) => /^[A-Za-z0-9-]{8,100}$/.test(room);

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
      call = await CallRoom.create({ room, participants: conversation.members, createdBy: req.auth.sub, video: Boolean(req.body.video) });
    }
    if (!call.participants.some((id) => id.toString() === req.auth.sub)) return res.sendStatus(403);
    res.json({ ok: true, video: call.video, group: call.participants.length > 2 });
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
    if (!["join", "offer", "answer", "ice", "leave"].includes(type)) return res.status(400).json({ error: "Invalid call signal" });
    const call = await CallRoom.findOne({ room, participants: req.auth.sub });
    if (!call) return res.sendStatus(404);
    const target = req.body.target ? String(req.body.target) : null;
    if (target && !call.participants.some((id) => id.toString() === target)) return res.sendStatus(403);
    const signal = await CallSignal.create({ room, sender: req.auth.sub, target, type, payload: req.body.payload || {} });
    res.status(201).json({ id: signal._id.toString() });
  } catch { res.status(400).json({ error: "Could not send call signal" }); }
});

app.get("/v1/calls/:room/signals", auth, async (req, res) => {
  try {
    const room = String(req.params.room || "").trim();
    const call = await CallRoom.findOne({ room, participants: req.auth.sub });
    if (!call) return res.sendStatus(404);
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
