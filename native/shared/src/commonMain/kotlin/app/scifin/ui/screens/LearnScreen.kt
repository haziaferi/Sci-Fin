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
import app.scifin.ui.components.LearnLink
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
 * §4.5's topics in their fixed order -- the order their links are met in the app, Budget from the top, then
 * Categories, then Activity; topics no screen links to follow -- each mapped to the finding that supplies its
 * source line. Each body paragraph is checked against its audit section (§4.5).
 */
data class LearnTopic(
    val id: String,
    val title: String,
    val source: String,
    val body: List<String>,
    /** §9.3's country list, collapsed inside this topic's own body. Empty on every other topic. */
    val resources: List<CreditResource> = emptyList(),
    /** The calculators the topic leads into (§9.1, §9.2, §10.2), as links after its body. */
    val calculators: List<Pair<String, Route>> = emptyList(),
)

/**
 * §10.3's text: the prototype's, checked claim by claim against the sources in the research audit's §2.5 (2026-10-23).
 * Declared before [LEARN_TOPICS], which reads it as it is built.
 */
private val TIME_WORTH_BODY = listOf(
    "Plenty of people hold back from asking for a raise, overtime pay, or other fair compensation they have earned. For women, holding back is often a rational read of real circumstances. Studies find that women who ask can be judged more harshly for it than men who ask the same way, and that women who ask are less likely to get what they asked for. Add a documented wage gap and a larger share of unpaid care and household work, and asking can risk more while starting from less.",
    "The wider pattern shows up across genders too. In 2025 studies of nearly 6,000 Americans deciding whether to haggle over everyday purchases, most said they sometimes pass up the chance: many wanted to save a fifth to a third of the price before it felt worth raising, and about half would pay more just to skip the conversation. Asked why they didn’t push for more pay, people most often say they were happy with the offer or didn’t feel comfortable asking.",
    "None of which makes not asking a mistake. Sometimes it is the accurate reading: job security is genuinely at stake, the power imbalance is genuinely real, or your role has no paid-overtime arrangement to point to in the first place. This card is not here to tell you to ask.",
    "What it can do is make the number visible: what the time you are giving away comes to, in your own figures. What you do with that, including nothing, is yours to decide.",
)
/** §9's topic (owner, 2026-10-23): written from the sources in the research audit's §2.6, declared before [LEARN_TOPICS] like the above. */
private val BORROWING_BODY = listOf(
    "This app leans toward saving for things rather than borrowing for them. That’s a deliberate point of view, and it comes from one finding repeated across studies: when borrowing is judged by its monthly payment, the full cost stays out of sight, and people tend to underestimate it.",
    "With a loan, the monthly payment is the number that gets the attention. In studies of car loans, buyers were swayed more by a longer term than by a lower interest rate, settled on round monthly amounts, and often spent a lower payment on a bigger loan. Interest also builds faster than intuition expects: shown a loan’s payments, people tend to guess its interest rate too low, and those who misjudge it most tend to borrow more and save less.",
    "With a card, the number doing the work is the minimum printed on the statement. In one experiment, people paying part of a bill said they would pay much less when a minimum was shown than when it wasn’t; those paying in full were unaffected. Across a large share of real accounts, payments sit at or near the minimum, and for some people the minimum itself, rather than a tight month, seems to set the amount. Printing a larger, faster payment on statements moved very few.",
    "So the calculators here lead with the whole cost. For a loan, that’s what it repays in total and in interest, beside the same monthly amount saved instead. For a purchase on a card, it starts with waiting and paying cash, then shows how long the balance takes to clear and what it costs at a payment you choose. A card’s own screen shows the same outlook for its balance: paid in full, at a fixed amount, and at the minimum, so the minimum is one option among several. If you already carry a loan or a card balance, that’s an ordinary part of life, and these numbers are here to help you plan it, not to judge it.",
)
private const val BORROWING_SOURCE = "Grounded in research on exponential growth bias (Stango & Zinman), on monthly payment targeting in car loans (Argyle, Nadauld & Palmer), and on anchoring to credit-card minimum payments, including work by Neil Stewart and by Keys & Wang."

