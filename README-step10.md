# ExamCamp Android: ধাপ ১০ (রিলিজ)

## এই ধাপে যা যোগ হয়েছে
- `app/build.gradle.kts`
  - Release signing এখন environment variable থেকে (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)। রিপোতে কোনো গোপন তথ্য নেই। variable না থাকলে (লোকাল বিল্ড) debug key-তে সাইন হয়, শুধু R8 পরীক্ষার জন্য। সেটা কখনও Play-তে দেবেন না।
  - `versionCode` = GitHub run নম্বর (প্রতি রিলিজে নিজে বাড়ে), `versionName` = ট্যাগ বা workflow ইনপুট।
  - শুধু `en` ও `bn` রিসোর্স রাখা হয় (APK ছোট)। AAB-তে ভাষা-স্প্লিট বন্ধ, যাতে অ্যাপের ভাষা বদলালে বাংলা সবসময় থাকে।
- `.github/workflows/release.yml`: signed APK + AAB + R8 `mapping.txt` বানায়। secret না থাকলে স্পষ্ট error দিয়ে থামে। লগে release SHA-1/SHA-256 ছাপে এবং `apksigner` দিয়ে সাইন যাচাই করে।
- `tools/make-release-keystore.sh`: keystore একবার বানানোর স্ক্রিপ্ট। চার secret-এর মান ও Firebase-এর fingerprint ছাপে।
- `app/proguard-rules.pro`: kotlinx.serialization-এর সম্পূর্ণ keep rules, `data/` মডেল, Credential Manager, OkHttp warning।
- আইকন: লঞ্চার আইকন (সব আকার) ও adaptive icon আগে থেকেই ছিল এবং নিরাপদ সীমার ভেতরে। যোগ হয়েছে Android 13+ themed (monochrome) আইকন।
- `store/PLAY_STORE.md`: বর্ণনা (EN/BN), Data safety উত্তর, রিলিজ নোট।
- ওয়েবসাইট ZIP-এ `privacy.html` (EN + বাংলা সারাংশ)।
- `.gitignore`-এ `*.keystore` (debug keystore বাদে) যোগ।

## আপনাকে যা করতে হবে (ক্রমে)
1. **Keystore বানান** (নিজের কম্পিউটারে, Java লাগবে): `bash tools/make-release-keystore.sh`। পাসওয়ার্ড দিন। ফাইলটি (`examcamp-release.keystore`) নিরাপদ জায়গায় ব্যাকআপ রাখুন। হারালে আর এই কী দিয়ে আপডেট দেওয়া যাবে না। কখনও গিটে তুলবেন না।
2. **GitHub Secrets** (রিপো → Settings → Secrets and variables → Actions) চারটি: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `examcamp`), `KEY_PASSWORD` (পাসওয়ার্ডের মতোই)।
3. **Firebase**-এ release SHA-1 ও SHA-256 যোগ করুন (Project settings → আপনার Android অ্যাপ), তারপর নতুন `google-services.json` নামিয়ে `app/`-এ বদলান। এখন ফাইলে শুধু debug SHA-1 আছে, তাই release APK-তে Google Sign-In কাজ করবে না।
4. **Play App Signing ব্যবহার করলে** (নতুন অ্যাপে বাধ্যতামূলক): Play Console → Setup → App signing থেকে "App signing key certificate"-এর SHA-1/SHA-256ও Firebase-এ যোগ করুন। Play থেকে ইনস্টল করা অ্যাপ ওই কী দিয়ে সাইন হয়, আপনার upload key দিয়ে নয়।
5. **Release চালান**: Actions → "Release (signed APK + AAB)" → Run workflow (version দিন), অথবা `git tag v1.0.0 && git push --tags`।
6. Artifacts থেকে `examcamp-release-apk` ফোনে ইনস্টল করে পরীক্ষা করুন, `examcamp-release-aab` Play Console-এ আপলোড করুন, `examcamp-r8-mapping` ডিঅবফাসকেশন ফাইল হিসেবে দিন।
7. ওয়েবসাইট রিপোতে `privacy.html` পুশ করুন (`YOUR_CONTACT_EMAIL` বদলান), তারপর `store/PLAY_STORE.md` ধরে Play listing ভরুন।

## যাচাই তালিকা (Release APK দিয়ে ফোনে)
- Google Sign-In কাজ করে, সাইন-ইনের পর History সিঙ্ক হয়।
- AI পরীক্ষা তৈরি (Gemini), পরীক্ষা দেওয়া, ফলাফল, PDF/ছবি শেয়ার।
- Practice (ক্যাটাগরি → টপিক), Live Exam পেজ, Admin (আপনার ইমেইলে)।
- বিমান মোড: ব্যানার, History, সংরক্ষিত Live পেজ।
- কোনো স্ক্রিনে crash নেই। crash হলে `adb logcat` দেখে `SerializationException` বা `ClassNotFoundException` খুঁজুন: এগুলো ProGuard নিয়মের অভাব বোঝায়, লগ পাঠান।

## সীমাবদ্ধতা (খোলাখুলি)
- Android SDK না থাকায় এখানে কম্পাইল বা R8 চালানো যায়নি। Gradle ফাইল ও workflow সিনট্যাক্স যাচাই করা হয়েছে, কিন্তু প্রথম বিল্ডে লাল error আসতে পারে। লগ পাঠালে ঠিক করে দেব।
- `-keep class com.examcamp.app.data.**` ইচ্ছে করে বড় রাখা হয়েছে (নিরাপদ দিক)। APK সাইজ নিয়ে সমস্যা হলে পরে সংকুচিত করা যাবে।
- Play-র নতুন ব্যক্তিগত ডেভেলপার অ্যাকাউন্টের closed testing (টেস্টার সংখ্যা ও দিন) নিয়ম বদলায়, Play Console-এ বর্তমান নিয়ম মিলিয়ে নিন।
- Google Sign-In-এ Firebase-এর web client ID (`client_type: 3`) আগের মতোই; এই ধাপে বদলায়নি।
- ধাপ ৯-এর বাকি তিনটি এখনও খোলা: "সব প্রশ্ন অফলাইনের জন্য নামান" বাটন, সোয়াইপ-টু-ডিলিট (tombstone সহ), পিক্সেল মিলানো।

## Gemini মডেল হালনাগাদ (২০ অক্টোবর ২০২৬)
- ai.google.dev/gemini-api/docs/models থেকে মেলানো স্থিতিশীল তালিকা: `gemini-3.8-flash` (ডিফল্ট), `gemini-4-argon`, `gemini-3.6-flash`, `gemini-3.5-flash-lite`, `gemini-3.1-flash-lite`।
- বাদ: `gemini-3.5-flash`, `gemini-3.1-pro-preview`, `gemini-2.5-flash/pro` (Google নতুন প্রজেক্টে 2.5 সীমিত করেছে)।
- আগে সংরক্ষিত বাদ-যাওয়া মডেল থাকলে `GeminiModels.resolve` (অ্যাপ) ও `resolveGeminiModel` (ওয়েব) নিজে ডিফল্টে নেয়, তাই পুরোনো ইউজারের AI তৈরি ভাঙে না।
- ওয়েব `app.js` বদলেছে, `sw.js` cache v15। ওয়েবও পুশ করুন, কারণ মডেল পছন্দ ক্লাউডে সিঙ্ক হয়।
