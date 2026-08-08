# Mowell Vercel environment variables

Add these under **Vercel project → Settings → Environment Variables** for Production, Preview and Development as appropriate.

```env
MONGODB_URI=mongodb+srv://DATABASE_USER:ROTATED_PASSWORD@YOUR_CLUSTER.mongodb.net/mowell?retryWrites=true&w=majority
JWT_SECRET=GENERATE_A_RANDOM_SECRET_OF_AT_LEAST_32_CHARACTERS
GOOGLE_CLIENT_ID=YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com
ALLOWED_ORIGIN=*
PUBLIC_BASE_URL=https://mowell-api.grapaxels.in
ANDROID_VERSION_CODE=23
ANDROID_VERSION_NAME=1.5.7
ANDROID_APK_URL=https://mowell-api.grapaxels.in/v1/app/apk
ANDROID_APK_SHA256=DF860366CEF7DC0014A5BC0F410E5ECBAA1884A9DE54A3D3D7EA7D82010907F8
ANDROID_UPDATE_REQUIRED=false
SMTP_HOST=smtp.gmail.com
SMTP_PORT=465
SMTP_SECURE=true
SMTP_USER=your-google-mail@gmail.com
SMTP_PASS=your-16-character-google-app-password
SMTP_FROM=Mowell from Grapaxels <your-google-mail@gmail.com>
BLOCKED_EMAIL_DOMAINS=optional-extra-domain.example,another-temp-domain.example
STUN_URLS=stun:turn.your-domain.com:3478
TURN_URLS=turn:turn.your-domain.com:3478?transport=udp,turns:turn.your-domain.com:5349?transport=tcp
TURN_USERNAME=your-turn-username
TURN_CREDENTIAL=your-turn-password
```

`MONGO_URI` is also accepted as an alternative name for `MONGODB_URI`, but configure only one. `GOOGLE_CLIENT_ID` is optional until Google OAuth is set up. `SMTP_PASS` must be a 16-character Google App Password created after enabling 2-Step Verification, not your normal Gmail password. Spaces in a pasted App Password are accepted. `EMAIL_USER`/`EMAIL_APP_PASSWORD` and `MAIL_USER`/`MAIL_PASS` are also accepted aliases. Do not add `PORT` on Vercel; Vercel manages it. Redeploy after changing variables.

The deploy includes `server/assets/Mowell.apk` and serves it from `/v1/app/apk`, so the in-app updater no longer depends on a separately created GitHub Release. For every future update, replace that APK, increase `ANDROID_VERSION_CODE`, update its SHA-256, push, and redeploy the server.

After deployment, open `https://mowell-api.grapaxels.in/health/email`. It should return `"configured":true`. The endpoint never returns the email address or password.

Generate `JWT_SECRET` locally with PowerShell:

```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

Never commit the real MongoDB URI, JWT secret, Google credentials or Android signing keys.
