package app.scifin.app

import app.scifin.engine.moneyText
import app.scifin.model.ProfileData
import app.scifin.i18n.tr
import app.scifin.i18n.trf

/**
 * §11.6's stops, in tour order: each a small window anchored to real UI. Where the real screen
 * is empty a single mockup item is drawn inside the window itself, never written to real state.
 * The copy is §11.6's, verbatim.
 */
data class TutorialStop(
    val tab: Tab,
    val sub: String? = null,
    val settingsPage: SettingsPage? = null,
    /** The `tutAnchor` id the window sits beside; null centres it (Learn). */
    val anchor: String?,
    /**
     * §11.6's mockup, drawn only while the value it stands in for is still unset -- "each stop's
     * mockup substitution is conditional on the underlying value still being unset" -- and in the
     * Profile's own currency.
     */
    val mock: ((ProfileData) -> String?)? = null,
    val lines: List<String>,
)

// a getter, so the copy follows the language in use
val TUTORIAL_STOPS: List<TutorialStop> get() = listOf(
    TutorialStop(Tab.BUDGET, "envelopes", anchor = "savings-row", mock = { d -> if (d.savings.amount == null) trf("Savings — {0}", moneyText(124000, d.settings.currency)) else null },
        lines = listOf(tr("Savings is what you have available. It will never display an inflated number."))),
    // §11.6: stops follow Envelopes' order (§4.2).
    TutorialStop(Tab.BUDGET, "envelopes", anchor = "goals-section",
        lines = listOf(tr("Goals: near or far. Add to one whenever you like, like a piggy bank."))),
    TutorialStop(Tab.BUDGET, "envelopes", anchor = "income-section", mock = { d -> if (!d.settings.incomeEnabled) tr("Income — off") else null },
        lines = listOf(tr("Off by default, you can turn it on in Settings: a set amount, deposited for you or logged by hand, or pay by the hour, logged as you work it."))),
    TutorialStop(Tab.BUDGET, "envelopes", anchor = "recurring-section",
        lines = listOf(tr("Recurring Expenses: your flat-fee bills. What’s due this month leaves Savings on the 1st, up front; anything billed less often is set aside a little each month — no surprise charges."))),
    TutorialStop(Tab.BUDGET, "categories", anchor = "categories-list",
        lines = listOf(tr("What you set aside and what you spent. Categories frame where all purchases fit, month after month. There are no one-off entries — they’re pieces of the puzzle."))),
    TutorialStop(Tab.ACTIVITY, anchor = "accounts-section",
        lines = listOf(tr("A clear window on credit cards and loans to help keep an eye on them, no noise."))),
    TutorialStop(Tab.LEARN, anchor = null,
        lines = listOf(tr("The science behind the app and its features. If anything requires an explanation, look here first."))),
    TutorialStop(Tab.REPORTS, anchor = "reports-seg",
        lines = listOf(tr("Here you will find visual and written syntheses of money in relation to time."))),
    TutorialStop(Tab.SETTINGS, settingsPage = SettingsPage.APPEARANCE, anchor = "appearance-section",
        lines = listOf(tr("Accessibility can be personalized by density, reduced motion, and low-distraction mode — text follows your phone’s own size."))),
    // §11.6: the Profiles stop, on the hub's Profiles row
    TutorialStop(Tab.SETTINGS, anchor = "profiles-hook",
        lines = listOf(tr("For money that has to stay separate — someone else’s, something held for someone else, or your own but bound by different rules, like an inheritance."))),
)
