# এই রাউন্ডে যা করা হয়েছে (README-parity অনুযায়ী)

> এগুলো Android SDK ছাড়া লেখা, তাই **কম্পাইল ও টেস্ট চালানো হয়নি**। প্রথম কাজ: GitHub Actions চালিয়ে লগ পাঠানো (ধাপ A)।

## ধাপ B: তুলনার যন্ত্র (`tools/parity/`)
1. ওয়েব: `pip install playwright && playwright install chromium`, তারপর
   `python3 tools/parity/web_screens.py --site <examcamp-main ফোল্ডার বা URL> --out shots/web`
   (৩৯০×৮৪৪, ৩৬০×৮০০, ৪১২×৯১৫ × light/dark × en/bn, DPR 3)
2. অ্যাপ: ফোনে স্ক্রিন খুলে `tools/parity/app_screens.sh home light en 390x844` (একই ফাইল-নাম)
3. ডিফ: `python3 tools/parity/diff_screens.py --web shots/web --app shots/app --crop-top <স্ট্যাটাস বার px> --crop-bottom <নেভ বার px>`
   → `shots/diff/*__web-app-diff.png` (ওয়েব | অ্যাপ | ডিফ) আর শতকরা পার্থক্যের তালিকা।
   এই তিন-ভাগের ছবি পাঠালে কারণ বের করা যায়।

## ধাপ C: ওয়েব CSS বনাম Kotlin
- মিলেছে (Chromium-এ গণনা-করা মান থেকে): লাইট ও ডার্ক রঙ, radius, ফর্ম ফিল্ড (৪৪ উচ্চতা, ১৬ padding), আইকন বাটন ৪৪, segmented ৪০।
- **ভুল পাওয়া গিয়ে ঠিক করা:** ফোনে (≤৬৪০px) শিরোনাম h1 26 / h2 21 / h3 18 (আগে 36/28/24 ছিল), small/label-এর line-height 1.6, letter-spacing -0.005em, বাটনের উচ্চতা ৪৪ (আগে ৪৮), কার্ডের padding ২০ (আগে ১৬)।
- বাংলা: letter-spacing সবসময় 0 (`[lang=bn]` নিয়ম), h2/h3 line-height 1.3 (ইংরেজিতে 1.1)।
- ছায়া: `BoxShadow.kt` CSS `box-shadow`-এর মতো আঁকে (segmented, toggle thumb, ইউজার মেনু `0 10px 30px .12`)। ডায়ালগের radius ৮ (আগে M3-র ২৮)।
- সংখ্যাগুলো আবার বের করতে: `tools/parity/web_metrics.py`।
- ঠিক করা: `Icons.kt`-এর `File` আইকন ওয়েবের SVG-র সাথে মেলানো (path আর stroke 1.6)। বাকি আইকনের path ওয়েবের সাথে মিলেছে (circle/rect শুধু path-এ রূপান্তর)।
- **ফন্ট:** CI-তে ডাউনলোড ব্যর্থ হলে আগে নিঃশব্দে সিস্টেম ফন্টে নামত। এখন `build.yml` ও `release.yml` ফন্ট না পেলে বিল্ড থামায়। সবচেয়ে ভালো: `Lato-Regular.ttf`, `Lato-Bold.ttf`, `NotoSerifBengali.ttf` ফাইল `app/src/main/assets/fonts/`-এ কমিট করুন (এখানে নেটওয়ার্ক নেই, তাই আমি দিতে পারিনি)।
- বাকি (স্ক্রিনশট ডিফ ছাড়া ঠিক করা যায় না): ছায়ার নরমতা, কার্ড/রো-র pressed অবস্থা, notched field, বাংলা লাইন-হাইট।

## Delete account (ওয়েব ও অ্যাপ)
- **সার্ভার (আপনাকে deploy করতে হবে):** `examcamp-main/supabase/functions/delete-account/index.ts`
  ```
  supabase functions deploy delete-account --no-verify-jwt
  supabase secrets set FIREBASE_API_KEY=<Firebase web API key>
  ```
  ফাংশনটি Firebase ID token Google দিয়ে যাচাই করে, তারপর মোছে: `user_data`, `practice_progress` (firebase_uid), `enrollment_requests`, `members` (email)। `live_exam_submissions`-এ নাম ও ইমেইল বেনামি করা হয় (মেধাতালিকা ঠিক থাকে)। কোন টেবিল মুছবেন আর কোনটা রাখবেন তা `index.ts`-এ বদলানো যায়। টেবিলের কলামের নাম ওয়েবের কোড থেকে নেওয়া; আপনার স্কিমার সাথে একবার মিলিয়ে নিন।
- **ওয়েব:** অ্যাকাউন্ট মেনুতে "Delete account", কনফার্ম মডাল, EN/BN টেক্সট; `sw.js` cache `v14`।
- **অ্যাপ:** একই মেনুতে বাটন ও ডায়ালগ; `AuthManager.deleteAccount` (Firebase "recent login" লাগলে Google একবার আবার বেছে নেওয়া), ফোনের ডাটা মোছা, অপেক্ষমাণ আপলোড বাতিল।
- `privacy.html` হালনাগাদ। Play Console-এর Data safety-তে "account deletion" লিংক হিসেবে এই পেজ দিন।

## ধাপ F: ইউনিট টেস্ট
- `app/src/test/...`: `ScoringTest` (স্কোর, নেগেটিভ মার্কিং, সময় clamp), `HistoryTest` (`mergeHistories`, tombstone, `capHistory`, overview), `MathParserTest` (পার্সার, ডিলিমিটার, ফলব্যাক)।
- টেস্ট করার জন্য দুটি ফাংশন বের করা হয়েছে (আচরণ একই): `scoreSession()` (`data/Scoring.kt`) ও `mergeHistories()` (`data/Records.kt`)।
- চালানো: `gradle :app:testDebugUnitTest`; `build.yml`-এ APK-র আগে চলে।

## ফিচার-ধরে-ধরে বিশ্লেষণ (এই রাউন্ড)
- **টেক্সট:** ওয়েবের ২৮২টি কী মেলানো হয়েছে। অ্যাপে নেই শুধু AGQ প্যানেলের ১০৫টি (আপনি বাদ দিয়েছেন) আর `auth.deleteModal.working` (অ্যাপে দরকার নেই)।
- **স্ক্রিন:** home, live exam (+admin), history, practice (subjects/mode/admin), statistics, generating, exam, stats সবই অ্যাপে আছে। ছিল না: **Print/Download-এর শিট**।
- **ঠিক করা:** ওয়েবের মতো `Answer Sheet` / `Explanation Sheet` ডায়ালগ, আর মার্ক করা শিট (সঠিক সবুজ `#0A7D43`, ভুল বাছাই লাল `#C62828`, ফলাফলের সারি, ব্যাখ্যা শুধু Explanation Sheet-এ, ফুটার `Page 01/05`)। `PdfExporter.markedSheet`।
- **যাচাই করা, মিলেছে:** Evaluate-এর shuffle ও মডিউল ভাগ, tombstone-সহ ইতিহাস মোছা, publish gate, negative marking, মেধাতালিকা।
- **ব্যতিক্রম (ইচ্ছাকৃত):** ওয়েবে A4 পেজিনেশন, বিকল্পের লেআউট (অনুভূমিক/আয়তাকার/উল্লম্ব) আর KaTeX ছাপা হয়; অ্যাপের PDF-এ সাধারণ টেক্সট ও গণিতের Unicode রূপ।
