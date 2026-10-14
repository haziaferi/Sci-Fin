package app.scifin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.Tab
import app.scifin.app.UiState
import app.scifin.ui.LocalSecondColumn
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon
import app.scifin.ui.components.Caption
import app.scifin.ui.components.LEARN_LINK_DECORATION
import app.scifin.ui.components.NavRow
import app.scifin.ui.components.Page
import app.scifin.ui.components.SectionLabel
import app.scifin.ui.components.TabTitle
import app.scifin.ui.components.TopRow
import app.scifin.ui.components.measureCap
import app.scifin.ui.formatDateFull
import app.scifin.ui.opener
import app.scifin.ui.selectedWash
import app.scifin.ui.shownDetail

/**
 * §4.5's topics in their fixed order, each mapped to the §2.6 finding that supplies its
 * source line. Each body paragraph is checked against its audit section (§4.5).
 */
data class LearnTopic(
    val id: String,
    val title: String,
    val source: String,
    val body: List<String>,
    /** §9.3's country list, collapsed inside this topic's own body. Empty on every other topic. */
    val resources: List<CreditResource> = emptyList(),
)

val LEARN_TOPICS = listOf(
    LearnTopic(
        "why-no-red", "Why nothing turns red", "Grounded in research on financial shame and avoidance, including work by Gladstone and colleagues.",
        listOf(
            "Most budgeting tools mark the moment you pass a limit, often in red, and often as the only feedback they give. Research on financial shame and the “ostrich effect” finds that feedback framed as failure tends to backfire: people who feel ashamed about money stop looking at it, and not looking makes the situation worse.",
            "So this app shows the same fact either way. Going past your plan is drawn as a tick at the plan and a hatched fill beyond it — on the same bar, in the same colours, in the same tone. The information is all there; the verdict is not.",
            "The pattern is a shape rather than a colour, so it still reads with colour blindness.",
        ),
    ),
    LearnTopic(
        "name-your-goal", "Why naming a goal helps", "Grounded in future self-continuity research, including work by Hal Hershfield.",
        listOf(
            "When people picture their future self, the brain responds more as it would to a stranger than to themselves. The weaker that sense of connection, the less people save for that future self.",
            "Naming a goal concretely closes some of that distance. A goal that says what it’s for — a trip, a deposit, a month of breathing room — makes the person it’s for feel real in a way a number going up doesn’t, and in controlled studies that kind of concreteness has measurably raised saving.",
            "That’s why a goal here starts with what it’s for, not just how much. The name isn’t decoration; it’s the mechanism.",
        ),
    ),
    LearnTopic(
        "why-categories", "Why categories work", "Grounded in research on mental accounting (Richard Thaler) and on earmarking and partitioning (Soman & Cheema).",
        listOf(
            "On paper, money is interchangeable: a euro from a paycheck and a euro from a refund spend the same. Almost nobody experiences it that way. People sort money by where it came from and what it’s for, and hold each pile to different rules, even though it’s all one pool.",
            "That’s usually described as a bias to correct. It’s better read as the reason envelope-style budgeting works at all: giving money a job uses something the mind already does. Where it misfires: windfalls such as refunds tend to feel like “extra” and get spent more freely than the same amount from a paycheck. Worth a second look when one lands.",
            "Setting money visibly apart, not just labelling it, adds to the effect: people save more toward a pot they can see. The same research found the other edge: people sometimes protect a labelled pot through a real emergency, borrowing at high interest rather than “break” it. That’s why every goal and category here stays freely editable, and nothing makes moving money back feel like failing.",
        ),
    ),
    LearnTopic(
        "well-being", "Financial well-being over adherence", "Grounded in the Consumer Financial Protection Bureau’s research defining financial well-being.",
        listOf(
            "Many budgeting apps quietly define success as adherence: did you follow the plan exactly. That means you’ve failed the moment real life doesn’t cooperate, which it often doesn’t.",
            "Research defines financial well-being differently: as a sense of security and freedom of choice, not as a score against a plan.",
            "That’s why no month here is graded, and why Reports open on how long your Savings would cover you rather than on a spending breakdown. The numbers are information, not a report card.",
        ),
    ),
    LearnTopic("credit-score", CREDIT_TITLE, "Credit invisibility — research by the Consumer Financial Protection Bureau", CREDIT_BODY, CREDIT_RESOURCES),
)


