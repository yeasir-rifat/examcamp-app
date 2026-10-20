package com.examcamp.app

/** Public client config copied from the website (app.js). The anon key is meant to ship in clients. */
object Config {
    /** Hardcoded Live Exam admins (LIVE_EXAM_ADMIN_EMAILS in app.js). Lowercase. */
    /** Practice Admin is stricter: one owner email (PRACTICE_ADMIN_EMAILS in app.js). Lowercase. */
    val PRACTICE_ADMIN_EMAILS = listOf("rifat.webflow@gmail.com")
    val LIVE_EXAM_ADMIN_EMAILS = listOf("rifat.webflow@gmail.com")
    const val SUPABASE_URL = "https://ximkiqggugotgmqzwpmm.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhpbWtpcWdndWdvdGdtcXp3cG1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc5Njg5MDgsImV4cCI6MjEwMzU0NDkwOH0.-kyGx_LQByohZPm0oXtUyk0SeOy2e_ZrQtgrNGt335k"
    /** Supabase Edge Function that serves the Practice manifest and question banks (PRACTICE_API_BASE in app.js). */
    const val PRACTICE_API_BASE = "https://ximkiqggugotgmqzwpmm.supabase.co/functions/v1/clever-service"
}
