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

## Reliable calls on restricted networks

STUN can create direct calls on ordinary networks, but some mobile carriers,
office firewalls, and symmetric NAT routers require a TURN relay. Cloudflare
Realtime TURN currently includes a 1,000 GB monthly free tier.

Create a TURN key in Cloudflare Realtime and add these server-side Vercel
variables:

```text
CLOUDFLARE_TURN_KEY_ID=your_turn_key_id
CLOUDFLARE_TURN_API_TOKEN=your_turn_key_api_token
```

Do not expose the API token in `web/app.js`. The Mowell server exchanges it for
short-lived browser credentials through `/v1/calls/ice-servers`.

If you operate the free, open-source coturn server instead, use:

```text
TURN_URLS=turn:turn.example.com:3478?transport=udp,turns:turn.example.com:5349?transport=tcp
TURN_USERNAME=your_username
TURN_CREDENTIAL=your_password
```

Open `https://mowellweb.grapaxels.in` and sign in with the same verified Mowell
email/username and password used in the Android app.

QR device linking is deliberately not included: adding a QR scanner and linked
device approval flow would require changing the Android 1.3.1 application.
