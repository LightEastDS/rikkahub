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

    @Test
    fun `var alias and colon tolerance works`() {
        val variables = mutableMapOf("mood" to "cheerful", "score" to "100")
        val resolver = StMacroResolver(variables)
        assertEquals("cheerful", resolver.render("{{var::mood}}", context))
        assertEquals("cheerful", resolver.render("{{var:mood}}", context))
        assertEquals("cheerful", resolver.render("{{getvar:mood}}", context))
        assertEquals("100", resolver.render("{{var::score}}", context))

        resolver.render("{{setvar:role:warrior}}", context)
        assertEquals("warrior", variables["role"])

        resolver.render("{{setvar::weapon=sword}}", context)
        assertEquals("sword", variables["weapon"])
    }

    @Test
    fun `handlebars conditional blocks evaluate properly`() {
        val variables = mutableMapOf("enable" to "1", "disabled_flag" to "0")
        val resolver = StMacroResolver(variables)
        assertEquals("YES", resolver.render("{{#if {{getvar::enable}}}}YES{{/if}}", context))
        assertEquals("", resolver.render("{{#if {{getvar::disabled_flag}}}}YES{{/if}}", context))
        assertEquals("NOPE", resolver.render("{{#unless {{getvar::disabled_flag}}}}NOPE{{/unless}}", context))
        assertEquals("", resolver.render("{{#unless {{getvar::enable}}}}NOPE{{/unless}}", context))
    }

    @Test
    fun `two-pass pre-scan resolves reverse dependency across prompts`() {
        val prompts = listOf(
            StPrompt(identifier = "p1", name = "p1", content = "Role is {{getvar::role}}"),
            StPrompt(identifier = "p2", name = "p2", content = "{{setvar::role::Hero}}"),
        )
        val scannedVars = StMacroResolver.preScanVariables(
            prompts = prompts,
            persistedVariables = emptyMap(),
            context = context,
        )
        assertEquals("Hero", scannedVars["role"])
        val resolver = StMacroResolver(variables = scannedVars)
        val rendered = resolver.render(prompts[0].content, context)
        assertEquals("Role is Hero", rendered)
    }
}
