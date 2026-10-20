# ExamCamp Android: ধাপ ৫ ও ৬ (Live Exam, History, Statistics)

## এই অংশে যা যোগ হয়েছে
- `data/LiveExamModels.kt`: Subject, Live Exam, Member, Merit, Enrollment ও JoinResult মডেল।
- `data/LiveExamApi.kt`: Supabase টেবিল থেকে পড়া ও লেখা (`subjects`, `live_exams`, `members`,
  `live_exam_submissions`, `enrollment_requests`), একই anon key ও RLS যা ওয়েবসাইট ব্যবহার করে।
  প্রশ্ন আনা হয় Worker-এর `/live-exam/{id}/questions` রুট থেকে, Firebase ID token সহ।
- `data/LiveExamEngine.kt`: অ্যাক্সেস নিয়ম, মেধাতালিকা (শুধু published পরীক্ষা), নিজের Rank/Given/Avg,
  Join যাচাই (ওয়েবসাইটের ক্রমে), ফলাফল জমা (upsert), এনরোলমেন্ট জমা।
- `ExamModels.kt`: `Exam.liveExamId`, `Exam.timeLimitMs`, `Exam.limitMs`, `Session.needsLiveAnswers`।
- `ExamEngine.kt`:
  - সক্রিয় Live Exam সংরক্ষণের সময় correct answer বাদ থাকে (ওয়েবসাইটের `stripLiveExamAnswersForStorage` এর মতো)।
  - অ্যাপ রিস্টার্টের পর উত্তরের চাবি Worker থেকে ফিরিয়ে আনা হয় (`hydrateLive`)।
  - `submit()` এখন `Boolean` ফেরত দেয়। চাবি না আসা পর্যন্ত জমা হয় না।
  - টাইমার শেয়ার্ড deadline মেনে চলে (`limitMs`)।
- `AppViewModel.kt`: `live` engine, `submitExam()`, `startLiveExam()`। Auth বদলালে ডাটা রিফ্রেশ হয়।
- `AppRoot.kt`: Submit ও Time-up এখন `vm.submitExam()` দিয়ে যায়।
- `ExamScreen.kt`: টাইমার `exam.limitMs` ব্যবহার করে।
- `Config.kt`: `LIVE_EXAM_ADMIN_EMAILS` (ওয়েবসাইটের মতো)।
- `SupabaseApi.parseIso` এখন public।

## অংশ ২ (এই আপডেটে যোগ হয়েছে)
- `ui/screens/LiveExamScreen.kt`: Live Exam পেজ।
  - Campaign কার্ড: লাইভ কাউন্টডাউন (দিন/ঘণ্টা/মিনিট/সেকেন্ড), মেটা তথ্য, Join / Enroll / Submitted বাটন।
  - Summary: Central Merit (Rank), Exams Given, Course Avg।
  - Tabs: Routine (বিষয়ভিত্তিক accordion, Live/Upcoming/Ended বাদ্জ), Subjects (Enrolled বা Enroll Now), Merit (নিজের সারি হাইলাইট)।
  - Enroll Now শিট: নাম, ফোন, ইমেইল, কোর্স বাছাই, MFS প্রদানকারী (EcDropdown), Transaction ID। ওয়েবসাইটের একই যাচাই।
- `i18n/LiveDictionary.kt`: Android-only Live Exam টেক্সট (EN ও BN)। `I18n.get` এখন এটি দেখে।
- `LiveExamEngine.members` এখন public flow, যাতে অ্যাক্সেস বদলালে UI আপডেট হয়।
- `AppRoot.kt`: Home-এর "Live Exam" কার্ড এখন Live স্ক্রিনে যায়। Join-এর ফলাফল (Ready / NotEnrolled / NotLive / AlreadySubmitted / NoQuestions / Failed) টোস্টসহ হ্যান্ডেল হয়।
- ডাটা রিফ্রেশ: স্ক্রিন খুললে ও প্রতি ৩০ সেকেন্ডে। এটি realtime-এর বদলে polling।

