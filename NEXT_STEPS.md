> **হালনাগাদ:** ধাপ ৭, ৮, ৯ (বাকি কাজসহ, `README-step9b.md`) হয়েছে। ধাপ ১০ (রিলিজ) কোড তৈরি, `README-step10.md`। আপনার করণীয়: keystore, GitHub Secrets, Firebase SHA, Play Console। বাকি: ডিভাইসে পিক্সেল মেলানো (স্ক্রিনশট দিলে করা যাবে)।

# ExamCamp Android: পরের ধাপগুলোর Readme

এই ফাইলটি ধাপ ৭ থেকে ধাপ ১০ পর্যন্ত কাজের নকশা ও ক্রম বলে দেয়। ধাপ ৬ পর্যন্ত কাজ হয়ে গেছে (Foundation, AI Exam, Math, Practice, Live Exam, History ও Statistics)।

---

## ধাপ ৭: Admin প্যানেল

ওয়েবসাইটের Admin প্যানেলে চারটি ট্যাব আছে: Courses, Questions, Results, Users। এছাড়া আলাদা Practice Admin আছে।

### কোথায় কী আছে (ওয়েবসাইট)
- Courses (`renderAdminSubjectList`): live exam campaign তৈরি, সম্পাদনা, প্রকাশ।
- Questions (`renderAdminQuestionBank`): live exam-এর প্রশ্ন ব্যাংক।
- Results (`renderAdminResultsList`): সাবমিশন দেখা ও ফলাফল প্রকাশ।
- Users (`renderAdminMemberList`): members টেবিল, role ও বিষয়ভিত্তিক অ্যাক্সেস।
- Enrollment requests (`renderAdminEnrollmentRequests`): অনুমোদন বা প্রত্যাখ্যান।
- Practice Admin (`renderPracticeAdminPanel`): ক্যাটাগরি, সাবজেক্ট, প্রশ্ন।

### অ্যাক্সেস নিয়ম
- Live Exam admin: `LIVE_EXAM_ADMIN_EMAILS` তালিকা অথবা `members.role` = admin/assistant (`isLiveExamAdmin`)।
- Practice admin: শুধু `PRACTICE_ADMIN_EMAILS`-এর ইমেইল (`isPracticeAdmin`), কোনো assistant tier নেই।
- Android-এ এই যাচাই UI-তে দেখানোর আগে এবং সার্ভারে (RLS / Edge Function) দুই জায়গাতেই হওয়া উচিত।

### ডাটা ও API
- টেবিল: `live_exams`, `live_exam_submissions`, `enrollment_requests`, `members`, `subjects`।
- প্রশ্ন আপলোড: Edge Function `clever-service` (PUT `/live-exam/{id}/questions` এবং Practice bank PUT) , Firebase ID token সহ।
- Realtime চ্যানেল `live-exam-changes` আগে থেকেই আছে, admin পরিবর্তনও একই চ্যানেলে আসবে।

### Android-এ প্রস্তাবিত ফাইল
- `ui/screens/admin/AdminScreen.kt` (ট্যাব কাঠামো)
- `ui/screens/admin/CoursesAdmin.kt`, `QuestionsAdmin.kt`, `ResultsAdmin.kt`, `UsersAdmin.kt`, `EnrollmentsAdmin.kt`
- `ui/screens/admin/PracticeAdmin.kt`
- `data/AdminApi.kt`, `data/AdminEngine.kt` (অ্যাক্সেস গণনা ও ডাটা লোড)

### আগে যা জানা দরকার
- Question upload-এর JSON ফরম্যাট (ওয়েবসাইটের `renderAdminQuestionBank` থেকে নিশ্চিত করতে হবে)।
- Edge Function-এর admin এন্ডপয়েন্টের নাম ও শর্ত।
- Admin-এর কাজ অ্যাপে রাখা হবে কিনা। README-plan-এ এটি হ্যাঁ ধরা হয়েছে, তবে AGQ প্যানেল বাদ।

### গ্রহণযোগ্যতার মাপকাঠি
- হোস্ট ইমেইল দিয়ে সাইন ইন করলে Admin আইকন দেখা যায়, অন্যদের দেখা যায় না।
- নতুন campaign তৈরি করে publish করলে ওয়েবসাইটে ও অ্যাপে একই সময়ে দেখা যায়।
- enrollment অনুমোদন দিলে সদস্যের `members.subjects` আপডেট হয় এবং সদস্য Join করতে পারে।
- ফলাফল প্রকাশ করলে Merit ও History-তে দেখা যায়।

---

## ধাপ ৮: PDF, শেয়ার ও এক্সপোর্ট

### কী করতে হবে
- ফলাফল PDF: স্কোর, প্রতিটি প্রশ্ন, সঠিক উত্তর, ব্যাখ্যা।
- প্রশ্নপত্র PDF: AI বা Practice-এর প্রশ্ন।
- ছবি হিসেবে শেয়ার: ফলাফল কার্ড বিটম্যাপ, Android share sheet দিয়ে।

### ওয়েবসাইটের সাথে মিল
- ওয়েবসাইট jsPDF ও html2canvas ব্যবহার করে। Android-এ `android.graphics.pdf.PdfDocument` এবং Compose-এর `drawToBitmap` দিয়ে সমতুল্য কাজ করা হবে।
- কাগজের বিন্যাস (A4, মার্জিন, ফন্ট) ওয়েবসাইটের আউটপুটের সাথে মিলিয়ে দেখতে হবে।

