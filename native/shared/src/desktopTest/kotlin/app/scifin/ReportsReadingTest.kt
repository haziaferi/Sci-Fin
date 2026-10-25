package app.scifin

import app.scifin.ui.screens.LEARN_TOPICS
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** §13.1 item 4 (owner, 2026-10-25): Age of Money's and Income vs Expenses' reading, as Learn topics linked last in each view's side column. */
class ReportsReadingTest {
    @Test fun `both topics are written, and always in Learn whatever Income is set to`() {
        for ((id, title) in listOf("money-on-hand" to "Why money on hand matters", "uneven-months" to "Why uneven months are normal")) {
            val topic = LEARN_TOPICS.single { it.id == id }
            assertEquals(title, topic.title)
            assertTrue(topic.body.size >= 3 && topic.body.none { it == "PENDING" } && topic.source != "PENDING", "$id still carries its placeholder")
            assertTrue(topic.calculators.isEmpty() && topic.resources.isEmpty())
        }
    }

    @Test fun `each view links its topic last in its side column`() {
        val src = File("src/commonMain/kotlin/app/scifin/ui/screens/ReportsScreen.kt").readText()
        for (id in listOf("money-on-hand", "uneven-months")) {
            val at = src.indexOf("LearnTopic(\"$id\")")
            assertTrue(at > 0, "no link to $id")
            val column = src.substring(src.lastIndexOf("supporting = {", at), src.indexOf("\n    })", at))
            assertTrue(column.substringAfter("LearnTopic(\"$id\")").lines().none { "InfoNote(" in it || "LearnLink(" in it }, "the $id link is not last in its column")
        }
    }
}
