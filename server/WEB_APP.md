# Mowell Web deployment

The responsive web client is served from this same `server` project. The Android
`app` project is not required for the web deployment and remains unchanged from
Mowell 1.3.1.

## Vercel

1. Deploy the `server` folder as the Vercel project root.
2. Keep the same MongoDB, JWT, SMTP, and Google environment variables already
   used by the Mowell API.
3. Add `mowell-api.grapaxels.in` and `mowellweb.grapaxels.in` to the same Vercel
   project, or point the web domain at another deployment of this same folder.
4. Keep `ALLOWED_ORIGIN=*`, or set it to `https://mowellweb.grapaxels.in` if the
   web and API domains use separate deployments.

Open `https://mowellweb.grapaxels.in` and sign in with the same verified Mowell
email/username and password used in the Android app.

QR device linking is deliberately not included: adding a QR scanner and linked
device approval flow would require changing the Android 1.3.1 application.
