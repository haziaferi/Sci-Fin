package app.scifin

import app.scifin.app.Route
import app.scifin.ui.screens.LEARN_TOPICS
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** §9's Learn topic (owner, 2026-10-23): loans and cards together, linked from both account screens, leading into both calculators. */
class BorrowingTopicTest {
    private val topic = LEARN_TOPICS.single { it.id == "borrowing" }

    @Test fun `the topic leads into the loan and the card calculators, in that order`() {
        assertEquals("What borrowing really costs", topic.title)
        assertEquals(listOf(Route.LoanCalc, Route.PurchaseCalc), topic.calculators.map { it.second })
        assertTrue(topic.body.size >= 3 && topic.body.none { it == "PENDING" } && topic.source != "PENDING")
        val all = topic.body.joinToString(" ").lowercase()
        assertTrue("monthly payment" in all && "minimum" in all, "the topic is about what the payment and the minimum hide")
    }

    @Test fun `a loan's screen and a card's screen link to it, the card's above its credit-score link`() {
        val src = File("src/commonMain/kotlin/app/scifin/ui/screens/ActivityScreens.kt").readText()
        assertEquals(2, Regex("""LearnTopic\("borrowing"\)""").findAll(src).count(), "one link on each account type's screen")
        val panel = src.substringAfter("private fun PayoffPanel(")
        assertTrue(panel.indexOf("LearnTopic(\"borrowing\")") in 0 until panel.indexOf("LearnTopic(\"credit-score\")"), "§4.5's order puts borrowing before credit")
    }
}