/** Reports' reading (§13.1 item 4, owner 2026-10-25): Age of Money's and Income vs Expenses' topics, from the research audit's §2.7 and §2.8. */
private val MONEY_ON_HAND_BODY = listOf(
    "Research on money and well-being usually looks at income. One study looked instead at what people actually had on hand. Matching the bank records of 585 UK customers with their own answers, it found that people with more in their current and savings accounts felt more secure about money: less likely to lose sleep over it, more confident about it. That sense of security went with being more satisfied with life, even allowing for income, investments, debt and spending. It was a snapshot, so it shows the two go together, not that one causes the other.",
    "Other research looks at the opposite state. In lab games where some players were given fewer turns or less time, those players focused harder on the problem in front of them and borrowed more readily from later rounds. The shortfall pulled attention towards itself. A broader claim, that money worries make people think less well in general, has not held up well when other teams tried to repeat the studies, so it isn’t made here.",
    "This app reads the two together as pointing at room: some distance between money arriving and money being needed. Money that has sat a while is money that hasn’t yet had to be called on. That’s what Age of Money describes: how long the oldest money in your Savings has been there and, if you want it, how long today’s Savings would cover you at your usual spending.",
    "It’s a cushion, not a score. It rises by itself on quiet days and falls when you use what you set aside, which is what Savings is for. A short figure describes your circumstances right now, not you, and nothing here claims that watching it will change how you feel.",
)
private const val MONEY_ON_HAND_SOURCE = "Grounded in research on cash on hand and life satisfaction (Ruberton, Gladstone & Lyubomirsky) and on scarcity and attention, including work by Shah, Mullainathan & Shafir and the replication studies that followed."
private val UNEVEN_MONTHS_BODY = listOf(
    "Money rarely arrives in even monthly slices, and spending doesn’t either. In a year-long study that followed low- and moderate-income US households in detail, the typical household had about five months when its income was more than a quarter above or below its own average. In bank data on 100,000 US account holders across the income range, 41% saw their income swing by more than 30% from one month to the next. Spending swung even more, and the highest earners were as uneven as the lowest.",
    "The two lines don’t move together, either. In the household study, most spending peaks came in months without an income peak, and a third came in months when income was below average. Some months simply hold more: a tax bill, the end-of-year season, a month with an extra payday in it.",
    "Where pay is regular, spending tends to follow it. Studies of US pension and food-assistance payments found spending rose when the money arrived. Data from a personal-finance app showed much of that rise is just bills that fall due around payday, with the rest mostly among people who had less on hand.",
    "So a month where expenses run past income is common, and on its own it isn’t a verdict. That’s why this view draws both in one colour at two strengths, rather than as a good and a bad pair. They’re two different things that happen to share a chart.",
)
private const val UNEVEN_MONTHS_SOURCE = "Grounded in research on income and spending volatility, including the US Financial Diaries (Morduch & Schneider) and the JPMorgan Chase Institute, and on how spending responds to payday, including work by Stephens, Shapiro, and Gelman and colleagues."

private const val TIME_WORTH_SOURCE = "Grounded in research on what happens when women ask, including Babcock & Laschever’s Women Don’t Ask; on negotiation avoidance, including a 2025 paper in Negotiation and Conflict Management Research; and on Pew Research Center survey data."

val LEARN_TOPICS = listOf(
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
        "why-no-red", "Why nothing turns red", "Grounded in research on financial shame and avoidance, including work by Gladstone and colleagues.",
        listOf(
            "Most budgeting tools mark the moment you pass a limit, often in red, and often as the only feedback they give. Research on financial shame and the “ostrich effect” finds that feedback framed as failure tends to backfire: people who feel ashamed about money stop looking at it, and not looking makes the situation worse.",
            "So this app shows the same fact either way. Going past your plan is drawn as a tick at the plan and a hatched fill beyond it — on the same bar, in the same colours, in the same tone. The information is all there; the verdict is not.",
            "The pattern is a shape rather than a colour, so it still reads with colour blindness.",
        ),
    ),
    LearnTopic(
        "borrowing", "What borrowing really costs", BORROWING_SOURCE, BORROWING_BODY,
        calculators = listOf("Work out what a loan costs" to Route.LoanCalc, "Work out what a purchase on a card costs" to Route.PurchaseCalc),
    ),
    LearnTopic("credit-score", CREDIT_TITLE, "Credit invisibility — research by the Consumer Financial Protection Bureau", CREDIT_BODY, CREDIT_RESOURCES),
    LearnTopic("money-on-hand", "Why money on hand matters", MONEY_ON_HAND_SOURCE, MONEY_ON_HAND_BODY),
    LearnTopic("uneven-months", "Why uneven months are normal", UNEVEN_MONTHS_SOURCE, UNEVEN_MONTHS_BODY),
    LearnTopic(
        "well-being", "Financial well-being over adherence", "Grounded in the Consumer Financial Protection Bureau’s research defining financial well-being.",
        listOf(
            "Many budgeting apps quietly define success as adherence: did you follow the plan exactly. That means you’ve failed the moment real life doesn’t cooperate, which it often doesn’t.",
            "Research defines financial well-being differently: as a sense of security and freedom of choice, not as a score against a plan.",
            "That’s why no month here is graded, and why Reports open on how long your Savings would cover you rather than on a spending breakdown. The numbers are information, not a report card.",
        ),
    ),
    LearnTopic(
        "time-worth", "What your time is worth, and why asking is hard", TIME_WORTH_SOURCE, TIME_WORTH_BODY,
        calculators = listOf("Work out what your extra hours come to" to Route.TimeWorthCalc),
    ),
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
                    TopicBody(topic, openRoute, initialListOpen)
                }
            }
        }
        SectionLabel("Calculators")
        NavRow(Lucide.LANDMARK, "What a loan actually costs", "Compare financing against saving", decorative = true) { openRoute(Route.LoanCalc) }
        NavRow(Lucide.CREDIT_CARD, "Putting a purchase on a card", "What financing this one thing costs", decorative = true) { openRoute(Route.PurchaseCalc) }
        NavRow(Lucide.ZAP, "What your time is worth", "What extra hours come to", decorative = true) { openRoute(Route.TimeWorthCalc) }
    }
}

/** A topic's text, its country list or calculator where it has one, and its source line: the same in place and in the pane. */
@Composable
private fun TopicBody(topic: LearnTopic, openRoute: (Route) -> Unit, initialListOpen: Boolean = false) {
    val t = LocalTokens.current; val p = t.palette
    for (para in topic.body) Text(para, color = p.txt2, fontSize = t.type.body, lineHeight = t.type.body * 1.6, modifier = Modifier.measureCap(t))
    if (topic.resources.isNotEmpty()) CreditResourceList(topic.resources, initialListOpen)
    for ((label, route) in topic.calculators) LearnLink(label, { openRoute(route) })
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
        Column(verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) { TopicBody(topic, opener(store)) }
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
