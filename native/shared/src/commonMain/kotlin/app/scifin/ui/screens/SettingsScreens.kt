package app.scifin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.scifin.app.AppStore
import app.scifin.app.Route
import app.scifin.app.SettingsPage
import app.scifin.app.Sheet
import app.scifin.app.Tab
import app.scifin.app.UiState
import app.scifin.model.Density
import app.scifin.model.IncomeCadence
import app.scifin.model.ProfileData
import app.scifin.model.ThemeMode
import app.scifin.notify.NotificationPermission
import app.scifin.ui.LocalSecondColumn
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.opener
import app.scifin.ui.shownDetail
import app.scifin.ui.components.AddRow
import app.scifin.ui.components.Avatar
import androidx.compose.runtime.saveable.rememberSaveable
import app.scifin.ui.components.Button
import app.scifin.ui.components.ButtonKind
import app.scifin.ui.components.Caption
import app.scifin.ui.components.ChipRow
import app.scifin.ui.components.EntityIcon
import app.scifin.ui.components.DateField
import app.scifin.ui.components.FeasibilityNote
import app.scifin.ui.components.FieldGroup
import app.scifin.ui.components.FieldLabel
import app.scifin.ui.components.InfoNote
import app.scifin.ui.components.NavRow
import app.scifin.ui.components.Page
import app.scifin.ui.components.PillToggle
import app.scifin.ui.components.RoundIconButton
import app.scifin.ui.components.SectionLabel
import app.scifin.ui.components.SettingRow
import app.scifin.ui.components.Switch
import app.scifin.ui.components.TextField
import app.scifin.ui.components.ThemeGrid
import app.scifin.ui.components.TopRow
import app.scifin.ui.currencySymbol
import app.scifin.ui.formatDate
import app.scifin.ui.formatDateFull
import app.scifin.ui.formatMoney
import app.scifin.ui.moneyText
import app.scifin.ui.parseMoney
import app.scifin.ui.tutAnchor

/*
 * §4.7: a hub of rows, not a flat column -- Appearance & accessibility → Income → Notifications
 * → the Savings vs. Owed toggle inline → Other. Each spoke is a full screen with a back chevron,
 * the shape every pushed screen in the app uses. Every label and caption is the prototype's.
 */

fun cadenceLabel(c: IncomeCadence) = when (c) {
    IncomeCadence.WEEKLY -> "Weekly"; IncomeCadence.BIWEEKLY -> "Every 2 weeks"; IncomeCadence.SEMIMONTHLY -> "Twice a month"; IncomeCadence.MONTHLY -> "Monthly"
}