## পূর্বের অপূর্ণতার অবস্থা
- এনরোলমেন্ট খসড়া: এখন ডিভাইসে সংরক্ষিত হয় (`live_enroll_draft`)। ফর্ম খুললে আগের তথ্য ফিরে আসে। সফল জমার পর মুছে যায়।
- Realtime: ওয়েবসাইটের একই channel (`live-exam-changes`) ব্যবহার করা হয়েছে। পাঁচটি টেবিলের পরিবর্তনে ডাটা রিলোড হয়, heartbeat প্রতি ২৫ সেকেন্ডে, ব্যর্থ হলে backoff-সহ পুনরায় সংযোগ। স্ক্রিন বন্ধ হলে সংযোগ বন্ধ হয়। ৬০ সেকেন্ডের রিলোড নিরাপত্তা হিসেবে থাকে।
- ফলাফল জমা: ব্যর্থ হলে `live_pending_results`-এ জমা থাকে এবং পরের রিলোড বা অ্যাপ চালুতে আবার চেষ্টা হয়।
- কম্পাইল: Android SDK ছাড়া যাচাই হয়নি। GitHub Actions বিল্ড সবচেয়ে বড় যাচাই।

## Schema যাচাই (গুরুত্বপূর্ণ)
কোডের কলাম নাম ওয়েবসাইটের `app.js` থেকে নেওয়া। আপনার Supabase-এ এই SQL চালিয়ে ফলাফল পাঠান,
যাতে কলামের নাম ও ধরন হুবহু মেলানো যায়:

```sql
select table_name, column_name, data_type
from information_schema.columns
where table_schema = 'public'
  and table_name in ('subjects','live_exams','members','enrollment_requests','live_exam_submissions')
order by table_name, ordinal_position;
```

## জানা সীমাবদ্ধতা
- Android SDK এই পরিবেশে নেই, তাই কোড কম্পাইল হয়নি। GitHub Actions-এর লাল এরর থাকলে পাঠান।
- ফলাফল জমা ব্যর্থ হলে ওয়েবসাইটের মতো শুধু লগ হয়। পুনরায় চেষ্টার queue নেই।
- প্রশ্নের HTML (`question_html`) ওয়েবসাইটের মতো সরাসরি টেক্সট হিসেবে পড়া হয়। Step 3-এর MathText যতটুকু পারে দেখায়।
- পরীক্ষা শেষ হওয়ার পর Worker প্রশ্ন দেবে না। তাই শেষ হওয়া, কিন্তু রিস্টার্ট হওয়া অসম্পূর্ণ সেশনের উত্তর জমা হবে না।


## ধাপ ৬: History ও Statistics (এই আপডেটে)
- `data/Records.kt`: exam_history থেকে Practice সারি, Live ফলাফল (শুধু প্রকাশিত ও শেষ হওয়া পরীক্ষা, নিজের স্কোরসহ), overview গণনা এবং রেকর্ড থেকে Session ফেরত আনা।
- `LiveExamEngine.myScores`: নিজের স্কোর প্রতি পরীক্ষায় (ওয়েবসাইটের liveExamOwnScores)। merit-এর গড় নয়।
- `AppViewModel.examHistory`: exam_history-এর কাঁচা তালিকা।
- `ui/screens/HistoryStatsScreens.kt`: History (Live ও Practice ট্যাব) ও Statistics (Practice ও Live overview, সাম্প্রতিক ৫টি live ফলাফল)।
- Practice সম্পন্ন রেকর্ডে ক্লিক করলে ফলাফল স্ক্রিন খোলে (ResultsScreen)।
- Home-এ "History" কার্ড যোগ হয়েছে। Statistics ও Home-এর quick stats এখন সরাসরি স্ক্রিনে যায়।

### এখনও বাকি (ধাপ ৬, অংশ ২)
- (সমাধান হয়েছে) Statistics-এর BCS Preliminary চার্ট (pie ও বিষয়-অনুযায়ী bar চার্ট) এবং ক্যাটাগরি ট্যাব। এটি practice progress-এর ডাটা থেকে তৈরি হয়। এই অংশে বাদ আছে।
- Practice রেকর্ড খোলা হলে ওয়েবসাইটের মতো বিস্তারিত analytics (enterStatistics) নয়, এখন সাধারণ ফলাফল স্ক্রিন দেখায়।
- ফলাফল স্ক্রিনের "Home" বোতাম এখন History-তে ফেরত দেয়।
- কম্পাইল যাচাই হয়নি। GitHub Actions বিল্ড লগ দেখুন।


