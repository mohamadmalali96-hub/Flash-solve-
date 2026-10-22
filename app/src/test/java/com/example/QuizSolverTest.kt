package com.example

import com.example.data.QuizPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSolverTest {

    @Test
    fun testQuizPresetsAvailable() {
        val presets = QuizPresets.items
        assertTrue("Presets should have items", presets.isNotEmpty())
        val physicsPreset = presets.firstOrNull { it.id == "arabic_physics" }
        assertNotNull("Arabic physics preset must exist", physicsPreset)
        assertEquals("العربية", physicsPreset?.language)
    }

    @Test
    fun testMathPresetFormula() {
        val mathPreset = QuizPresets.items.firstOrNull { it.id == "math_algebra" }
        assertNotNull("Math algebra preset must exist", mathPreset)
        assertTrue(mathPreset!!.questionPrompt.contains("4x - 9 = 27"))
    }
}
