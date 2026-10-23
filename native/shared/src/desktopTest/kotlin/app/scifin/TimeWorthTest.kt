package app.scifin

import app.scifin.app.Route
import app.scifin.engine.TimeWorth
import app.scifin.model.IncomeCadence
import app.scifin.model.moneyOf
import app.scifin.ui.screens.LEARN_TOPICS
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §10's topic and calculator, and §4.5's order (owner, 2026-10-23): topics in the order their links are met. */
class TimeWorthTest {
    private val settings = Fixture.profile.settings

    @Test fun `Learn's order follows its links -- Budget top down, Categories, Activity -- then the unlinked topics`() {
        assertEquals(listOf("name-your-goal", "why-categories", "why-no-red", "borrowing", "credit-score", "well-being", "time-worth"), LEARN_TOPICS.map { it.id })
        // Budget's two links read in the order of the sections they speak to, Goals above categories
        val budget = File("src/commonMain/kotlin/app/scifin/ui/screens/BudgetScreen.kt").readText()
        assertTrue(budget.indexOf("LearnTopic(\"name-your-goal\")") in 0 until budget.indexOf("LearnTopic(\"why-categories\")"))
    }

    @Test fun `the topic leads into its calculator, which Learn's list offers too`() {
        val topic = LEARN_TOPICS.single { it.id == "time-worth" }
        assertEquals(listOf(Route.TimeWorthCalc), topic.calculators.map { it.second })
        assertTrue(topic.body.size >= 3 && topic.resources.isEmpty())
        assertEquals(setOf("time-worth", "borrowing"), LEARN_TOPICS.filter { it.calculators.isNotEmpty() }.map { it.id }.toSet(), "only §10's and §9's topics lead into calculators")
        assertTrue("Route.TimeWorthCalc" in File("src/commonMain/kotlin/app/scifin/ui/screens/LearnScreen.kt").readText().substringAfter("SectionLabel(\"Calculators\")"))
    }

    @Test fun `with Income off there is no pre-fill, and the rate typed is all the math needs`() {
        val off = settings.copy(incomeEnabled = false)
        assertNull(TimeWorth.monthlyEquivalent(off)); assertNull(TimeWorth.prefillRate(off, 160.0))
        assertEquals(moneyOf(220.0) to moneyOf(2640.0), TimeWorth.worth(moneyOf(22.0), 10.0))
        assertEquals(moneyOf(11.0) to moneyOf(132.0), TimeWorth.worth(moneyOf(22.0), 0.5))
        assertEquals(0L to 0L, TimeWorth.worth(moneyOf(22.0), -3.0))
    }

    @Test fun `with Income on a month's pay over the expected hours pre-fills the rate`() {
        fun on(c: IncomeCadence, pay: Double) = settings.copy(incomeEnabled = true, income = moneyOf(pay), incomeCadence = c)
        assertEquals(moneyOf(3200.0), TimeWorth.monthlyEquivalent(on(IncomeCadence.MONTHLY, 3200.0)))
        assertEquals(moneyOf(3200.0), TimeWorth.monthlyEquivalent(on(IncomeCadence.SEMIMONTHLY, 1600.0)))
        assertEquals(moneyOf(2600.0), TimeWorth.monthlyEquivalent(on(IncomeCadence.BIWEEKLY, 1200.0)))
        assertEquals(moneyOf(2600.0), TimeWorth.monthlyEquivalent(on(IncomeCadence.WEEKLY, 600.0)))
        assertEquals(moneyOf(20.0), TimeWorth.prefillRate(on(IncomeCadence.MONTHLY, 3200.0), 160.0))
        assertNull(TimeWorth.prefillRate(on(IncomeCadence.MONTHLY, 3200.0), 0.0), "no hours, no rate -- never a division by zero")
        assertNull(TimeWorth.monthlyEquivalent(on(IncomeCadence.MONTHLY, 0.0)), "Income on with no amount is no pre-fill")
    }
}
