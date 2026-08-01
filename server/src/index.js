import "dotenv/config";
import bcrypt from "bcryptjs";
import cors from "cors";
import express from "express";
import { OAuth2Client } from "google-auth-library";
import helmet from "helmet";
import jwt from "jsonwebtoken";
import mongoose from "mongoose";
import { Conversation, Media, Message, User } from "./models/index.js";

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
  displayName: user.displayName, avatarUrl: user.avatarUrl || null, lastSeenAt: user.lastSeenAt
});
// Mobile sessions intentionally persist until the user chooses Log out.
// Rotate JWT_SECRET to revoke all sessions after a security incident.
const issueToken = (user) => jwt.sign({ sub: user._id.toString(), username: user.username }, process.env.JWT_SECRET, { issuer: "mowell-api" });
const usernamePattern = /^[a-z0-9_]{3,24}$/;
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const auth = async (req, res, next) => {
  try {
    const token = req.headers.authorization?.replace(/^Bearer\s+/i, "");
    if (!token) return res.status(401).json({ error: "Authentication required" });
    req.auth = jwt.verify(token, process.env.JWT_SECRET, { issuer: "mowell-api" });
    next();
  } catch { res.status(401).json({ error: "Invalid or expired session" }); }
};

app.get("/health", (_req, res) => res.json({ ok: true, service: "mowell-api" }));
app.get("/v1/app/version", (_req, res) => res.json({
  versionCode: Number(process.env.ANDROID_VERSION_CODE || 1),
  versionName: process.env.ANDROID_VERSION_NAME || "0.1.0",
  apkUrl: process.env.ANDROID_APK_URL || null,
  required: String(process.env.ANDROID_UPDATE_REQUIRED).toLowerCase() === "true"
}));

app.post("/v1/auth/register", async (req, res) => {
  try {
    const email = String(req.body.email || "").trim().toLowerCase();
    const username = String(req.body.username || "").trim().toLowerCase();
    const displayName = String(req.body.displayName || username).trim();
    const password = String(req.body.password || "");
    if (!emailPattern.test(email)) return res.status(400).json({ error: "Enter a valid email" });
    if (!usernamePattern.test(username)) return res.status(400).json({ error: "Username must be 3–24 lowercase letters, numbers, or underscores" });
    if (password.length < 8) return res.status(400).json({ error: "Password must have at least 8 characters" });
    if (await User.exists({ $or: [{ email }, { username }] })) return res.status(409).json({ error: "Email or username is already in use" });
    const user = await User.create({ email, username, displayName, passwordHash: await bcrypt.hash(password, 12) });
    res.status(201).json({ token: issueToken(user), user: publicUser(user) });
  } catch (error) { res.status(500).json({ error: "Could not create account" }); }
});

app.post("/v1/auth/login", async (req, res) => {
  const identity = String(req.body.identity || "").trim().toLowerCase();
  const user = await User.findOne({ $or: [{ email: identity }, { username: identity }] }).select("+passwordHash");
  if (!user?.passwordHash || !(await bcrypt.compare(String(req.body.password || ""), user.passwordHash))) {
    return res.status(401).json({ error: "Incorrect email, username, or password" });
  }
  user.lastSeenAt = new Date(); await user.save();
  res.json({ token: issueToken(user), user: publicUser(user) });
});

app.post("/v1/auth/google", async (req, res) => {
  if (!process.env.GOOGLE_CLIENT_ID) return res.status(503).json({ error: "Google sign-in is not configured" });
  try {
    const ticket = await new OAuth2Client(process.env.GOOGLE_CLIENT_ID).verifyIdToken({ idToken: req.body.idToken, audience: process.env.GOOGLE_CLIENT_ID });
    const payload = ticket.getPayload();
    let user = await User.findOne({ $or: [{ googleSub: payload.sub }, { email: payload.email.toLowerCase() }] }).select("+googleSub");
    if (!user) {
      let base = payload.email.split("@")[0].toLowerCase().replace(/[^a-z0-9_]/g, "").slice(0, 18) || "mowell";
      let username = base, suffix = 0;
      while (await User.exists({ username })) username = `${base}${++suffix}`;
      user = await User.create({ email: payload.email, username, displayName: payload.name || username, avatarUrl: payload.picture, googleSub: payload.sub });
    } else if (!user.googleSub) { user.googleSub = payload.sub; await user.save(); }
    res.json({ token: issueToken(user), user: publicUser(user) });
  } catch { res.status(401).json({ error: "Google identity could not be verified" }); }
});

app.get("/v1/me", auth, async (req, res) => {
  const user = await User.findById(req.auth.sub); if (!user) return res.sendStatus(404);
  res.json({ user: publicUser(user) });
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

export default app;