### Android-এ প্রস্তাবিত ফাইল
- `util/PdfExporter.kt`
- `util/ShareHelper.kt` (FileProvider সহ)
- `res/xml/file_paths.xml` এবং AndroidManifest-এ FileProvider

### আগে যা জানা দরকার
- বাংলা ফন্ট (Kalpurush) PDF-এ এম্বেড করতে হবে। ফন্ট ফাইল `assets/fonts/` এ রাখতে হবে।
- KaTeX সূত্র PDF-এ কীভাবে আসবে। ছবি হিসেবে রেন্ডার করে বসানো সহজ।

### গ্রহণযোগ্যতার মাপকাঠি
- বাংলা ও ইংরেজি মিশ্র টেক্সট PDF-এ ঠিকভাবে দেখায়।
- শেয়ার করা PDF/ছবি WhatsApp ও Gmail-এ খোলে।

---

## ধাপ ৯: অফলাইন, গতি ও পালিশ

### কী করতে হবে
- অফলাইন ক্যাশ: exam history, practice progress, live exam schedule, question bank। Room বা DataStore।
- সিঙ্ক কিউ: অফলাইনে করা পরিবর্তন (practice progress, exam history, enrollment draft) অনলাইনে এলে পাঠানো। Live result queue ইতিমধ্যে আছে (`live_pending_results`), একই ধাঁচে বাকিগুলো করা হবে।
- লোডিং স্টেট: Skeleton ও progress indicator।
- অ্যানিমেশন ও ট্রানজিশন: স্ক্রিন বদল, accordion, bar chart (ইতিমধ্যে আছে)।
- Swipe: Practice ও History-তে সোয়াইপ-টু-ডিলিট বা ট্যাব সোয়াইপ।
- ট্যাবলেট ও ছোট স্ক্রিন: `WindowSizeClass` ভিত্তিক লেআউট। Practice grid ইতিমধ্যে ডায়নামিক কলাম ব্যবহার করে।
- পিক্সেল মিলানো: ওয়েবসাইট ও অ্যাপ পাশাপাশি রেখে প্রতিটি স্ক্রিন মেলানো।

### আগে যা জানা দরকার
- Room ব্যবহার করা হবে নাকি শুধু DataStore যথেষ্ট, সিদ্ধান্ত নিতে হবে। Exam history বড় হলে Room ভালো।
- Network state লিসেনার (ConnectivityManager)।

### গ্রহণযোগ্যতার মাপকাঠি
- বিমানে মোড চালু করে পুরোনো পরীক্ষার ইতিহাস ও Practice প্রগ্রেস দেখা যায়।
- অফলাইনে করা practice উত্তর অনলাইনে ফিরলে Supabase-এ যায়।
- ছোট ফোনে (৫") কোনো স্ক্রিন ভাঙে না।

---

## ধাপ ১০: রিলিজ

### কী করতে হবে
- রিলিজ keystore তৈরি ও GitHub Secrets-এ সংরক্ষণ (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)।
- Signed APK ও AAB বিল্ড: GitHub Actions-এ release workflow।
- অ্যাপ আইকন: ওয়েবসাইটের `webapp_icon/icon-512.png` থেকে launcher icon (mipmap সব আকার), adaptive icon (foreground + background)।
- ProGuard / R8: Kotlin serialization, OkHttp, Firebase-এর keep rules।
- APK সাইজ কমানো: শুধু প্রয়োজনীয় font, অপ্রয়োজনীয় resource বাদ।
- (ঐচ্ছিক) Play Store: privacy policy, screenshot, বর্ণনা, data safety ফর্ম।

### আগে যা জানা দরকার
- Play Store-এর জন্য developer অ্যাকাউন্ট ও পেমেন্ট (যদি চান)।
- অ্যাপের নাম ও package name চূড়ান্ত কিনা (`com.examcamp.app`)।
- Firebase-এ release SHA-1 ও SHA-256 যোগ করা (Google Sign-In-এর জন্য)।

### গ্রহণযোগ্যতার মাপকাঠি
- GitHub Actions থেকে signed AAB তৈরি হয়।
- Release APK ফোনে ইনস্টল হয় এবং Google Sign-In কাজ করে।
- Obfuscated build-এ কোনো crash নেই (সব প্রধান ফ্লো পরীক্ষা)।

---

## ক্রম ও নির্ভরতা

1. ধাপ ৭ (Admin) এর আগে বিল্ড যাচাই ও ধাপ ৫-৬-এর বাগ ফিক্স।
2. ধাপ ৭ নির্ভর করে Edge Function-এর admin এন্ডপয়েন্টের উপর।
3. ধাপ ৮ আলাদাভাবে করা যায়, শুধু ফলাফল ডাটা লাগে।
4. ধাপ ৯ শেষের দিকে, কারণ এটি সব স্ক্রিনের উপর প্রভাব ফেলে।
5. ধাপ ১০ সবার শেষে।

## প্রতিটি ধাপে যা পাবেন
- আপডেট করা পুরো প্রজেক্ট ZIP
- এই ধরনের ছোট README (কী বদলেছে, কোন ফাইল কোথায়)
- GitHub Actions থেকে APK ডাউনলোডের নির্দেশ

## আপনার কাছে যা দরকার
- সর্বশেষ বিল্ড লগের লাল error (থাকলে)
- Admin এন্ডপয়েন্টের নাম ও প্রশ্ন আপলোডের JSON (ধাপ ৭-এর আগে)
- ধাপ ৯-এ Room ব্যবহার করবেন কিনা
- ধাপ ১০-এর আগে Play Store পরিকল্পনা আছে কিনা
