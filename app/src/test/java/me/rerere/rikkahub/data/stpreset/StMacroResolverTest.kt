package me.rerere.rikkahub.data.stpreset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StMacroResolverTest {
    private val context = StMacroContext(
        characterName = "Alice",
        userName = "Bob",
        personaDescription = "a careful writer",
        description = "desc",
        personality = "kind",
        scenario = "room",
        mesExample = "example",
        modelName = "test-model",
        input = "hello",
        lastMessage = "last",
    )

    @Test
    fun `expands character and persona macros`() {
        val resolver = StMacroResolver()
        assertEquals(
            "Alice meets Bob; a careful writer",
            resolver.render("{{char}} meets {{user}}; {{persona}}", context),
        )
    }

    @Test
    fun `setvar then getvar works in the same pass`() {
        val variables = mutableMapOf<String, String>()
        val resolver = StMacroResolver(variables)
        val result = resolver.render("{{setvar::mood::happy}}Mood={{getvar::mood}}", context)
        assertEquals("Mood=happy", result)
        assertEquals("happy", variables["mood"])
    }

    @Test
    fun `getvar resolves macros inside the value`() {
        val variables = mutableMapOf("who" to "{{char}}")
        val resolver = StMacroResolver(variables)
        assertEquals("Alice", resolver.render("{{getvar::who}}", context))
    }

    @Test
    fun `setvar value can contain a nested macro`() {
        val variables = mutableMapOf<String, String>()
        val resolver = StMacroResolver(variables)
        assertEquals("Alice", resolver.render("{{setvar::who::{{char}}}}{{getvar::who}}", context))
        assertEquals("Alice", variables["who"])
    }

    @Test
    fun `random and roll are deterministic with a fixed seed`() {
        val first = StMacroResolver(random = Random(42)).render("{{random::A::B}}", context)
        val second = StMacroResolver(random = Random(42)).render("{{random::A::B}}", context)
        assertEquals(first, second)
        assertTrue(first == "A" || first == "B")

        val roll = StMacroResolver(random = Random(1)).render("{{roll:1d1+3}}", context)
        assertEquals("4", roll)
    }

    @Test
    fun `unknown macro is removed and recorded`() {
        val resolver = StMacroResolver()
        assertEquals("a  b", resolver.render("a {{unknownThing}} b", context))
        assertTrue(resolver.unresolvedMacros.any { it.contains("unknownThing") })
    }

    @Test
    fun `comments are removed`() {
        val resolver = StMacroResolver()
        assertEquals("abc", resolver.render("a{{// note}}bc", context))
    }
}
