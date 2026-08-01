import mongoose from "mongoose";

const userSchema = new mongoose.Schema({
  email: { type: String, unique: true, required: true, lowercase: true, trim: true, index: true },
  username: { type: String, unique: true, required: true, lowercase: true, trim: true, index: true },
  displayName: { type: String, required: true, trim: true, maxlength: 60 },
  passwordHash: { type: String, select: false },
  googleSub: { type: String, unique: true, sparse: true, select: false },
  avatarUrl: String,
  lastSeenAt: { type: Date, default: Date.now }
}, { timestamps: true });

const conversationSchema = new mongoose.Schema({
  title: { type: String, trim: true, maxlength: 80 },
  isGroup: { type: Boolean, default: false },
  members: [{ type: mongoose.Schema.Types.ObjectId, ref: "User", required: true }],
  createdBy: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  lastMessageAt: { type: Date, default: Date.now }
}, { timestamps: true });
conversationSchema.index({ members: 1, lastMessageAt: -1 });

const messageSchema = new mongoose.Schema({
  clientId: { type: String, required: true },
  conversation: { type: mongoose.Schema.Types.ObjectId, ref: "Conversation", required: true, index: true },
  sender: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  body: { type: String, required: true, maxlength: 8000 },
  kind: { type: String, enum: ["text", "image", "audio", "video", "file", "location", "contact", "call", "system"], default: "text" },
  attachment: { type: mongoose.Schema.Types.ObjectId, ref: "Media" },
  sentAt: { type: Date, default: Date.now }
}, { timestamps: true });
messageSchema.index({ conversation: 1, clientId: 1 }, { unique: true });
messageSchema.index({ conversation: 1, sentAt: -1 });

export const User = mongoose.model("User", userSchema);
export const Conversation = mongoose.model("Conversation", conversationSchema);
export const Message = mongoose.model("Message", messageSchema);

const mediaSchema = new mongoose.Schema({
  conversation: { type: mongoose.Schema.Types.ObjectId, ref: "Conversation", required: true, index: true },
  uploader: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  fileName: { type: String, required: true, maxlength: 180 },
  mimeType: { type: String, required: true, maxlength: 120 },
  size: { type: Number, required: true, max: 2621440 },
  data: { type: Buffer, required: true, select: false }
}, { timestamps: true });

export const Media = mongoose.model("Media", mediaSchema);