@Composable
fun LearnScreen(
    state: UiState,
    store: AppStore,
    /** Renders only: start with this topic expanded instead of the list closed. */
    initialOpen: String? = null,
    /** Renders only: start §9.3's country list expanded too. */
    initialListOpen: Boolean = false,
) {
    val t = LocalTokens.current; val p = t.palette
    var open by remember { mutableStateOf(initialOpen) }
    val openRoute = opener(store)
    // §4.8: with a reading pane, a topic opens there and its row is marked
    val pane = LocalSecondColumn.current
    val reading = if (pane) (shownDetail(state) as? Route.LearnTopic)?.id else null
    Page(null) {
        TabTitle(null, Tab.LEARN.label) { store.selectTab(Tab.SETTINGS) }
        for (topic in LEARN_TOPICS) {
            val isOpen = !pane && open == topic.id
            val wash = selectedWash(reading == topic.id)
            Column(Modifier.fillMaxWidth().background(p.surf, RoundedCornerShape(t.radii.lg)).then(if (wash != null) Modifier.background(wash, RoundedCornerShape(t.radii.lg)) else Modifier)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = t.spacing.target).clickable(role = Role.Button) { if (pane) openRoute(Route.LearnTopic(topic.id)) else open = if (isOpen) null else topic.id }
                        .semantics { if (!pane) stateDescription = if (isOpen) "Expanded" else "Collapsed" }.padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(topic.title, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.md, modifier = Modifier.rotate(if (isOpen) 90f else 0f))
                }
                if (isOpen) Column(Modifier.padding(start = t.spacing.u * 1.5f, end = t.spacing.u * 1.5f, bottom = t.spacing.u * 1.25f), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
                    TopicBody(topic, initialListOpen)
                }
            }
        }
        SectionLabel("Calculators")
        NavRow(Lucide.LANDMARK, "What a loan actually costs", "Compare financing against saving", decorative = true) { openRoute(Route.LoanCalc) }
        NavRow(Lucide.CREDIT_CARD, "Putting a purchase on a card", "What financing this one thing costs", decorative = true) { openRoute(Route.PurchaseCalc) }
    }
}

/** A topic's text, its country list where it has one, and its source line: the same in place and in the pane. */
@Composable
private fun TopicBody(topic: LearnTopic, initialListOpen: Boolean = false) {
    val t = LocalTokens.current; val p = t.palette
    for (para in topic.body) Text(para, color = p.txt2, fontSize = t.type.body, lineHeight = t.type.body * 1.6, modifier = Modifier.measureCap(t))
    if (topic.resources.isNotEmpty()) CreditResourceList(topic.resources, initialListOpen)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2)) {
        LucideIcon(Lucide.INFO, tint = p.txt2, size = t.icons.xs); Text(topic.source, color = p.txt2, fontSize = t.type.caption, modifier = Modifier.measureCap(t))
    }
}

/** §4.8: the chosen topic in Learn's second column. */
@Composable
fun LearnTopicScreen(route: Route.LearnTopic, store: AppStore) {
    val t = LocalTokens.current
    val topic = LEARN_TOPICS.firstOrNull { it.id == route.id } ?: LEARN_TOPICS.first()
    Page(null) {
        TopRow(topic.title, onBack = { store.back() })
        Column(verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) { TopicBody(topic) }
    }
}

/** §9.3's country list: a disclosure inside the credit topic's own body, closed by default and whenever the topic closes. */
@Composable
private fun CreditResourceList(resources: List<CreditResource>, initialOpen: Boolean = false) {
    val t = LocalTokens.current
    val p = t.palette
    var open by remember { mutableStateOf(initialOpen) }
    Column(Modifier.fillMaxWidth().background(p.surf2, RoundedCornerShape(t.radii.sm))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = t.spacing.target)
                .clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(horizontal = t.spacing.u * 1.25f, vertical = t.spacing.u),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(CREDIT_LIST_LABEL, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).measureCap(t))
            LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.sm, modifier = Modifier.rotate(if (open) 90f else 0f))
        }
        if (open) Column(
            Modifier.padding(horizontal = t.spacing.u * 1.25f).padding(bottom = t.spacing.u * 1.25f),
            verticalArrangement = Arrangement.spacedBy(t.spacing.gap),
        ) {
            for ((scored, label) in listOf(true to CREDIT_SCORED_LABEL, false to CREDIT_UNSCORED_LABEL)) {
                SectionLabel(label)
                for (r in resources.filter { it.scored == scored }) CreditResourceRow(r)
            }
            Caption(CREDIT_SOURCING_NOTE)
        }
    }
}

/** One country: what it is, the official source, and the day that source was last checked. */
@Composable
private fun CreditResourceRow(r: CreditResource) {
    val t = LocalTokens.current
    val p = t.palette
    Column(verticalArrangement = Arrangement.spacedBy(t.spacing.u / 4)) {
        Text(r.country, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold, modifier = Modifier.measureCap(t))
        Text(r.body, color = p.txt2, fontSize = t.type.caption, lineHeight = t.type.caption * 1.5, modifier = Modifier.measureCap(t))
        Text(
            "${r.source} · checked ${formatDateFull(r.checked)}",
            color = p.txt2, fontSize = t.type.caption, lineHeight = t.type.caption * 1.5, modifier = Modifier.measureCap(t),
        )
        val uri = LocalUriHandler.current
        Text(
            r.url, color = p.acc, fontSize = t.type.caption, fontWeight = FontWeight.Bold, textDecoration = LEARN_LINK_DECORATION,
            modifier = Modifier.measureCap(t).heightIn(min = t.spacing.u * 3).clickable(role = Role.Button, onClickLabel = "Open ${r.source}") { runCatching { uri.openUri(r.url) } },
        )
    }
}