## ধাপ ৭: Admin প্যানেল (Live Exam অংশ)
- `data/AdminEngine.kt`: সব admin লেখা (বিষয়, পরীক্ষা, প্রকাশ, সদস্য, এনরোলমেন্ট অনুমোদন/বাতিল, প্রশ্ন সংরক্ষণ) এবং প্রশ্ন JSON parser (`parseQuestionBank`)।
- `data/LiveExamApi.kt`: admin লেখার API (subjects, live_exams, members, enrollment_requests) এবং `putQuestions` (Worker-এর admin PUT রুট, Firebase ID token সহ)।
- `ui/screens/admin/AdminScreen.kt`: Courses (বিষয় ও পরীক্ষা যোগ/সম্পাদনা/মুছা, প্রকাশ সুইচ), Results (বিষয়ভিত্তিক প্রকাশ, প্রতি পরীক্ষার স্কোর), Enrollments (অনুমোদন/বাতিল)।
- `ui/screens/admin/AdminUsersQuestions.kt`: Users (সদস্য যোগ/সম্পাদনা/মুছা, role ও বিষয় বরাদ্দ), Questions (JSON পেস্ট করে প্রশ্ন সংরক্ষণ)।
- Home-এ "Admin" কার্ড শুধু admin-দের দেখা যায় (`isLiveAdmin`)। অ-admin সরাসরি রুটে যেতে পারে না।
- `LiveExamEngine.allSubmissions`: সব সদস্যের সব সাবমিশন (Results-এর জন্য)।
- Localization: `I18n.get(lang, key)` ব্যবহার, কারণ click ও coroutine lambda-র ভেতরে composable `t()` কল করা যায় না।

### ধাপ ৭-এর সীমাবদ্ধতা
- Practice Admin (ক্যাটাগরি, সাবজেক্ট, প্রশ্ন) এখনও নেই। ধাপ ৭-এর এই অংশে শুধু Live Exam admin।
- প্রশ্ন তৈরি: ওয়েবসাইটের builder (AI বা PDF থেকে) নেই। এখানে JSON পেস্ট করতে হয়।
- পরীক্ষার শুরুর সময় স্থানীয় সময়ে টাইপ করতে হয় (`yyyy-MM-dd HH:mm`)। ওয়েবসাইটে date picker আছে।
- সদস্যের phone ফিল্ড অ্যাপে এখনও দেখানো হয় না। আপডেটে phone বাদ যায় না, কারণ শুধু পাঠানো কলাম আপডেট হয়।
- Admin লেখা anon key দিয়ে করা হয়, ওয়েবসাইটের মতো। সার্ভারে RLS যাচাই আপনার কাজ। প্রশ্ন সংরক্ষণ Worker-এর Firebase যাচাইয়ের উপর নির্ভর করে।
- কম্পাইল যাচাই হয়নি। GitHub Actions বিল্ড লগ দেখুন।


## ধাপ ৭: Practice Admin
- `data/PracticeApi.kt`: admin লেখা: `putManifest` (পুরো ক্যাটাগরি/বিষয়/টপিক গাছ), `putTopicQuestions` (টপিকের প্রশ্ন ব্যাংক), `deleteTopicQuestions`। সব Firebase ID token সহ।
- `data/PracticeAdminEngine.kt`: `PracticeTree` (ক্যাটাগরি/বিষয়/টপিক সম্পাদনার বিশুদ্ধ ফাংশন), `parsePracticeQuestions` (JSON পার্স, আগের প্রশ্নের ID রেখে দেয় যাতে সংরক্ষিত প্রগ্রেস না হারায়)।
- `ui/screens/admin/PracticeAdminPanel.kt`: ক্যাটাগরি → বিষয় → টপিক → প্রশ্ন, ধাপে ধাপে যাওয়া। প্রতিটি পরিবর্তন পুরো গাছ সহ সংরক্ষণ হয়, ব্যর্থ হলে আগের অবস্থায় ফেরে।
- Admin স্ক্রিনে "Practice" ট্যাব শুধু `PRACTICE_ADMIN_EMAILS`-এর ইমেইলের জন্য দেখা যায় (এখন একজন)।

