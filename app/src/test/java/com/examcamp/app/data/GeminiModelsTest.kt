package com.examcamp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiModelsTest {
    @Test fun offeredModelIsKept() {
        assertEquals("gemini-4-argon", GeminiModels.resolve("gemini-4-argon"))
        assertEquals("gemini-3.6-flash", GeminiModels.resolve("gemini-3.6-flash"))
    }

    @Test fun retiredOrBlankModelFallsBackToDefault() {
        for (old in listOf("", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-3.5-flash", "gemini-3.1-pro-preview")) {
            assertEquals(GeminiModels.DEFAULT, GeminiModels.resolve(old))
        }
    }

    @Test fun defaultIsInTheList() {
        assertTrue(GeminiModels.options.any { it.first == GeminiModels.DEFAULT })
    }
}
