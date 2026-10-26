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
        lines = listOf(tr("Recurring Expenses: bills and payments on a regular schedule, for a fixed amount. What’s due this month is logged as leaving Savings on the 1st, ahead of the actual due dates spread over the following 30 days or so; anything paid less often is set aside automatically in equal parts each month."))),
    TutorialStop(Tab.BUDGET, "categories", anchor = "categories-list",
        lines = listOf(tr("What you set aside and what you spent. Categories give every purchase a place, month after month. Every entry is a piece of the puzzle."))),
    TutorialStop(Tab.ACTIVITY, anchor = "accounts-section",
        lines = listOf(tr("A view of credit cards and loans, to keep the payments in check."))),
    TutorialStop(Tab.LEARN, anchor = null,
        lines = listOf(tr("The science behind the app and its features. If something needs explaining, you can read about it here."))),
    TutorialStop(Tab.REPORTS, anchor = "reports-seg",
        lines = listOf(tr("Here you find visual and written summaries of how money moves over time."))),
    TutorialStop(Tab.SETTINGS, settingsPage = SettingsPage.APPEARANCE, anchor = "appearance-section",
        lines = listOf(tr("Accessibility can be personalized by density, reduced motion, and low-distraction mode — text follows your phone’s own size."))),
    // §11.6: the Profiles stop, on the hub's Profiles row
    TutorialStop(Tab.SETTINGS, anchor = "profiles-hook",
        lines = listOf(tr("For keeping apart money that is managed differently — a relative’s, money held for someone else, or your own under different rules, like an inheritance."))),
)