fun ordinal(n: Int): String {
    val r = n % 100
    val suffix = if (r in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

// ---- the hub -----------------------------------------------------------------------------

@Composable
fun SettingsScreen(state: UiState, store: AppStore) {
    val d = state.data ?: return
    val t = LocalTokens.current
    val s = d.settings
    val modeCaption = when (s.mode) { ThemeMode.SYSTEM -> "Auto (${if (t.dark) "dark" else "light"})"; ThemeMode.DARK -> "Dark"; ThemeMode.LIGHT -> "Light" }
    val incomeCaption = if (s.incomeEnabled) "On · ${cadenceLabel(s.incomeCadence).lowercase()}" else "Off"
    val open = opener(store)
    // §4.8: the page the second column shows, marked on its row; null on a single column
    val shown = if (LocalSecondColumn.current) (shownDetail(state) as? Route.Settings)?.page else null
    Page(Tab.SETTINGS.label) {
        NavRow(Lucide.PALETTE, "Appearance & accessibility", "${s.theme.label} · $modeCaption", selected = shown == SettingsPage.APPEARANCE) { open(Route.Settings(SettingsPage.APPEARANCE)) }
        NavRow(Lucide.BANKNOTE, "Income", incomeCaption, selected = shown == SettingsPage.INCOME) { open(Route.Settings(SettingsPage.INCOME)) }
        NavRow(Lucide.BELL, "Notifications", if (s.newMonthNotificationEnabled) "New Month on" else "New Month off", selected = shown == SettingsPage.NOTIFICATIONS) { open(Route.Settings(SettingsPage.NOTIFICATIONS)) }
        SettingRow(Lucide.LANDMARK, "Show Savings vs. Owed in Reports", "Off by default — only useful once there’s a liability to weigh against") {
            Switch(s.savingsVsOwedEnabled) { store.updateSettings { it.copy(savingsVsOwedEnabled = !it.savingsVsOwedEnabled) } }
        }
        SectionLabel("Other")
        val exported = state.appState?.lastBackupExportDate
        NavRow(Lucide.DATABASE, "Data & backup", if (exported != null) "Exported ${formatDate(exported)}" else "Export or restore", selected = shown == SettingsPage.BACKUP) { open(Route.Settings(SettingsPage.BACKUP)) }
        NavRow(Lucide.LOCK, "Security & lock", if (state.lock != null) "PIN on" else "Off", selected = shown == SettingsPage.SECURITY) { open(Route.Settings(SettingsPage.SECURITY)) }
        NavRow(Lucide.ROTATE_CCW, "Replay onboarding", "View", selected = shown == SettingsPage.REPLAY) { open(Route.Settings(SettingsPage.REPLAY)) }
        val total = state.profiles.size
        Box(Modifier.tutAnchor("profiles-hook")) { NavRow(null, "Profiles", d.profile.name + (if (total > 1) " · $total total" else ""), leading = { Avatar(d.profile.name.take(1).ifEmpty { "Y" }) }, selected = shown == SettingsPage.PROFILES) { open(Route.Settings(SettingsPage.PROFILES)) } }
    }
}

// ---- the spokes ----------------------------------------------------------------------------

@Composable
fun SettingsPageScreen(route: Route.Settings, state: UiState, store: AppStore) {
    val d = state.data ?: return
    val title = if (route.page == SettingsPage.BACKUP && (state.pendingBackup != null || state.lockedBackup != null)) "Restore backup" else route.page.title
    Page(null) {
        TopRow(title, onBack = { store.back() })
        when (route.page) {
            SettingsPage.APPEARANCE -> AppearancePage(d, store)
            SettingsPage.INCOME -> IncomePage(d, store)
            SettingsPage.NOTIFICATIONS -> NotificationsPage(d, state, store)
            SettingsPage.BACKUP -> BackupPage(state, store)
            SettingsPage.SECURITY -> SecurityPage(state, store)
            SettingsPage.PROFILES -> ProfilesPage(state, store)
            SettingsPage.REPLAY -> ReplaySummaryPage(store)
        }
    }
}

@Composable
internal fun AppearancePage(d: ProfileData, store: AppStore, currencyFirst: Boolean = false) {
    val t = LocalTokens.current
    val s = d.settings
    Column(Modifier.tutAnchor("appearance-section"), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        for (section in appearanceSections(currencyFirst)) when (section) {
            "look" -> {
                SectionLabel("Look")
                Box(Modifier.padding(bottom = t.spacing.gap * 1.2f)) {
                    ThemeGrid(s.theme) { th -> store.updateSettings { it.copy(theme = th) } }
                }
            }
            "mode" -> SettingRow(if (t.dark) Lucide.MOON else Lucide.SUN, "Appearance", "System follows your device") {
                PillToggle(listOf("system" to "Auto", "light" to "Light", "dark" to "Dark"), s.mode.token) { v -> store.updateSettings { it.copy(mode = ThemeMode.of(v)) } }
            }
            "currency" -> SettingRow(Lucide.BANKNOTE, "Currency", "Symbol only — amounts don’t convert") {
                PillToggle(listOf("USD" to "$", "EUR" to "€"), s.currency) { v -> store.updateSettings { it.copy(currency = v) } }
            }
            "density" -> SettingRow(Lucide.SLIDERS_HORIZONTAL, "Density", "Spacing between rows") {
                PillToggle(listOf("comfortable" to "Comfortable", "compact" to "Compact"), s.density.token) { v -> store.updateSettings { it.copy(density = Density.of(v)) } }
            }
            "motion" -> SettingRow(if (s.reduceMotion) Lucide.ZAP_OFF else Lucide.ZAP, "Motion", "Interface animation") {
                Switch(!s.reduceMotion) { store.updateSettings { it.copy(reduceMotion = !it.reduceMotion) } }
            }
            "distraction" -> SettingRow(if (s.lowDistraction) Lucide.EYE_OFF else Lucide.EYE, "Low-distraction mode", "Hides decorative detail") {
                Switch(s.lowDistraction) { store.updateSettings { it.copy(lowDistraction = !it.lowDistraction) } }
            }
        }
    }
}

/**
 * The Appearance page's sections in the order they are shown. The onboarding landing (§11 step 4)
 * asks about currency, so there -- and only there -- Currency leads.
 */
fun appearanceSections(currencyFirst: Boolean): List<String> {
    val base = listOf("look", "mode", "currency", "density", "motion", "distraction")
    return if (currencyFirst) listOf("currency") + base.filter { it != "currency" } else base
}

@Composable
private fun IncomePage(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current
    val s = d.settings
    SettingRow(Lucide.BANKNOTE, "Income", "Off by default — cleared, not remembered, on toggle-off") {
        Switch(s.incomeEnabled) { store.setIncomeEnabled(!s.incomeEnabled) }
    }
    if (!s.incomeEnabled) return
    val semi = s.incomeCadence == IncomeCadence.SEMIMONTHLY
    // A local draft, committed on every valid change and deliberately NOT keyed on s.income: re-keying resets the field mid-typing and drops digits.
    var amountText by remember { mutableStateOf(if (s.income > 0) moneyText(s.income) else "") }
    FieldGroup {
        FieldLabel("Per-paycheck amount")
        TextField(amountText, { v ->
            amountText = v
            val m = if (v.isBlank()) 0L else parseMoney(v) ?: return@TextField
            store.updateSettings { it.copy(income = m) }
        }, prefix = currencySymbol(s.currency), keyboard = KeyboardType.Decimal, label = "Per-paycheck amount")
    }
    FieldGroup {
        FieldLabel("Cadence")
        ChipRow(IncomeCadence.entries.map { it.token to cadenceLabel(it) }, s.incomeCadence.token) { v -> store.setIncomeCadence(IncomeCadence.of(v)) }
    }
    FieldGroup {
        FieldLabel(if (semi) "First payday" else "Next payday")
        // §3.3: empty until chosen -- the field shows what the engine has, and nothing deposits without it
        DateField(s.incomePayDate, placeholder = "Choose a date") { day -> store.updateSettings { it.copy(incomePayDate = day) } }
    }
    if (semi) FieldGroup {
        FieldLabel("Second payday")
        DateField(s.incomePayDate2, placeholder = "Choose a date") { day -> store.updateSettings { it.copy(incomePayDate2 = day) } }
    }
    SettingRow(Lucide.REPEAT, "Auto-deposit every payday", "Off by default — manual keeps the pre-fill, not the auto-tag") {
        Switch(s.incomeAutoDeposit) { store.setAutoDeposit(!s.incomeAutoDeposit) }
    }
    if (!s.incomeAutoDeposit) Caption(
        "Manual path: the Savings card’s Income button pre-fills ${formatMoney(s.income, s.currency)}, and a “no income logged” note appears if a paycheck goes unrecorded.",
        Modifier.padding(start = t.spacing.u * 0.25f, bottom = t.spacing.gap),
    )
}

@Composable
private fun NotificationsPage(d: ProfileData, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val s = d.settings
    SettingRow(Lucide.BELL, "New Month", "1st of each month — “Fresh start, ready when you are”") {
        Switch(s.newMonthNotificationEnabled) { store.updateSettings { it.copy(newMonthNotificationEnabled = !it.newMonthNotificationEnabled) } }
    }
    // §11.5 / §4.7: the one line that says plainly when nothing will display
    if (state.notificationPermission == NotificationPermission.DENIED) FeasibilityNote("Notifications are turned off for Sci-Fin in your system settings — New Month and due-date reminders won’t appear.")
    val imp = d.importState
    SettingRow(Lucide.DATABASE, "Remind me to import", "Once a month — off unless you ask for it") {
        Switch(imp.importReminderEnabled) { store.updateImportState { it.copy(importReminderEnabled = !it.importReminderEnabled) } }
    }
    if (imp.importReminderEnabled) Caption(
        "On the ${ordinal(imp.importReminderDay)} of each month: “Ready to import last month’s statement?” Nothing happens on its own — it opens the picker.",
        Modifier.padding(start = t.spacing.u * 0.25f, bottom = t.spacing.gap),
    )
    Caption("Per-expense due-date reminders are set on each Recurring Expense, since their lead time is per-item.", Modifier.padding(start = t.spacing.u * 0.25f, top = t.spacing.gap))
}

@Composable
private fun BackupPage(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    if (BackupOpen(state, store)) return
    var exporting by rememberSaveable { mutableStateOf(false) }
    if (exporting) { ExportStep(state, store) { exporting = false }; return }
    InfoNote("A backup is one file holding every profile, every transaction and every setting. It stays where you put it — nothing is sent anywhere.")
    Button("Export a backup", ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth().padding(top = t.spacing.gap)) { exporting = true }
    Caption(
        if (state.lock != null) "The file is written with a password while the app PIN is on." else "You can add a password to the file on the next step.",
        Modifier.padding(start = t.spacing.u * 0.25f, top = t.spacing.u / 2),
    )
    state.appState?.lastBackupExportDate?.let { Caption("Last export: ${formatDateFull(it)}.", Modifier.padding(start = t.spacing.u * 0.25f, top = t.spacing.u / 2)) }
    Column(Modifier.padding(top = t.spacing.pad)) {
        FieldLabel("Restore from a backup")
        Button("Choose a backup file", ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth()) { store.pickBackup() }
    }
    state.backupError?.let { FeasibilityNote(it, Modifier.padding(top = t.spacing.gap)) }
    Caption("Restoring replaces what is on this device; you will be shown what the file holds and asked before anything changes. A file with a password asks for it first.", Modifier.padding(top = t.spacing.gap))
    state.appState?.lastBackupImportDate?.let { Caption("Last restore: ${formatDateFull(it)}.", Modifier.padding(top = t.spacing.gap)) }
}

@Composable
private fun ProfilesPage(state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val active = state.data ?: return
    for (prof in state.profiles) {
        val isActive = prof.id == active.profileId
        Row(
            Modifier.fillMaxWidth().heightIn(min = t.spacing.target).background(p.surf2, RoundedCornerShape(t.radii.lg))
                .let { if (isActive) it else it.clickable(onClickLabel = "Switch to ${prof.name}", role = Role.Button) { store.switchProfile(prof.id) } }
                .padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.125f)) {
                EntityIcon(if (isActive) Lucide.CHECK else Lucide.USER_CIRCLE_2)
                Text(prof.name, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.25f)) {
                Column(horizontalAlignment = Alignment.End) {
                    // only the active Profile's Savings is loaded; the others show no figure
                    Text(if (isActive) active.savings.amount?.let { formatMoney(it, active.settings.currency) } ?: "—" else "", color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold)
                    Text(if (isActive) "Open now" else "Switch →", color = p.txt2, fontSize = t.type.caption)
                }
                RoundIconButton(Lucide.PENCIL, "Rename", Modifier.zIndex(1f)) { store.openSheet(Sheet.RenameProfile(prof.id)) }
                if (state.profiles.size > 1) RoundIconButton(Lucide.TRASH_2, "Delete") { store.openSheet(Sheet.DeleteProfile(prof.id)) }
            }
        }
    }
    // §8.3: the two notes, read before "New profile" is tapped
    FeasibilityNote("A profile is for a genuinely separate pool of money — someone else’s, something you hold for someone else, or your own money bound by rules that make it wrong to mix with everyday Savings. A trust, an inheritance, a joint account.")
    InfoNote("Money you have simply set aside mentally isn’t this — it is still your everyday money, and a goal without a target fits it better. This starts a separate, empty environment with its own everything.")
    AddRow("New profile") { store.push(Route.Onboarding(1, forNewProfile = true)) }
    InfoNote("Each profile is a separate environment, not a second view of the same money — its own Savings, ledger, reports and settings, right down to density and currency. Switching changes everything the app shows.")
}

