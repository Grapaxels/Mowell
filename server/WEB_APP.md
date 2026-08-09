# Mowell Web deployment

The responsive web client is served from this same `server` project. Android
2.4.1 loads the same authenticated WebRTC ICE configuration used by the web
client (`versionCode` 41).

## Vercel

1. Deploy the `server` folder as the Vercel project root.
2. Keep the same MongoDB, JWT, SMTP, and Google environment variables already
   used by the Mowell API.
3. Add `mowell-api.grapaxels.in` and `mowellweb.grapaxels.in` to the same Vercel
   project, or point the web domain at another deployment of this same folder.
4. Keep `ALLOWED_ORIGIN=*`, or set it to `https://mowellweb.grapaxels.in` if the
   web and API domains use separate deployments.

## WebRTC calls

Mowell first attempts peer-to-peer WebRTC and uses the fixed Metered TURN routes
in `server/src/index.js` when a direct route is unavailable. Authenticated web
and Android clients obtain the ICE array from `/v1/calls/ice-servers`.

No TURN environment variables are required. Because the fixed credential is
present in repository source, access to the repository also grants access to
the Metered allocation. Rotate it in Metered and update `callIceServers` if the
repository or credential is exposed.

Open `https://mowellweb.grapaxels.in` and sign in with the same verified Mowell
email/username and password used in the Android app.

The existing 1.3.2 web layout is retained. A web-only QR action opens a
scannable code for `https://mowellweb.grapaxels.in`; it does not change the
Android UI or perform account/device pairing.

The signed 2.4.1 APK is included in Vercel's `server/public` directory and is
served as a static asset at
`https://mowell-api.grapaxels.in/Mowell-v2.4.1.apk`, avoiding private GitHub
raw-download authentication and serverless-function response-size limits.
