# Play Store তথ্য (Play Console-এ কপি-পেস্ট করার জন্য)

## মৌলিক
- অ্যাপের নাম: Examcamp
- Package: `com.examcamp.app` (একবার Play-তে আপলোড হলে আর বদলানো যায় না)
- ক্যাটাগরি: Education
- Privacy policy URL: `<আপনার ওয়েবসাইট>/privacy.html` (ওয়েবসাইট রিপোতে `privacy.html` পুশ করুন, তারপর লিংকটি খুলে দেখুন। ফাইলে `YOUR_CONTACT_EMAIL` বদলে আপনার ইমেইল দিন)
- যোগাযোগ ইমেইল: আপনার ইমেইল

## ছোট বর্ণনা (৮০ অক্ষর)
EN: AI-powered MCQ exams, practice banks and live exams in English and Bengali.
BN: AI দিয়ে MCQ পরীক্ষা, প্র্যাকটিস ও লাইভ এক্সাম — বাংলা ও ইংরেজিতে।

## পূর্ণ বর্ণনা (EN)
Examcamp helps you prepare for exams with MCQs in English and Bengali.

• AI Mode – describe a topic, or attach a photo or PDF, and generate a full MCQ exam with your own Gemini API key.
• Practice – browse category and subject question banks and track your progress per topic.
• Live Exams – join scheduled exams, see the routine, results and merit list.
• History and Statistics – review every attempt with explanations and see how you improve.
• Works offline – history, progress and previously opened practice topics stay available without internet, and changes sync when you are back online.
• Math-friendly – formulas display properly.
• Share and export – export results or question papers as PDF, or share a result card as an image.
• Light and dark themes, adjustable text size.

## পূর্ণ বর্ণনা (BN)
Examcamp দিয়ে বাংলা ও ইংরেজিতে MCQ পরীক্ষার প্রস্তুতি নিন।

• AI Mode – টপিক লিখুন বা ছবি/PDF দিন, নিজের Gemini API key দিয়ে পূর্ণ MCQ পরীক্ষা তৈরি করুন।
• Practice – ক্যাটাগরি ও বিষয়ভিত্তিক প্রশ্নব্যাংক, টপিকওয়ারি অগ্রগতি।
• Live Exam – নির্ধারিত পরীক্ষায় অংশ নিন, রুটিন, ফলাফল ও মেধাতালিকা দেখুন।
• History ও Statistics – প্রতিটি পরীক্ষা ব্যাখ্যাসহ দেখুন, উন্নতি মাপুন।
• অফলাইনেও চলে – ইতিহাস ও অগ্রগতি ইন্টারনেট ছাড়াই দেখা যায়, অনলাইনে এলে সিঙ্ক হয়।
• গণিতের সূত্র সুন্দরভাবে দেখায়।
• PDF এক্সপোর্ট ও ছবি হিসেবে ফলাফল শেয়ার।
• লাইট/ডার্ক থিম ও লেখার আকার বদলানো যায়।

## গ্রাফিক্স (আপনাকে বানাতে হবে)
- আইকন 512×512: `webapp_icon/icon-512.png` (ওয়েবসাইটে আছে)
- Feature graphic 1024×500 (বাধ্যতামূলক)
- ফোনের স্ক্রিনশট ২–৮টি (Home, AI Mode, Exam, Result, History/Statistics, Practice, Live Exam, Dark mode)

## Data safety ফর্মের উত্তর
- Data collected: হ্যাঁ। Data shared: হ্যাঁ (নিচের পরিষেবাদাতা ছাড়া অন্য কাউকে নয়)।
- Encrypted in transit: হ্যাঁ (HTTPS)।
- Users can request deletion: হ্যাঁ (ইমেইলে। Play-তে "delete data URL" চাইলে privacy policy-এর লিংকই দিন)।

| ডাটা ধরন | সংগ্রহ | কেন | ঐচ্ছিক? |
|---|---|---|---|
| Name, Email address | হ্যাঁ | App functionality, Account management | সাইন-ইন ঐচ্ছিক (Guest মোড আছে) |
| User IDs | হ্যাঁ | Account management | সাইন-ইনের সাথে |
| Photos / Files (ছবি, PDF) | না সংরক্ষণ হয় না | শুধু প্রশ্ন তৈরির অনুরোধে Gemini-তে যায় (ephemeral) | হ্যাঁ |
| App activity (exam history, progress) | হ্যাঁ | App functionality | সাইন-ইনের সাথে |
| Other user-generated content (Gemini API key, settings) | হ্যাঁ | App functionality | হ্যাঁ |
| Location, Contacts, Financial, Health, Device IDs, Ads | না | | |

- Ads: না। Advertising ID: ব্যবহার হয় না।
- Target audience: ১৩+ (শিক্ষার্থী)। Children's program-এ দেবেন না।
- Content rating: Everyone / কোনো সংবেদনশীল কনটেন্ট নেই।
- Account deletion (Play-র নিয়ম): অ্যাপে সাইন-ইন আছে বলে "account deletion" URL লাগে। সহজ উপায়: privacy policy-র ইমেইল-অনুরোধ অংশই দিন, অথবা পরে অ্যাপে "Delete account" বাটন যোগ করুন।

## Release notes (প্রথম সংস্করণ)
EN: First release of Examcamp for Android.
BN: Examcamp Android অ্যাপের প্রথম সংস্করণ।

## নতুন ডেভেলপার অ্যাকাউন্টের নিয়ম (২০২৩-এর পর খোলা ব্যক্তিগত অ্যাকাউন্ট)
Production-এ যাওয়ার আগে ১২ জন টেস্টার ১৪ দিন Closed testing-এ অ্যাপ অপ্ট-ইন রাখতে হয়। আগে থেকে টেস্টারদের জোগাড় করুন।
