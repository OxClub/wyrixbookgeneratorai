# WYRIX - Play Store publish guide (Hinglish)

## 0. Pehle ye 3 cheezein badalni hain
1. **Privacy policy ko online karo (GitHub Pages, free):** repo `OxClub/wyrixbookgeneratorai` > Settings > Pages > Source: "Deploy from a branch" > Branch: `main` / folder `/ (root)` > Save. 1-2 minute baad ye link chalega (app aur Play Console dono me yahi use hoga, `MainActivity.kt` me pehle se daala hua hai):
   `https://oxclub.github.io/wyrixbookgeneratorai/privacy_policy.html`
   (Free Pages ke liye repo **public** hona chahiye. Public repo me jks/keystore.properties kabhi mat daalna.)
2. Support email `oxclub@proton.me` app aur privacy policy dono me set hai.
3. Play Store me app ka **title max 30 characters** hota hai. "WYRIX - AI Book Generator & Story Writer" 40 characters ka hai, isliye Play Console me title daalo: **WYRIX - AI Book Generator** (25 chars). Phone ke andar app ka naam poora "WYRIX - AI Book Generator & Story Writer" hi dikhega.
4. Pehli baar build karke test karo (neeche step 1). Main apne yahan Gradle build run nahi kar paya (internet band tha), isliye agar koi compile error aaye to error bhej dena, main fix kar dunga.

## 1. AAB (Play Store file) kaise banayein

### Tareeka A - GitHub Actions (phone/PC dono se, Android Studio nahi chahiye)
1. Is project ko apne GitHub repo me daalo. **jks aur keystore.properties repo me MAT daalna** (ye alag file me diye hain, .gitignore bhi unhe rokta hai, lekin "Upload files" button .gitignore nahi dekhta).
2. Repo > Settings > Secrets and variables > Actions > New repository secret:
   - `WYRIX_KEYSTORE_BASE64` = `wyrixbook.jks.base64.txt` file ka poora text
   - `WYRIX_STORE_PASSWORD` = keystore password
3. Actions tab > "Build APK + AAB" > Run workflow.
4. Khatam hone par Artifacts me milega:
   - `WYRIX-release-aab` -> **app-release.aab** (ye Play Store par upload hoga)
   - `WYRIX-debug-apk` -> testing APK

### Tareeka B - Android Studio
1. Project folder kholo. `wyrixbook.jks` aur `keystore.properties` ko project ke **root folder** me (settings.gradle.kts ke saath) copy karo.
2. Terminal: `gradle bundleRelease` (ya Build > Generate Signed App Bundle, alias `wyrixbook`).
3. File: `app/build/outputs/bundle/release/app-release.aab`

## 2. Keystore (signing key) ki jaankari
- File: `wyrixbook.jks`, alias: `wyrixbook`, validity 2056 tak.
- SHA-1: `40:2F:13:56:EC:80:17:8C:E7:94:AD:CC:80:10:D5:6E:79:A5:93:CD`
- SHA-256: `70:02:DF:67:FA:47:43:33:EE:DE:E9:92:1D:B6:1C:0D:0D:75:35:B5:85:E6:A3:7D:1E:CD:EB:49:17:63:87:B2`
- **Is jks file ka 2-3 jagah backup rakho** (Drive + pen drive). Play App Signing on rakhne par bhi ye "upload key" hai; kho gaya to update daalne ke liye Google support se reset karwana padega.
- Password kisi ko share mat karo aur GitHub par public repo me mat daalo.

## 3. Play Console me kya karna hai
1. Create app > Name: **WYRIX - AI Book Generator**, Default language, App (not game), Free.
2. **Play App Signing**: enabled rehne do, pehla AAB upload karte waqt apni jks se signed AAB do.
3. Production (ya pehle Internal/Closed testing) > Create release > AAB upload. Naye personal developer accounts ko production se pehle 12 testers ke saath 14 din closed testing karni padti hai.
4. Store listing: title, short description (max 80), full description (max 4000), icon `playstore/play_store_icon_512.png`, feature graphic `playstore/feature_graphic_1024x500.png`, kam se kam 2 phone screenshots.
5. App content:
   - **Privacy policy URL**: hosted `privacy_policy.html` ka link.
   - **Data safety**: developer koi data collect nahi karta. User ke prompts (user content) user ki chuni hui third-party AI services ko bheje jaate hain (app functionality); transit me encrypted (HTTPS). Data local hai, delete app me hi ho jata hai.
   - **Content rating** questionnaire: AI-generated text content hai (user-generated/AI content wale sawaal dhyan se bharna).
   - **Target audience**: 13+ ya 18+ (bachchon ke liye mat rakho).
   - **Ads**: No ads.
   - **Foreground service permission** (`FOREGROUND_SERVICE_DATA_SYNC`): Console me use declare karna hoga ("long-running book generation / data sync") aur kabhi kabhi ek chhota demo video maangte hain.
   - **Generative AI**: app me AI content report karne ka button hai (Reader / About screen), ye Play ki AI-content policy ke liye zaroori hai. Support email sahi daalna na bhoolo.
6. Har naye update se pehle `app/build.gradle.kts` me `versionCode` +1 karo.

## 3b. Adult / unsafe content protection (Play policy)
App 3 layer me rokta hai:
1. **Prompt filter** (`ContentFilter.kt`): English, Hinglish aur Hindi (Devanagari) me sexual/18+, minors se related, bomb/suicide-method jaise prompts "Generate" dabate hi block ho jate hain.
2. **AI safety rules**: har AI request ke saath safety instruction jaati hai; model mana kare to book `BLOCKED` ho jati hai.
3. **Output scan**: likha hua text me explicit shabd mile to ek baar saaf karwata hai, phir bhi mile to book rok deta hai.
Aur Reader/About me "Report content" button hai. Blocked word list `ContentFilter.kt` me hai, aap aur shabd jod sakte ho.
Dhyan rahe: koi bhi filter 100% nahi hota (user spelling badal sakta hai). Isliye Play Console ke content rating form me honestly AI-generated text batao, aur target audience 18+ rakhne ka option bhi soch lo.

## 4. App me kya naya hai
- Naam: "WYRIX - AI Book Generator & Story Writer"; aapki image launcher icon (adaptive + round) aur notification me.
- Har API key ke niche **"How to get this key"** steps + website kholne ka button (Gemini, Groq, OpenRouter, Cerebras, Mistral, SambaNova, NVIDIA, GitHub Models, HuggingFace, Custom/DeepSeek).
- Production features: API keys encrypted storage, in-app Reader (font size, Hindi/English toggle), Pause/Resume, Delete book (confirm), PDF/EPUB export ke baad Share/Open, genre selection (Story, Novel, Self-help, Kids, Educational, Biography), "Add API key" banner, Fetch models ka result message, About + Privacy + AI notice, Report content, database migration (purani books safe), release build me R8 shrink, backup me keys exclude, Android 16 (API 36) target, edge-to-edge + keyboard handling, notification tap se app khulta hai.

## 5. Aage ke feature idea
Cover image generation, DOCX export, chapter-wise edit/regenerate, online backup (Drive), multiple languages (Marathi, Bengali, Tamil...), widget/streak, Play Billing for premium.