### ধাপ ৭ (Practice) এর সীমাবদ্ধতা
- ক্যাটাগরির আইকন শুধু ইমোজি। ওয়েবসাইটের ছবি আপলোড নেই।
- Main topic (`mainTopics`) ও subtopic সম্পাদনা নেই। বিদ্যমান মান অপরিবর্তিত থাকে, তবে নতুন তৈরি করা যায় না।
- প্রশ্ন JSON পেস্ট করে সংরক্ষণ। যদি প্রশ্নের সংখ্যা কমে বা ক্রম বদলায়, ID অবস্থান অনুযায়ী বরাদ্দ হয়, তাই কিছু প্রগ্রেস ভুল প্রশ্নে যেতে পারে। সংরক্ষণের আগে ক্রম ঠিক রাখুন।
- Practice Admin-এর সব লেখা Firebase token সহ Worker-এ যায়। সঠিক admin email ছাড়া Worker লেখা প্রত্যাখ্যান করবে।
- কম্পাইল যাচাই হয়নি। GitHub Actions বিল্ড লগ দেখুন।


## ধাপ ৮: PDF, শেয়ার ও এক্সপোর্ট
- `util/PdfExporter.kt`: `android.graphics.pdf.PdfDocument` দিয়ে সরাসরি টেক্সট আঁকা PDF (ছবি নয়, তাই তীক্ষ্ণ ও ছোট)।
  - `questionPaper`: প্রশ্নপত্র, শেষ পৃষ্ঠায় উত্তরপত্র ও ব্যাখ্যা।
  - `resultReport`: স্কোর, সঠিক/ভুল/উত্তর-না-দেওয়া সংখ্যা, প্রতিটি প্রশ্নে আপনার উত্তর ও সঠিক উত্তর।
  - A4, Letter, Legal; 10 pt; line height 1.3; অপশন ক/খ/গ/ঘ (বাংলা বা ইংরেজি-বাংলা) বা A/B/C/D, ওয়েবসাইটের মতো।
  - পৃষ্ঠা আপনাআপনি ভাগ হয়।
- `util/ShareHelper.kt`: PDF ও PNG শেয়ার (FileProvider, `cache/exports`)। `resultCard` ফলাফলের একটি 1080 x 1350 কার্ড আঁকে।
- ফলাফল স্ক্রিনে (`ExportRow`) তিনটি বোতাম: ফলাফল PDF, প্রশ্নপত্র PDF, ছবি হিসেবে শেয়ার। ইতিহাসের রিভিউতেও একই।
- `res/xml/file_paths.xml`: `exports/` পাথ যোগ।
- স্ট্রিং: `export.*` ও `pdf.*` কী, EN ও BN।

### ধাপ ৮-এর সীমাবদ্ধতা
- ফাইল শেয়ার শিটে যায়। "Files" বা Drive-এ সংরক্ষণ করে নিতে হয়। সরাসরি Downloads ফোল্ডারে লেখা হয় না।
- KaTeX-এর গণিত সূত্র PDF-এ `$...$` হিসেবে আসে, রেন্ডার হয় না। ওয়েবসাইটে এগুলো KaTeX দিয়ে আঁকা হয়।
- বাংলা অক্ষরের জন্য Android-এর সিস্টেম ফন্ট ব্যবহার হয় (Noto Sans/Serif Bengali)। ওয়েবসাইটের Noto Serif Bengali এম্বেড করা হয়নি। ফন্ট `assets` এ যোগ করলে মিল বাড়বে।
- Times New Roman ওয়েবসাইটের ডিফল্ট, কিন্তু Android-এ নেই, তাই Serif ব্যবহার হয়।
- কম্পাইল ও PDF-এর চেহারা যাচাই হয়নি। GitHub Actions বিল্ডের পর একটি PDF খুলে দেখুন।

---
## ফোল্ডার
- রুট: Android অ্যাপ (GitHub-এ পুশ করলেই `.github/workflows/build.yml` চলে)।
- `web/`: ওয়েবসাইট (Delete account সহ) ও `web/supabase/functions/delete-account`।
- নোট: `README-parity-step.md`।
