# Mowell Vercel environment variables

Add these under **Vercel project → Settings → Environment Variables** for Production, Preview and Development as appropriate.

```env
MONGODB_URI=mongodb+srv://DATABASE_USER:ROTATED_PASSWORD@YOUR_CLUSTER.mongodb.net/mowell?retryWrites=true&w=majority
JWT_SECRET=GENERATE_A_RANDOM_SECRET_OF_AT_LEAST_32_CHARACTERS
GOOGLE_CLIENT_ID=YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com
ALLOWED_ORIGIN=*
ANDROID_VERSION_CODE=3
ANDROID_VERSION_NAME=0.3.0
ANDROID_APK_URL=https://github.com/Grapaxels/Mowell/releases/latest/download/Mowell.apk
ANDROID_UPDATE_REQUIRED=false
```

`MONGO_URI` is also accepted as an alternative name for `MONGODB_URI`, but configure only one. `GOOGLE_CLIENT_ID` is optional until Google OAuth is set up. Do not add `PORT` on Vercel; Vercel manages it. Redeploy after changing variables.

Generate `JWT_SECRET` locally with PowerShell:

```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

Never commit the real MongoDB URI, JWT secret, Google credentials or Android signing keys.
