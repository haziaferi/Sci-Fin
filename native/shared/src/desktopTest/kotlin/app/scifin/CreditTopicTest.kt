package app.scifin

import app.scifin.ui.screens.CREDIT_RESOURCES
import app.scifin.ui.screens.CREDIT_TITLE
import app.scifin.ui.screens.LEARN_TOPICS
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §9.3's rules about its own resource list are the strictest sourcing rules in the document,
 * and the list is designed to grow — "adding country #31 later is adding a row, not a
 * redesign". These are the gate on that row.
 */
class CreditTopicTest {

    /** Names that identify a private credit bureau or a commercial credit-building site. */
    private val forbidden = listOf(
        "experian", "equifax", "transunion", "schufa", "crif", "creditkarma", "credit-karma",
        "fico", "cibil", "clearscore", "creditsafe", "nerdwallet", "creditsesame",
    )

    @Test fun `every resource points at a public authority, never a bureau and never a commercial site`() {
        for (r in CREDIT_RESOURCES) {
            val where = "${r.country}: ${r.url}"
            val u = r.url.lowercase()
            for (name in forbidden) {
                // the body may *name* a bureau -- Equifax, SCHUFA -- but the link never points at one
                assertTrue(name !in u, "$where links at what reads as a credit bureau or a commercial site")
            }
            assertTrue(!u.startsWith("http"), "$where carries a scheme; entries are bare hosts and paths")
            assertTrue("?" !in u && "#" !in u, "$where carries a query or fragment, which is how tracking links look")
            assertTrue(u.substringBefore('/').contains('.'), "$where has no host")
        }
    }

    @Test fun `the body may name a bureau even though the link may not`() {
        // guards the test above from being tightened into something §9.3 does not ask for:
        // Canada's and Germany's entries are only honest because they name who runs the score.
        val bodies = CREDIT_RESOURCES.joinToString(" ") { it.body }.lowercase()
        assertTrue("equifax" in bodies, "Canada's entry stopped naming who runs its score")
    }

    @Test fun `both buckets are populated, countries are unique, and every entry is dated`() {
        assertTrue(CREDIT_RESOURCES.any { it.scored }, "no scored country left")
        assertTrue(CREDIT_RESOURCES.any { !it.scored }, "the unscored bucket is what stops a US-shaped template")
        assertEquals(CREDIT_RESOURCES.size, CREDIT_RESOURCES.map { it.country }.toSet().size, "a country appears twice")
        // the date is shown to the person precisely because nothing re-checks the link afterwards
        assertTrue(CREDIT_RESOURCES.all { it.checked.year >= 2026 }, "an entry has no plausible check date")
    }

    @Test fun `the topic is last, carries a real body, and says what it will not do`() {
        val last = LEARN_TOPICS.last()
        assertEquals("credit-score", last.id, "§4.5's order puts the credit topic last")
        assertEquals(CREDIT_TITLE, last.title)
        assertTrue(last.body.size >= 4, "the body is the five paragraphs §9.3's bullets ask for")
        val all = last.body.joinToString(" ").lowercase()
        assertTrue("credit invisible" in all, "the topic never names the phenomenon it exists to name")
        assertTrue("no recommendation" in all, "§9.3 forbids a behavioural recommendation; the body has to say so")
        assertTrue(CREDIT_RESOURCES === last.resources, "the list has to sit with the topic")
        assertTrue(LEARN_TOPICS.count { it.resources.isNotEmpty() } == 1, "only the credit topic carries a country list")
    }

    /** §9.3: "not linked from either calculator" — a link there would read as the recommendation it rules out. */
    @Test fun `neither calculator links to the credit topic`() {
        val calc = File("src/commonMain/kotlin/app/scifin/ui/screens/CalculatorScreens.kt").readText()
        for (needle in listOf("credit-score", "CREDIT_TITLE", "CREDIT_BODY", "CREDIT_RESOURCES")) {
            assertTrue(needle !in calc, "CalculatorScreens.kt references $needle")
        }
    }
}
