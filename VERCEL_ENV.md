# Mowell Vercel environment variables

Add these under **Vercel project → Settings → Environment Variables** for Production, Preview and Development as appropriate.

```env
MONGODB_URI=mongodb+srv://DATABASE_USER:ROTATED_PASSWORD@YOUR_CLUSTER.mongodb.net/mowell?retryWrites=true&w=majority
JWT_SECRET=GENERATE_A_RANDOM_SECRET_OF_AT_LEAST_32_CHARACTERS
GOOGLE_CLIENT_ID=YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com
ALLOWED_ORIGIN=*
ANDROID_VERSION_CODE=7
ANDROID_VERSION_NAME=1.1.0
ANDROID_APK_URL=https://github.com/Grapaxels/Mowell/releases/latest/download/Mowell.apk
ANDROID_APK_SHA256=D22CBB5393B27BC46557474391B7C59682A805C0582F66FDFA8AD57C51A7A8EB
ANDROID_UPDATE_REQUIRED=false
SMTP_HOST=smtp.gmail.com
SMTP_PORT=465
SMTP_SECURE=true
SMTP_USER=your-google-mail@gmail.com
SMTP_PASS=your-16-character-google-app-password
SMTP_FROM=Mowell by Grapaxels <your-google-mail@gmail.com>
BLOCKED_EMAIL_DOMAINS=optional-extra-domain.example,another-temp-domain.example
```

`MONGO_URI` is also accepted as an alternative name for `MONGODB_URI`, but configure only one. `GOOGLE_CLIENT_ID` is optional until Google OAuth is set up. `SMTP_PASS` must be a Google App Password, not your normal Google password. Do not add `PORT` on Vercel; Vercel manages it. Redeploy after changing variables.

Generate `JWT_SECRET` locally with PowerShell:

```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

Never commit the real MongoDB URI, JWT secret, Google credentials or Android signing keys.
