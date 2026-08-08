import mongoose from "mongoose";

const userSchema = new mongoose.Schema({
  email: { type: String, unique: true, required: true, lowercase: true, trim: true, index: true },
  username: { type: String, unique: true, required: true, lowercase: true, trim: true, index: true },
  displayName: { type: String, required: true, trim: true, maxlength: 60 },
  passwordHash: { type: String, select: false },
  googleSub: { type: String, unique: true, sparse: true, select: false },
  emailVerified: { type: Boolean, default: false },
  verificationCodeHash: { type: String, select: false },
  verificationExpiresAt: { type: Date, select: false },
  verificationLastSentAt: { type: Date, select: false },
  verificationAttempts: { type: Number, default: 0, select: false },
  passwordResetCodeHash: { type: String, select: false },
  passwordResetExpiresAt: { type: Date, select: false },
  passwordResetAttempts: { type: Number, default: 0, select: false },
  avatarUrl: String,
  avatarData: { type: Buffer, select: false },
  avatarMime: { type: String, select: false },
  lastSeenAt: { type: Date, default: Date.now }
}, { timestamps: true });

const conversationSchema = new mongoose.Schema({
  title: { type: String, trim: true, maxlength: 80 },
  isGroup: { type: Boolean, default: false },
  groupType: { type: String, enum: ["public", "private", "password"], default: "private" },
  groupPasswordHash: { type: String, select: false },
  members: [{ type: mongoose.Schema.Types.ObjectId, ref: "User", required: true }],
  createdBy: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  admins: [{ type: mongoose.Schema.Types.ObjectId, ref: "User" }],
  bannedMembers: [{ type: mongoose.Schema.Types.ObjectId, ref: "User" }],
  avatarUrl: String,
  avatarData: { type: Buffer, select: false },
  avatarMime: { type: String, select: false },
  lastMessageAt: { type: Date, default: Date.now }
}, { timestamps: true });
conversationSchema.index({ members: 1, lastMessageAt: -1 });

const messageSchema = new mongoose.Schema({
  clientId: { type: String, required: true },
  conversation: { type: mongoose.Schema.Types.ObjectId, ref: "Conversation", required: true, index: true },
  sender: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  body: { type: String, required: true, maxlength: 8000 },
  kind: { type: String, enum: ["text", "image", "audio", "video", "file", "location", "contact", "call", "call_end", "system", "sticker", "poll", "link", "collaborative_document", "collaborative_whiteboard"], default: "text" },
  attachment: { type: mongoose.Schema.Types.ObjectId, ref: "Media" },
  editedAt: Date,
  replyToClientId: { type: String, maxlength: 100 },
  threadRootClientId: { type: String, maxlength: 100 },
  reactions: [{
    user: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
    emoji: { type: String, required: true, maxlength: 16 }
  }],
  metadata: { type: mongoose.Schema.Types.Mixed, default: {} },
  deliveredTo: [{ type: mongoose.Schema.Types.ObjectId, ref: "User" }],
  seenBy: [{ type: mongoose.Schema.Types.ObjectId, ref: "User" }],
  sentAt: { type: Date, default: Date.now }
}, { timestamps: true });
messageSchema.index({ conversation: 1, clientId: 1 }, { unique: true });
messageSchema.index({ conversation: 1, sentAt: -1 });

export const User = mongoose.model("User", userSchema);
export const Conversation = mongoose.model("Conversation", conversationSchema);
export const Message = mongoose.model("Message", messageSchema);

// Contacts require explicit approval. Existing rows are migrated to accepted
// by the API so upgrades never hide established conversations.
const contactSchema = new mongoose.Schema({
  pairKey: { type: String, unique: true, required: true, index: true },
  users: [{ type: mongoose.Schema.Types.ObjectId, ref: "User", required: true }],
  addedBy: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  status: { type: String, enum: ["pending", "accepted", "declined"], default: "accepted", index: true },
  blockedBy: [{ type: mongoose.Schema.Types.ObjectId, ref: "User" }]
}, { timestamps: true });
contactSchema.index({ users: 1 });

export const Contact = mongoose.model("Contact", contactSchema);

const groupInvitationSchema = new mongoose.Schema({
  conversation: { type: mongoose.Schema.Types.ObjectId, ref: "Conversation", required: true, index: true },
  inviter: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  invitee: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true, index: true },
  status: { type: String, enum: ["pending", "accepted", "declined"], default: "pending", index: true }
}, { timestamps: true });
groupInvitationSchema.index({ conversation: 1, invitee: 1 }, { unique: true });

export const GroupInvitation = mongoose.model("GroupInvitation", groupInvitationSchema);

const typingStateSchema = new mongoose.Schema({
  conversation: { type: mongoose.Schema.Types.ObjectId, ref: "Conversation", required: true, index: true },
  user: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  expiresAt: { type: Date, required: true, expires: 0 }
}, { timestamps: true });
typingStateSchema.index({ conversation: 1, user: 1 }, { unique: true });

export const TypingState = mongoose.model("TypingState", typingStateSchema);

const mediaSchema = new mongoose.Schema({
  conversation: { type: mongoose.Schema.Types.ObjectId, ref: "Conversation", required: true, index: true },
  uploader: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  fileName: { type: String, required: true, maxlength: 180 },
  mimeType: { type: String, required: true, maxlength: 120 },
  size: { type: Number, required: true, max: 2621440 },
  data: { type: Buffer, required: true, select: false }
}, { timestamps: true });

export const Media = mongoose.model("Media", mediaSchema);

const callSignalSchema = new mongoose.Schema({
  room: { type: String, required: true, index: true, maxlength: 100 },
  sender: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  // A client-generated id makes signaling retries safe. Without it, a brief
  // serverless/Mongo failure can either lose an ICE candidate or duplicate an
  // SDP offer when the Android client retries the request.
  clientId: { type: String, maxlength: 180 },
  target: { type: mongoose.Schema.Types.ObjectId, ref: "User" },
  type: { type: String, enum: ["join", "offer", "answer", "ice", "leave", "media"], required: true },
  payload: { type: mongoose.Schema.Types.Mixed, default: {} },
  expiresAt: { type: Date, default: () => new Date(Date.now() + 60 * 60 * 1000), expires: 0 }
}, { timestamps: true });
callSignalSchema.index({ room: 1, _id: 1 });
callSignalSchema.index(
  { room: 1, sender: 1, clientId: 1 },
  { unique: true, partialFilterExpression: { clientId: { $type: "string" } } }
);

export const CallSignal = mongoose.model("CallSignal", callSignalSchema);

const callRoomSchema = new mongoose.Schema({
  room: { type: String, required: true, unique: true, maxlength: 100 },
  conversation: { type: mongoose.Schema.Types.ObjectId, ref: "Conversation", required: true },
  participants: [{ type: mongoose.Schema.Types.ObjectId, ref: "User", required: true }],
  // Invited members stay in `participants` for authorization. This separate
  // list represents people actually connected to the live WebRTC call.
  activeParticipants: [{ type: mongoose.Schema.Types.ObjectId, ref: "User" }],
  createdBy: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
  video: { type: Boolean, default: false },
  status: { type: String, enum: ["ringing", "active", "ended"], default: "ringing", index: true },
  answeredAt: Date,
  endedAt: Date,
  unansweredExpiresAt: { type: Date, default: () => new Date(Date.now() + 45 * 1000) },
  expiresAt: { type: Date, default: () => new Date(Date.now() + 6 * 60 * 60 * 1000), expires: 0 }
}, { timestamps: true });

export const CallRoom = mongoose.model("CallRoom", callRoomSchema);
