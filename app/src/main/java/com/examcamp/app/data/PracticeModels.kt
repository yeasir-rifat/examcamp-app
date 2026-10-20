package com.examcamp.app.data

import kotlinx.serialization.Serializable

/**
 * Practice tree, as served by GET /practice/manifest (see fetchPracticeManifest in app.js):
 * categories → subjects → (optional main topics) → topics. Question content is NOT here;
 * it lives per topic (see PracticeApi.fetchTopicQuestions).
 */
@Serializable
data class PracticeMainTopic(val id: String, val name: String)

@Serializable
data class PracticeTopic(
    val id: String,
    val name: String,
    /** "en" | "bn": decides ক/খ/গ/ঘ markers and Bengali numerals for this topic. */
    val language: String = "en",
    val mainTopicId: String? = null,
)

@Serializable
data class PracticeSubject(
    val id: String,
    val name: String,
    val mainTopics: List<PracticeMainTopic> = emptyList(),
    val topics: List<PracticeTopic> = emptyList(),
)

@Serializable
data class PracticeCategory(
    val id: String,
    val name: String,
    /** An emoji, or a "data:image/..." URI uploaded by the admin. */
    val icon: String = "",
    val description: String = "",
    val subjects: List<PracticeSubject> = emptyList(),
)

/** One practice-bank question ({id, question, options, correctAnswer, explanation, subtopic?}). */
@Serializable
data class PracticeQuestion(
    val id: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctAnswer: Int = 0,
    val explanation: String = "",
    val subtopic: String = "",
)

/**
 * Saved progress for one topic (practiceState.progress[subjectId::topicId] on the website,
 * public.practice_progress row in Supabase): answers keyed by question id.
 */
@Serializable
data class PracticeEntry(
    val answers: Map<String, Int> = emptyMap(),
    val current: Int = 0,
    val answeredCount: Int = 0,
    val updatedAt: Long = 0,
)

fun practiceKey(subjectId: String, topicId: String) = "$subjectId::$topicId"

/** A running Practice Mode session (practiceModeState on the website). */
data class PracticeRun(
    val subjectId: String,
    val topicId: String,
    /** Non-null: a subtopic-only run, kept in memory and never saved as topic progress. */
    val subtopic: String?,
    val subjectName: String,
    val topicName: String,
    val language: String,
    val questions: List<PracticeQuestion>,
    val answers: Map<String, Int>,
    val current: Int,
) {
    val right: Int get() = questions.count { answers[it.id] == it.correctAnswer && answers[it.id] != null }
    val wrong: Int get() = questions.count { answers[it.id] != null && answers[it.id] != it.correctAnswer }
}

enum class PracticeOpenError { NOT_FOUND, LOAD_FAILED, EMPTY }

/** Built-in tree used before the first successful manifest download (defaultPracticeCategories in app.js). */
object PracticeDefaults {
    private fun topic(id: String, name: String) = PracticeTopic(id, name)
    private fun subject(id: String, name: String, vararg topics: PracticeTopic) =
        PracticeSubject(id, name, emptyList(), topics.toList())

    fun categories(): List<PracticeCategory> = listOf(
        PracticeCategory(
            id = "bcs-prelim",
            name = "বিসিএস প্রিলিমিনারী",
            icon = "🏛️",
            subjects = listOf(
                subject("bn-lit", "বাংলা সাহিত্য", topic("bn-lit-t1", "প্রাচীন ও মধ্যযুগের সাহিত্য"), topic("bn-lit-t2", "আধুনিক যুগের সাহিত্য")),
                subject("bn-gram", "বাংলা ব্যাকরণ", topic("bn-gram-sandhi", "সন্ধি"), topic("bn-gram-karok", "কারক ও বিভক্তি")),
                subject("en-lit", "English Literature", topic("en-lit-t1", "Poetry"), topic("en-lit-t2", "Prose & Drama")),
                subject("en-gram", "English Grammar", topic("en-gram-tense", "Tense"), topic("en-gram-voice", "Voice & Narration")),
                subject("bd-affairs", "বাংলাদেশ বিষয়াবলী", topic("bd-affairs-history", "মুক্তিযুদ্ধ ও ইতিহাস"), topic("bd-affairs-economy", "অর্থনীতি ও উন্নয়ন")),
                subject("intl-affairs", "আন্তর্জাতিক বিষয়াবলী", topic("intl-affairs-org", "আন্তর্জাতিক সংস্থা"), topic("intl-affairs-events", "বিশ্ব রাজনীতি ও ঘটনাবলী")),
                subject("geo-env", "ভূগোল, পরিবেশ ও দূর্যোগ ব্যবস্থাপনা", topic("geo-env-world", "বিশ্ব ভূগোল"), topic("geo-env-disaster", "দূর্যোগ ব্যবস্থাপনা")),
                subject("gen-sci", "সাধারণ বিজ্ঞান", topic("gen-sci-physics", "পদার্থবিজ্ঞান"), topic("gen-sci-biology", "জীববিজ্ঞান")),
                subject("ict", "কম্পিউটার ও তথ্য প্রযুক্তি", topic("ict-basics", "কম্পিউটার মৌলিক ধারণা"), topic("ict-network", "নেটওয়ার্ক ও ইন্টারনেট")),
                subject("math-reason", "গাণিতিক যুক্তি", topic("math-reason-arith", "পাটিগণিত"), topic("math-reason-algebra", "বীজগণিত")),
                subject("mental-skill", "মানসিক দক্ষতা", topic("mental-skill-logic", "যৌক্তিক বিশ্লেষণ"), topic("mental-skill-analytical", "বিশ্লেষণধর্মী দক্ষতা")),
                subject("ethics-gov", "নৈতিকতা মূল্যবোধ ও সুশাসন", topic("ethics-gov-values", "মূল্যবোধ ও নৈতিকতা"), topic("ethics-gov-governance", "সুশাসন")),
            ),
        ),
    )
}
