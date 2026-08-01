# Mowell by Grapaxels — v0.3

Mowell is a native Android 7+ communication prototype with a Grapaxels-inspired claymorphic interface. It combines central identity and discovery with phone-local SQLite storage and nearby Bluetooth routing.

The Android client defaults to `https://mowell-api.grapaxels.in` for its central API.

## Included in v0.2

- 1.5-second Mowell splash screen.
- Email/password registration and login through the Mowell MongoDB API.
- Google sign-in flow; enter the same Google web client ID in the app and backend.
- Persistent login: the token remains in private app preferences until **You → Log out**.
- Globally unique usernames and central username-prefix search.
- User selection and direct conversation creation.
- MongoDB models for users, conversations and messages.
- Vercel-compatible REST delivery and two-second foreground synchronization for internet messages.
- SQLite conversations, messages and cached user-search results on the phone.
- Paired Bluetooth RFCOMM transport with packet IDs, duplicate suppression, hop limits and store-and-forward relay across running Mowell peers.
- Voice/video/group-call experience and adaptive media policy UI.
- In-app version check, APK download and installer handoff.
- Android 7.0 / API 24 minimum.

## Deploy the central service to Vercel

1. Import this GitHub repository into Vercel and set the project root to `server`.
2. Add the MongoDB Atlas URI to the Vercel secret `MONGODB_URI` (or `MONGO_URI`). Never put this URI in the Android app.
3. Generate a random `JWT_SECRET` containing at least 32 characters.
4. Add a Google OAuth web client ID to `GOOGLE_CLIENT_ID` if Google sign-in is needed.
5. Set the version and hosted APK URL variables for in-app updates.
6. Deploy. `server/vercel.json` routes API traffic to the Express Vercel Function.
7. Enter the resulting HTTPS deployment URL in Mowell's **Server setup**.

For local backend development, copy `server/.env.example` to the ignored `server/.env`, then run `npm install` and `npm start` inside `server/`.

The v0.1 APK must be manually upgraded to v0.2 once because v0.1 did not contain the updater. Later releases can be offered through the in-app update popup. Android always requires the user to confirm installation; silent self-updates are prohibited.

## Media and Bluetooth limits

Production calls require a WebRTC media implementation, signaling integration, STUN/TURN servers and end-to-end call encryption. Adaptive bitrate, congestion control and jitter buffering minimize stalls; media cannot be pre-downloaded during an interactive call. Bluetooth does not have enough throughput for HD/4K video, so nearby video is intentionally unavailable. The current call screens and policies are a prototype, not a live media implementation.

The nearby packet layer implements useful Bitchat-inspired behaviors but is not a complete clone of Bitchat. Background BLE discovery, cryptographic peer identity, multi-radio mesh operation, attachments and audited end-to-end encryption require further production engineering.
