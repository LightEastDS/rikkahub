package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StringUtilsTest {

    @Test
    fun `extract chinese double quotes`() {
        assertEquals(listOf("你好"), "他说“你好”".extractQuotedContent())
    }

    @Test
    fun `extract chinese single quotes`() {
        assertEquals(listOf("世界"), "标题是‘世界’".extractQuotedContent())
    }

    @Test
    fun `extract english double quotes`() {
        assertEquals(listOf("hello"), "he said \"hello\"".extractQuotedContent())
    }

    @Test
    fun `extract english single quotes`() {
        assertEquals(listOf("world"), "title is 'world'".extractQuotedContent())
    }

    @Test
    fun `extract corner brackets`() {
        assertEquals(listOf("你好"), "他说「你好」".extractQuotedContent())
    }

    @Test
    fun `extract white corner brackets`() {
        assertEquals(listOf("世界"), "标题是『世界』".extractQuotedContent())
    }

    @Test
    fun `extract multiple quotes`() {
        assertEquals(
            listOf("你好", "世界"),
            "“你好” 和 ‘世界’".extractQuotedContent(),
        )
    }

    @Test
    fun `blank content is ignored`() {
        assertTrue("“” \"\" '  '".extractQuotedContent().isEmpty())
    }

    @Test
    fun `no quotes returns empty`() {
        assertTrue("没有任何引号".extractQuotedContent().isEmpty())
    }

    @Test
    fun `extract as text joins with separator`() {
        assertEquals("你好\n世界", "“你好”‘世界’".extractQuotedContentAsText())
    }

    @Test
    fun `extract as text returns null when empty`() {
        assertNull("没有引号".extractQuotedContentAsText())
    }

    @Test
    fun `remove english brackets`() {
        assertEquals("你好世界", "你好(旁白)世界".removeBracketedContent())
    }

    @Test
    fun `remove chinese brackets`() {
        assertEquals("你好世界", "你好（旁白）世界".removeBracketedContent())
    }

    @Test
    fun `remove multiple brackets`() {
        assertEquals("你好世界", "你好(注释)世界（备注）".removeBracketedContent())
    }

    @Test
    fun `remove brackets keeps outside text trimmed`() {
        assertEquals("你好", "(旁白) 你好 ".removeBracketedContent())
    }

    @Test
    fun `remove brackets does not cross bracket boundaries`() {
        assertEquals("ac", "a(b)c".removeBracketedContent())
    }

    @Test
    fun `remove brackets returns null when all removed`() {
        assertNull("(全是旁白)".removeBracketedContent())
    }

    @Test
    fun `remove brackets returns null for blank result`() {
        assertNull("（旁白） ".removeBracketedContent())
    }

    @Test
    fun `no brackets returns original text`() {
        assertEquals("没有括号", "没有括号".removeBracketedContent())
    }

    @Test
    fun `countWords empty and whitespace returns 0`() {
        assertEquals(0, "".countWords())
        assertEquals(0, "   \t\n\r  \u3000\u200B  ".countWords())
    }

    @Test
    fun `countWords chinese characters and punctuation excluding whitespace`() {
        // 4 汉字 + 2 标点 = 6
        assertEquals(6, "你好，世界！".countWords())
        // 空格、制表符、换行、全角空格 \u3000 均不计入
        assertEquals(6, "  你好 ， \n 世界 ！ \u3000 ".countWords())
        // 14 汉字 + 2 标点 = 16
        assertEquals(16, "这是一段测试文本，包含标点符号。".countWords())
    }

    @Test
    fun `countWords english words and punctuation excluding whitespace`() {
        // 2 单词 ("Hello", "world") + 2 标点 (",", "!") = 4
        assertEquals(4, "Hello, world!".countWords())
        // 6 单词 ("Don't", "worry", "there", "are", "42", "apples") + 2 标点 (",", ".") = 8
        assertEquals(8, "Don't worry, there are 42 apples.".countWords())
        // 4 单词 + 1 标点 = 5
        assertEquals(5, "  This   is   a   test .  ".countWords())
        // 曲线单引号缩写 it’s 作为单个词计入
        assertEquals(4, "it’s a curly apostrophe".countWords())
    }

    @Test
    fun `countWords japanese kanji kana and punctuation excluding whitespace`() {
        // 6 汉字 ("吾輩猫名前無") + 8 假名 ("はであるはまだい") + 2 标点 ("。。") = 16
        assertEquals(16, "吾輩は猫である。名前はまだ無い。".countWords())
        // 1 汉字 ("飲") + 6 假名 ("コーヒーム") + 1 标点 ("。") = 8
        assertEquals(8, "コーヒーを飲む。".countWords())
        // 2 汉字 ("世界") + 5 假名 ("こんにちは") + 2 标点 ("、！") = 9
        assertEquals(9, "こんにちは、世界！".countWords())
        // 带全角空格与常规空格：3假名("これは") + 3假名("テスト") + 2假名("です") + 1标点("。") = 9
        assertEquals(9, "  这是　テスト です 。 ".replace("这是", "これは").countWords())
    }

    @Test
    fun `countWords mixed multilingual text`() {
        // 汉字: 我正在学习 (5) + 和 (1) + 日语 (2) + 日本語 (3) = 11
        // 英文单词: Kotlin (1)
        // 标点: （ (1) + ） (1) + 。 (1) = 3
        // 总计: 11 + 1 + 3 = 15
        assertEquals(15, "我正在学习 Kotlin 和 日语（日本語）。".countWords())

        // 汉字: 在 (1) + 年 (1) + 技术 (2) + 比如 (2) + 发展迅速 (4) = 10
        // 英文单词/数字: 2026 (1) + AI (1) + GPT (1) + 4 (1) = 4
        // 标点: ， (1) + （ (1) + - (1) + ） (1) + 。 (1) = 5
        // 总计: 10 + 4 + 5 = 19
        assertEquals(19, "在2026年，AI技术（比如GPT-4）发展迅速。".countWords())
    }

    @Test
    fun `countWords special marks and numbers`() {
        // 々 (叠字符号): 2
        assertEquals(2, "人们".replace("们", "々").countWords())
        // 〇 (中文零): 5
        assertEquals(5, "二〇二六年".countWords())
        // 引号与引号内的单词: 2 标点("“”") + 2 汉字("测试") + 2 标点("''") + 1 英文单词("test") = 7
        assertEquals(7, "“测试” 'test'".countWords())
    }
}
