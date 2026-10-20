# ExamCamp Android: রিভিউ রিপোর্ট (২০ অক্টোবর ২০২৬)

## কীভাবে যাচাই হয়েছে
এই পরিবেশে Android SDK ও ইন্টারনেট নেই, তাই **কম্পাইল, টেস্ট চালানো বা APK বানানো যায়নি**।
বদলে ৫৭টি Kotlin ফাইলে (প্রায় ১২,৮৬৬ লাইন) স্ক্রিপ্ট দিয়ে স্ট্যাটিক চেক চালানো হয়েছে।

## যা মিলেছে (সমস্যা পাওয়া যায়নি)
- ব্র্যাকেট `{ } ( ) [ ]` সব ফাইলে সুষম।
- সব ফাইলের `package` লাইন ফোল্ডারের সাথে মেলে।
- প্রজেক্টের ভেতরের সব `import` এমন কিছুর দিকে যায় যা সত্যিই আছে (`R` ছাড়া, সেটি বিল্ডের সময় তৈরি হয়)।
- অনুবাদ: কোডে ব্যবহৃত ৩১৬টি কী (`t("...")`, `I18n.get`, `common.min$it`) সবই EN-এ আছে।
  `Dictionaries` (২২২) ও `LiveDictionary` (১৬১)-এ EN ও BN কী হুবহু সমান, কোনো কী বাদ নেই।
- রিসোর্স: কোডে শুধু `R.drawable.brand_icon` ব্যবহার হয়েছে, ফাইলটি আছে। লঞ্চার আইকন (সব ঘনত্ব), adaptive ও monochrome আইকন, `file_paths.xml`, থিম (লাইট/ডার্ক) আছে।
- ম্যানিফেস্ট: INTERNET, ACCESS_NETWORK_STATE, FileProvider (`${applicationId}.fileprovider`) ঠিক আছে।
- বিল্ড কনফিগ: AGP 8.7.2, Kotlin 2.0.21, Gradle 8.9 (workflow-এ), compileSdk 35 পরস্পর সামঞ্জস্যপূর্ণ।
- ইউনিট টেস্ট যেসব ফাংশন ডাকে (`scoreSession`, `mergeHistories`, `makeTombstone`, `isTombstone`, `capHistory`, `MathParser`) সবই আছে এবং স্বাক্ষর মেলে। `ScoringTest`-এর প্রত্যাশিত মান (যেমন ২.৭৫ / ৫৫%) আমি হাতে হিসাব করে মিলিয়েছি।
- Delete account: অ্যাপ (`AuthManager.deleteAccount`) ও সার্ভার ফাংশন (`delete-account/index.ts`) যুক্তিতে মেলে।
- কোডে কোনো `TODO/FIXME` নেই।

## এখনও খোলা (আমি এখানে ঠিক করতে পারিনি)
1. **কম্পাইল হয়নি।** প্রথম GitHub Actions বিল্ডে লাল এরর আসতে পারে। লগ পাঠালে ঠিক করা যাবে।
2. **ফন্ট ফাইল নেই** (`app/src/main/assets/fonts/` খালি)। CI ডাউনলোড করে, ব্যর্থ হলে বিল্ড থামে।
   নিশ্চিন্ত থাকতে `Lato-Regular.ttf`, `Lato-Bold.ttf`, `NotoSerifBengali.ttf` নিজে ওই ফোল্ডারে কমিট করুন।
3. **Gradle wrapper (`gradlew`, `gradle-wrapper.jar`) নেই।** GitHub Actions-এ লাগে না (workflow নিজে Gradle 8.9 আনে)।
   Android Studio-তে খুললে নিজে Gradle কনফিগার করতে বলবে, বা `gradle wrapper --gradle-version 8.9` চালান।
4. **Release keystore, GitHub Secrets, Firebase release SHA-1/SHA-256, Play Console**: `README-step10.md`-এর ধাপ ১ থেকে ৭।
   এখন `google-services.json`-এ শুধু debug SHA-1 আছে, তাই release APK-তে Google Sign-In কাজ করবে না।
5. **`delete-account` Supabase ফাংশন deploy করতে হবে** (`web/supabase/functions/delete-account`), না হলে অ্যাপ ও ওয়েবের Delete account কাজ করবে না।
6. Config-এ ইমেইল `rifat.webflow@gmail.com` admin হিসেবে বসানো আছে; ঠিক আছে কি না দেখে নিন।
7. (সমাধান হয়েছে) Gemini মডেল তালিকা ২০ অক্টোবর ২০২৬-এর স্থিতিশীল মডেলে হালনাগাদ, বিস্তারিত `README-step10.md`-এর শেষে।
8. ধাপ ৯: "সব প্রশ্ন অফলাইনে নামান" ও সোয়াইপ-টু-ডিলিট (tombstone সহ) কোডে আগে থেকেই আছে (`README-step9b.md`)। শুধু ডিভাইসে পিক্সেল মিলানো বাকি, স্ক্রিনশট ছাড়া সম্ভব নয়।

## ওয়েবসাইট ফোল্ডার নিয়ে
`examcamp-main.zip` পুরনো ওয়েব সংস্করণ। `examcamp-all.zip`-এর `web/` ফোল্ডারে নতুন সংস্করণ আছে
(Delete account, `privacy.html`, Supabase ফাংশন, বড় `app.js`)। তাই এই জিপে `web/` রাখা হয়েছে, `main`-এর ফাইল আলাদা যোগ করিনি।

## প্রথম বিল্ড চালানোর উপায়
1. ফোল্ডারটি GitHub রিপোর রুটে পুশ করুন (main ব্রাঞ্চ)।
2. Actions → "Build debug APK" চলবে → Artifacts থেকে `examcamp-debug-apk` নামান।
3. লাল এরর এলে লগের শেষ অংশ পাঠান।
