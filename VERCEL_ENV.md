# Mowell Vercel environment variables

Add these under **Vercel project → Settings → Environment Variables** for Production, Preview and Development as appropriate.

```env
MONGODB_URI=mongodb+srv://DATABASE_USER:ROTATED_PASSWORD@YOUR_CLUSTER.mongodb.net/mowell?retryWrites=true&w=majority
JWT_SECRET=GENERATE_A_RANDOM_SECRET_OF_AT_LEAST_32_CHARACTERS
GOOGLE_CLIENT_ID=YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com
ALLOWED_ORIGIN=*
ANDROID_UPDATE_REQUIRED=false
SMTP_HOST=smtp.gmail.com
SMTP_PORT=465
SMTP_SECURE=true
SMTP_USER=your-google-mail@gmail.com
SMTP_PASS=your-16-character-google-app-password
SMTP_FROM=Mowell by Grapaxels <your-google-mail@gmail.com>
BLOCKED_EMAIL_DOMAINS=optional-extra-domain.example,another-temp-domain.example
```

`MONGO_URI` is also accepted as an alternative name for `MONGODB_URI`, but configure only one. `GOOGLE_CLIENT_ID` is optional until Google OAuth is set up. `SMTP_PASS` must be a 16-character Google App Password created after enabling 2-Step Verification, not your normal Gmail password. Spaces in a pasted App Password are accepted. `EMAIL_USER`/`EMAIL_APP_PASSWORD` and `MAIL_USER`/`MAIL_PASS` are also accepted aliases. Do not add `PORT` on Vercel; Vercel manages it. Redeploy after changing variables.

After deployment, open `https://mowell-api.grapaxels.in/health/email`. It should return `"configured":true`. The endpoint never returns the email address or password.

Generate `JWT_SECRET` locally with PowerShell:

```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

Never commit the real MongoDB URI, JWT secret, Google credentials or Android signing keys.
The 2.4.2 version, server-hosted APK URL, and verified SHA-256 are built into
the API, so Vercel does not need Android version, URL, or hash variables.
