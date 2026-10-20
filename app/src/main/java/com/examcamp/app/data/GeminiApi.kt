package com.examcamp.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** A question as the model returned it, before ids are assigned. */
data class RawQuestion(val question: String, val options: List<String>, val correctAnswer: Int, val explanation: String)

/** base64 payload of an attachment, ready for Gemini's inlineData. */
data class InlinePart(val mimeType: String, val base64: String)

class GeminiException(message: String) : Exception(message)

/** Port of the website's Gemini flow: buildGeminiPrompt, generateQuestionsWithGemini, repairAndParseGeminiJson. */
object GeminiApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val media = "application/json; charset=utf-8".toMediaType()

    /** Mime types Gemini's inlineData accepts; Word/PowerPoint are skipped like on the website. */
    val SUPPORTED_MIME = setOf("image/png", "image/jpeg", "image/webp", "image/heic", "image/heif", "application/pdf")

    suspend fun generate(config: ExamConfig, apiKey: String, model: String, parts: List<InlinePart>): List<RawQuestion> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw GeminiException("NO_API_KEY")
            val modelId = GeminiModels.resolve(model)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
            val body = buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", buildPrompt(config)) })
                            parts.forEach { p ->
                                add(buildJsonObject {
                                    put("inlineData", buildJsonObject {
                                        put("mimeType", p.mimeType)
                                        put("data", p.base64)
                                    })
                                })
                            }
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.7)
                })
            }.toString()

            // The key goes in a header, not the URL, so it never lands in logs or crash reports.
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .post(body.toRequestBody(media))
                .build()

            val (text, finishReason) = try {
                client.newCall(request).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw GeminiException("GEMINI_HTTP_${resp.code}: ${raw.take(200)}")
                    val root = json.parseToJsonElement(raw).jsonObject
                    val cand = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    val reason = cand?.get("finishReason")?.jsonPrimitive?.contentOrNull
                    val out = cand?.get("content")?.jsonObject?.get("parts")?.jsonArray
                        ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                        .orEmpty()
                    out to reason
                }
            } catch (e: GeminiException) {
                throw e
            } catch (e: java.io.IOException) {
                throw GeminiException("NETWORK: ${e.message ?: "request failed"}")
            }

            val parsed: JsonArray = try {
                (json.parseToJsonElement(text) as? JsonArray) ?: throw IllegalStateException()
            } catch (e: Exception) {
                repairAndParse(text) ?: run {
                    val suffix = if (finishReason != null && finishReason != "STOP") "_$finishReason" else ""
                    throw GeminiException("GEMINI_BAD_JSON$suffix")
                }
            }
            val questions = parsed.mapNotNull { toRaw(it) }
            if (questions.isEmpty()) throw GeminiException("GEMINI_EMPTY")
            questions
        }

    private fun toRaw(el: JsonElement): RawQuestion? {
        val o = el as? JsonObject ?: return null
        val q = (o["question"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val opts = (o["options"] as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() } ?: return null
        if (opts.size < 2) return null
        val ans = (o["correctAnswer"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.trim()?.toDoubleOrNull() }
            ?.toInt() ?: return null
        if (ans !in opts.indices) return null
        val exp = (o["explanation"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return RawQuestion(q, opts, ans, exp)
    }

    // ---------- JSON repair (same strategies and order as the website) ----------

    private val fenceRe = Regex("""^```(?:json)?\s*([\s\S]*?)\s*```$""", RegexOption.IGNORE_CASE)
    private val strayBackslashRe = Regex("""\\(?!["\\/u])""")
    private val trailingCommaRe = Regex(""",(\s*[\]}])""")
    private val objectRe = Regex("""\{[^{}]*\}""")

    fun repairAndParse(rawText: String): JsonArray? {
        var text = rawText.trim()
        fenceRe.find(text)?.let { text = it.groupValues[1].trim() }

        val doubled = text.replace(strayBackslashRe) { "\\\\" }
        val noCommas = doubled.replace(trailingCommaRe) { m -> m.groupValues[1] }
        for (candidate in listOf(text, doubled, noCommas)) {
            try {
                val parsed = json.parseToJsonElement(candidate)
                if (parsed is JsonArray && parsed.isNotEmpty()) return parsed
            } catch (_: Exception) {
            }
        }
        // Cut off by the token limit: keep every complete {...} object.
        val salvaged = objectRe.findAll(doubled).mapNotNull { m ->
            try {
                val obj = json.parseToJsonElement(m.value) as? JsonObject
                if (obj != null && obj["question"] != null) obj else null
            } catch (_: Exception) {
                null
            }
        }.toList()
        return if (salvaged.isNotEmpty()) JsonArray(salvaged) else null
    }

    // ---------- prompt ----------

    private fun containsBengali(text: String) = text.any { it in '\u0980'..'\u09FF' }

    /** The dropdown choice wins, except when it is left on "en" and the prompt is in Bengali. */
    fun resolveLanguage(config: ExamConfig): String = when {
        config.language == "bn" -> "bn"
        config.language == "en-bn" -> "en-bn"
        containsBengali(config.prompt) -> "bn"
        else -> "en"
    }

    private fun languageInstruction(lang: String) = when (lang) {
        "bn" -> "Bengali (বাংলা). Write the question, all four options, and the explanation entirely in Bengali script. Do not use English."
        "en-bn" -> "A mix of English and Bengali (বাংলা), matching however the user's own prompt mixes the two languages."
        else -> "English."
    }

    fun buildPrompt(config: ExamConfig): String {
        val lang = resolveLanguage(config)
        val attach = if (config.attachmentCount > 0) {
            "\n\nAttached source material: ${config.attachmentCount} file(s) (captured photo(s) and/or uploaded document(s)/image(s)) are attached to this request. Base the exam questions on the content of those files — the text, diagrams, tables, or problems shown in them — combined with the subject/topic/prompt above. If a file is unreadable or irrelevant, fall back to the subject/topic/prompt instead."
        } else ""
        return """You are generating a multiple-choice exam for a study/practice app.

Exam-prep context you must always apply (this app is built specifically
for Bangladeshi competitive job exam preparation — BCS Preliminary, Bank
Job recruitment tests, and general Government job (non-cadre) exams):

- Treat every question you generate as if it could appear in an actual
  BCS Preliminary, Bank Officer/Senior Officer, or Government job
  preliminary MCQ exam in Bangladesh — regardless of what subject/topic
  the user gives you. Match the real style, difficulty curve, and
  phrasing conventions used by BPSC (Bangladesh Public Service
  Commission), Bangladesh Bank, and PSC-administered recruitment exams.

- Draw on and mentally cross-check against the full range of sources a
  serious Bangladeshi exam candidate would actually study from, not just
  one type of material. Depending on which is most relevant to the
  topic, this includes:
  - National academic textbooks for Classes 1 through 12 (NCTB Bangla
    and English medium books) for core facts in science, mathematics,
    Bangla/English literature, and social science.
  - Well-known job-exam guidebooks (Professors, Oracle, MP3, Assurance,
    and similar BCS/Bank/Govt-job preparatory guides) for how topics are
    actually phrased and tested in practice.
  - Board/college-level lecture slides and course material for
    structured conceptual explanations.
  - Banglapedia and Wikipedia (Bangla and English editions) for
    encyclopedic facts on history, geography, biography, and culture.
  - Reputable general websites and reference sources for supporting
    context.
  - Established Bangladeshi newspapers (e.g. Prothom Alo, The Daily
    Star, Kaler Kantho, Ittefaq) for current affairs, recent events,
    and up-to-date figures.
  - Well-regarded educational YouTube channels for how a topic is
    commonly taught and which sub-points get emphasized.
  - Original literary works and their standard critical/textbook
    commentary for Bangla and English literature topics (authors,
    titles, characters, publication years, literary movements).
  You may also draw on your broader worldwide knowledge -- international
  history, geography, science, world literature, global current
  affairs, and general encyclopedic knowledge from any part of the
  world -- whenever a topic genuinely calls for it (e.g. world
  organizations, foreign countries, international treaties, global
  scientific facts). However, the sources listed above are the PRIMARY,
  authoritative sources for anything Bangladesh-specific: whenever a
  Bangladesh-related fact from your general/worldwide knowledge
  conflicts with, or is not confirmed by, the sources listed above, the
  Bangladesh-specific sources above take precedence. Use worldwide
  sources to fill in genuinely global topics or extra context, not to
  override Bangladesh-specific facts.
  When these sources could disagree (e.g. a date or figure that varies
  by source), prefer the version most consistent with official/
  government sources (Constitution, gazettes, BBS, Bangladesh Bank,
  NCTB) over guidebooks, and prefer guidebooks/textbooks over general
  web content when no official source covers the point.

- Base facts (dates, numbers, names, articles/sections, statistics,
  office-holders, historical events) ONLY on information you are
  confident is accurate and would be consistent across these sources.
  Do not invent or guess a specific date, number, or name — if you are
  not confident a fact is correct and stable across reliable sources,
  choose a different, well-established fact from the same topic instead
  of fabricating or guessing one.

- Favor question angles and phrasing patterns that have historically
  appeared in past BCS/Bank/Government preliminary exams for this kind
  of topic (frequently tested sub-areas, common distractor patterns,
  the kind of "close but wrong" options examiners typically use) over
  generic textbook-style questions.

- Each explanation should briefly justify the correct answer the way a
  good exam coaching guide would — clear enough that a student
  understands WHY the other three options are wrong, not just that they
  are — and may note which kind of source the fact is best known from
  (e.g. "as per the Constitution", "per NCTB textbook", "widely tested
  in guidebooks") when that helps the learner remember it.

- Do not include a question if you cannot state its correct answer with
  high confidence; prefer a safer, well-established fact over a
  borderline, disputed, or rapidly-changing one.

Subject: ${config.subject.ifBlank { "General" }}
Topic: ${config.topic.ifBlank { config.prompt.ifBlank { "General" } }}
Extra instructions from the user: ${config.prompt.ifBlank { "(none)" }}
Number of questions: ${config.questionCount}
Difficulty: ${config.difficulty}
Language: ${languageInstruction(lang)}

Return ONLY a JSON array (no markdown fences, no commentary) with exactly
${config.questionCount} items. Each item must have this exact shape:
{"question": string, "options": [string, string, string, string], "correctAnswer": number (0-3 index into options), "explanation": string}

Write the question, options, and explanation in the requested language.

Formatting math and chemistry:
- Whenever a question, option, or explanation contains a mathematical
  expression, equation, exponent, fraction, subscript/superscript, or a
  chemical formula/equation, write it as LaTeX wrapped in ${'$'}...${'$'} for
  inline notation or ${'$'}${'$'}...${'$'}${'$'} for a standalone/display equation. Do this
  even inside otherwise plain sentences.
- Examples: "${'$'}x^2 + y^2 = r^2${'$'}", "${'$'}\frac{a}{b}${'$'}", "${'$'}H_2SO_4${'$'}",
  "${'$'}2H_2 + O_2 \rightarrow 2H_2O${'$'}", "${'$'}\sqrt{16} = 4${'$'}".
- Do not use plain-text approximations for these (e.g. "x^2", "H2SO4",
  "->") — always use the ${'$'}...${'$'} / ${'$'}${'$'}...${'$'}${'$'} LaTeX form instead.
- Text with no math or chemistry content should NOT contain any ${'$'} signs.$attach"""
    }

    // ---------- stray LaTeX wrapping (autoWrapStrayLatex in app.js) ----------

    private const val CMDS =
        "frac|sqrt|sum|int|lim|prod|left|right|cdot|times|div|pm|mp|leq|geq|neq|approx|equiv|sim|propto|infty|alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega|Delta|Gamma|Theta|Lambda|Sigma|Phi|Psi|Omega|rightarrow|leftarrow|Rightarrow|Leftarrow|leftrightarrow|Leftrightarrow|longrightarrow|to|implies|iff|circ|degree|text|mathrm|mathbb|mathcal|mathbf|overline|underline|vec|hat|dot|partial|in|notin|ni|subset|subseteq|supset|supseteq|cup|cap|setminus|emptyset|varnothing|forall|exists|nexists|neg|lnot|wedge|vee|land|lor|mid|nmid|parallel|perp|angle|triangle|square|therefore|because|dfrac|binom|nabla|oplus|otimes|langle|rangle"
    private val commandPattern = """\\(?:$CMDS)\b(?:\{[^{}]*\}|\[[^\[\]]*\])*"""
    private val tokenRe = Regex("$commandPattern|\\\\[{}(),.]")
    private val mathSegmentRe = Regex("""\${'$'}{1,2}[^${'$'}]*\${'$'}{1,2}""")

    fun autoWrapStrayLatex(text: String): String {
        if (!text.contains('\\')) return text
        val sb = StringBuilder()
        var cursor = 0
        fun plain(seg: String) {
            sb.append(wrapPlain(seg))
        }
        for (m in mathSegmentRe.findAll(text)) {
            plain(text.substring(cursor, m.range.first))
            sb.append(m.value) // already-delimited math stays as the model wrote it
            cursor = m.range.last + 1
        }
        plain(text.substring(cursor))
        return sb.toString()
    }

    private fun wrapPlain(seg: String): String {
        if (!seg.contains('\\')) return seg
        val tokens = tokenRe.findAll(seg).map { it.range.first to (it.range.last + 1) }.toList()
        if (tokens.isEmpty()) return seg
        val maxGap = 20
        val runs = mutableListOf<Pair<Int, Int>>()
        var curStart = tokens[0].first
        var curEnd = tokens[0].second
        for (i in 1 until tokens.size) {
            val gapText = seg.substring(curEnd, tokens[i].first)
            if (gapText.length <= maxGap && !gapText.contains('\n')) {
                curEnd = tokens[i].second
            } else {
                runs.add(curStart to curEnd)
                curStart = tokens[i].first
                curEnd = tokens[i].second
            }
        }
        runs.add(curStart to curEnd)
        val out = StringBuilder()
        var cursor = 0
        for ((s, e) in runs) {
            out.append(seg, cursor, s)
            out.append('$').append(seg, s, e).append('$')
            cursor = e
        }
        out.append(seg, cursor, seg.length)
        return out.toString()
    }

    /** Placeholder questions for "Use Sample Questions" (generateSampleQuestions in app.js). */
    fun sampleQuestions(config: ExamConfig): List<RawQuestion> = (1..config.questionCount).map { i ->
        val correct = (i - 1) % 4
        val opts = listOf("Option A", "Option B", "Option C", "Option D").mapIndexed { idx, label ->
            if (idx == correct) "$label (sample correct answer)" else label
        }
        RawQuestion(
            "[Sample] Question $i about ${config.topic.ifBlank { config.subject.ifBlank { "this topic" } }}?",
            opts, correct, "This is a placeholder explanation for sample question $i.",
        )
    }
}