/** §11.6's condensed replay page: a static recap, one line per stop, with the replay button under it. */
@Composable
private fun ReplaySummaryPage(store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    InfoNote("A static recap — not a live re-run. Replay the full tour below any time, and nothing here resets your data.")
    // the prototype's nine lines, verbatim -- one per stop of TUTORIAL_STOPS, Profiles included (§11.6)
    val stops = listOf(
        Triple(Lucide.WALLET, "Savings", "What you have available — never inflated."),
        Triple(Lucide.BANKNOTE, "Income", "Off by default. Manual or auto-deposit, your choice."),
        Triple(Lucide.REPEAT, "Recurring & Goals", "Recurring deducts automatically; Goals are yours to add to anytime."),
        Triple(Lucide.TAG, "Categories", "What you set aside and what you spent."),
        Triple(Lucide.CREDIT_CARD, "Activity", "A clear window on credit cards and loans, no noise."),
        Triple(Lucide.LIGHTBULB, "Learn", "The science behind the app — look here first."),
        Triple(Lucide.BAR_CHART_3, "Reports", "Visual and written syntheses of money in relation to time."),
        Triple(Lucide.SLIDERS_HORIZONTAL, "Appearance", "Density, motion, low-distraction — all personal."),
        Triple(Lucide.USERS, "Profiles", "For money bound by different rules — someone else’s, or your own held apart."),
    )
    Column(Modifier.padding(top = t.spacing.gap), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        for ((icon, title, body) in stops) Row(Modifier.fillMaxWidth().background(p.surf, RoundedCornerShape(t.radii.lg)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.125f)) {
            EntityIcon(icon)
            Column { Text(title, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold); Text(body, color = p.txt2, fontSize = t.type.caption) }
        }
    }
    Button("Replay the full tour", modifier = Modifier.fillMaxWidth().padding(top = t.spacing.gap)) { store.startTutorial(fromOnboarding = false) }
}
