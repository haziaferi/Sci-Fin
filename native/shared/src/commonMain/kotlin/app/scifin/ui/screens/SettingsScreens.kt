package app.scifin.ui.screens

import app.scifin.i18n.SUPPORTED
import app.scifin.i18n.Language
import app.scifin.i18n.I18n
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
import app.scifin.engine.Hourly
import app.scifin.i18n.tr
import app.scifin.i18n.trf
import app.scifin.i18n.trp
import app.scifin.model.IncomeType
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
    IncomeCadence.WEEKLY -> tr("Weekly"); IncomeCadence.BIWEEKLY -> tr("Every 2 weeks"); IncomeCadence.SEMIMONTHLY -> tr("Twice a month"); IncomeCadence.MONTHLY -> tr("Monthly")
}

fun ordinal(n: Int): String {
    // §2.7: Italian names the day by its number ("il giorno 5"); the suffix is English's
    if (I18n.current == Language.IT) return n.toString()
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
    val modeCaption = when (s.mode) { ThemeMode.SYSTEM -> if (t.dark) tr("Auto (dark)") else tr("Auto (light)"); ThemeMode.DARK -> tr("Dark"); ThemeMode.LIGHT -> tr("Light") }
    val incomeCaption = if (s.incomeEnabled) (if (s.incomeType == IncomeType.HOURLY) trf("On · by the hour, {0}", cadenceLabel(s.incomeCadence).lowercase()) else trf("On · {0}", cadenceLabel(s.incomeCadence).lowercase())) else tr("Off")
    val open = opener(store)
    // §4.8: the page the second column shows, marked on its row; null on a single column
    val shown = if (LocalSecondColumn.current) (shownDetail(state) as? Route.Settings)?.page else null
    Page(Tab.SETTINGS.label) {
        NavRow(Lucide.PALETTE, tr("Appearance & accessibility"), "${s.theme.label} · $modeCaption", selected = shown == SettingsPage.APPEARANCE) { open(Route.Settings(SettingsPage.APPEARANCE)) }
        NavRow(Lucide.BANKNOTE, tr("Income"), incomeCaption, selected = shown == SettingsPage.INCOME) { open(Route.Settings(SettingsPage.INCOME)) }
        NavRow(Lucide.BELL, tr("Notifications"), if (s.newMonthNotificationEnabled) tr("New Month on") else tr("New Month off"), selected = shown == SettingsPage.NOTIFICATIONS) { open(Route.Settings(SettingsPage.NOTIFICATIONS)) }
        SettingRow(Lucide.LANDMARK, tr("Show Savings vs. Owed in Reports"), tr("Off by default — only useful once there’s a liability to weigh against")) {
            Switch(s.savingsVsOwedEnabled) { store.updateSettings { it.copy(savingsVsOwedEnabled = !it.savingsVsOwedEnabled) } }
        }
        SectionLabel(tr("Other"))
        val exported = state.appState?.lastBackupExportDate
        NavRow(Lucide.DATABASE, tr("Data & backup"), if (exported != null) trf("Exported {0}", formatDate(exported)) else tr("Export or restore"), selected = shown == SettingsPage.BACKUP) { open(Route.Settings(SettingsPage.BACKUP)) }
        NavRow(Lucide.LOCK, tr("Security & lock"), if (state.lock != null) tr("PIN on") else tr("Off"), selected = shown == SettingsPage.SECURITY) { open(Route.Settings(SettingsPage.SECURITY)) }
        NavRow(Lucide.TRASH_2, tr("Trash"), if (state.trash.isEmpty()) tr("Empty") else trp("{0} item", "{0} items", state.trash.size), selected = shown == SettingsPage.TRASH) { open(Route.Settings(SettingsPage.TRASH)) }
        NavRow(Lucide.ROTATE_CCW, tr("Replay onboarding"), tr("View"), selected = shown == SettingsPage.REPLAY) { open(Route.Settings(SettingsPage.REPLAY)) }
        val total = state.profiles.size
        Box(Modifier.tutAnchor("profiles-hook")) { NavRow(null, tr("Profiles"), if (total > 1) trf("{0} · {1} total", d.profile.name, total) else d.profile.name, leading = { Avatar(d.profile.name.take(1).ifEmpty { "Y" }) }, selected = shown == SettingsPage.PROFILES) { open(Route.Settings(SettingsPage.PROFILES)) } }
    }
}

// ---- the spokes ----------------------------------------------------------------------------

@Composable
fun SettingsPageScreen(route: Route.Settings, state: UiState, store: AppStore) {
    val d = state.data ?: return
    val title = if (route.page == SettingsPage.BACKUP && (state.pendingBackup != null || state.lockedBackup != null)) tr("Restore backup") else route.page.title
    Page(null) {
        TopRow(title, onBack = { store.back() })
        when (route.page) {
            SettingsPage.APPEARANCE -> AppearancePage(d, store)
            SettingsPage.INCOME -> IncomePage(d, store)
            SettingsPage.NOTIFICATIONS -> NotificationsPage(d, state, store)
            SettingsPage.BACKUP -> BackupPage(state, store)
            SettingsPage.SECURITY -> SecurityPage(state, store)
            SettingsPage.TRASH -> TrashPage(state, store)
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
                SectionLabel(tr("Look"))
                Box(Modifier.padding(bottom = t.spacing.gap * 1.2f)) {
                    ThemeGrid(s.theme) { th -> store.updateSettings { it.copy(theme = th) } }
                }
            }
            "mode" -> SettingRow(if (t.dark) Lucide.MOON else Lucide.SUN, tr("Appearance"), tr("System follows your device")) {
                PillToggle(listOf("system" to tr("Auto"), "light" to tr("Light"), "dark" to tr("Dark")), s.mode.token) { v -> store.updateSettings { it.copy(mode = ThemeMode.of(v)) } }
            }
            // §2.7: the device's language unless this Profile chooses one; each language is named in itself
            "language" -> if (SUPPORTED.size > 1) FieldGroup {
                FieldLabel(tr("Language"))
                ChipRow(listOf("" to trf("Device ({0})", I18n.device().label)) + SUPPORTED.map { it.tag to it.label }, s.language ?: "") { v ->
                    store.updateSettings { it.copy(language = v.ifEmpty { null }) }
                }
            }
            "currency" -> SettingRow(Lucide.BANKNOTE, tr("Currency"), tr("Symbol only — amounts don’t convert")) {
                PillToggle(listOf("USD" to "$", "EUR" to "€"), s.currency) { v -> store.updateSettings { it.copy(currency = v) } }
            }
            "density" -> SettingRow(Lucide.SLIDERS_HORIZONTAL, tr("Density"), tr("Spacing between rows")) {
                PillToggle(listOf("comfortable" to tr("Comfortable"), "compact" to tr("Compact")), s.density.token) { v -> store.updateSettings { it.copy(density = Density.of(v)) } }
            }
            "motion" -> SettingRow(if (s.reduceMotion) Lucide.ZAP_OFF else Lucide.ZAP, tr("Motion"), tr("Interface animation")) {
                Switch(!s.reduceMotion) { store.updateSettings { it.copy(reduceMotion = !it.reduceMotion) } }
            }
            "distraction" -> SettingRow(if (s.lowDistraction) Lucide.EYE_OFF else Lucide.EYE, tr("Low-distraction mode"), tr("Hides decorative detail")) {
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
    val base = listOf("look", "mode", "language", "currency", "density", "motion", "distraction")
    return if (currencyFirst) listOf("currency") + base.filter { it != "currency" } else base
}

@Composable
private fun IncomePage(d: ProfileData, store: AppStore) {
    val t = LocalTokens.current
    val s = d.settings
    SettingRow(Lucide.BANKNOTE, tr("Income"), tr("Off by default — cleared, not remembered, on toggle-off")) {
        Switch(s.incomeEnabled) { store.setIncomeEnabled(!s.incomeEnabled) }
    }
    if (!s.incomeEnabled) return
    val semi = s.incomeCadence == IncomeCadence.SEMIMONTHLY
    val hourly = s.incomeType == IncomeType.HOURLY
    // §3.10 (owner, 2026-10-26): a known paycheck, or hours logged and confirmed each payday
    FieldGroup {
        FieldLabel(tr("Paid"))
        ChipRow(listOf(IncomeType.SALARIED.token to tr("A set amount"), IncomeType.HOURLY.token to tr("By the hour")), s.incomeType.token) { v -> store.setIncomeType(IncomeType.of(v)) }
    }
    if (hourly) {
        // the same local-draft rule as the paycheck amount below
        var rateText by remember { mutableStateOf(if (s.hourlyRate > 0) moneyText(s.hourlyRate) else "") }
        FieldGroup {
            FieldLabel(tr("Per hour"))
            TextField(rateText, { v ->
                rateText = v
                val m = if (v.isBlank()) 0L else parseMoney(v) ?: return@TextField
                store.setHourlyRate(m)
            }, prefix = currencySymbol(s.currency), keyboard = KeyboardType.Decimal, label = tr("Per hour"))
            Caption(tr("Each shift starts from this rate and can be changed when you log it."),Modifier.padding(top = t.spacing.u * 0.75f))
        }
    } else {
        // A local draft, committed on every valid change and deliberately NOT keyed on s.income: re-keying resets the field mid-typing and drops digits.
        var amountText by remember { mutableStateOf(if (s.income > 0) moneyText(s.income) else "") }
        FieldGroup {
            FieldLabel(tr("Per-paycheck amount"))
            TextField(amountText, { v ->
                amountText = v
                val m = if (v.isBlank()) 0L else parseMoney(v) ?: return@TextField
                store.updateSettings { it.copy(income = m) }
            }, prefix = currencySymbol(s.currency), keyboard = KeyboardType.Decimal, label = tr("Per-paycheck amount"))
        }
    }
    FieldGroup {
        FieldLabel(tr("Cadence"))
        ChipRow(IncomeCadence.entries.map { it.token to cadenceLabel(it) }, s.incomeCadence.token) { v -> store.setIncomeCadence(IncomeCadence.of(v)) }
    }
    FieldGroup {
        FieldLabel(if (semi) tr("First payday") else tr("Next payday"))
        // §3.3: empty until chosen -- the field shows what the engine has, and nothing deposits without it
        DateField(s.incomePayDate, placeholder = tr("Choose a date")) { day -> store.updateSettings { it.copy(incomePayDate = day) } }
    }
    if (semi) FieldGroup {
        FieldLabel(tr("Second payday"))
        DateField(s.incomePayDate2, placeholder = tr("Choose a date")) { day -> store.updateSettings { it.copy(incomePayDate2 = day) } }
    }
    if (hourly) {
        Caption(
            tr("Log hours whenever you work them; they reach Savings on payday, when you confirm what arrived. The estimate is hours × rate before deductions until your first payday, then it follows what your recent paydays brought."),
            Modifier.padding(start = t.spacing.u * 0.25f, bottom = t.spacing.gap),
        )
        return
    }
    SettingRow(Lucide.REPEAT, tr("Auto-deposit every payday"), tr("Off by default — manual keeps the pre-fill, not the auto-tag")) {
        Switch(s.incomeAutoDeposit) { store.setAutoDeposit(!s.incomeAutoDeposit) }
    }
    if (!s.incomeAutoDeposit) Caption(
        trf("Manual path: the Savings card’s Income button pre-fills {0}, and a “no income logged” note appears if a paycheck goes unrecorded.", formatMoney(s.income, s.currency)),
        Modifier.padding(start = t.spacing.u * 0.25f, bottom = t.spacing.gap),
    )
}

@Composable
private fun NotificationsPage(d: ProfileData, state: UiState, store: AppStore) {
    val t = LocalTokens.current
    val s = d.settings
    SettingRow(Lucide.BELL, tr("New Month"), tr("1st of each month — “Fresh start, ready when you are”")) {
        Switch(s.newMonthNotificationEnabled) { store.updateSettings { it.copy(newMonthNotificationEnabled = !it.newMonthNotificationEnabled) } }
    }
    // §11.5 / §4.7: the one line that says plainly when nothing will display
    if (state.notificationPermission == NotificationPermission.DENIED) FeasibilityNote(tr("Notifications are turned off for Sci-Fin in your system settings — New Month and due-date reminders won’t appear."))
    val imp = d.importState
    SettingRow(Lucide.DATABASE, tr("Remind me to import"), tr("Once a month — off unless you ask for it")) {
        Switch(imp.importReminderEnabled) { store.updateImportState { it.copy(importReminderEnabled = !it.importReminderEnabled) } }
    }
    if (imp.importReminderEnabled) Caption(
        trf("On the {0} of each month: “Ready to import last month’s statement?” Nothing happens on its own — it opens the picker.", ordinal(imp.importReminderDay)),
        Modifier.padding(start = t.spacing.u * 0.25f, bottom = t.spacing.gap),
    )
    // §3.10: only while income is not recorded by itself (owner, 2026-10-26)
    if (Hourly.manualPayday(s)) SettingRow(Lucide.BANKNOTE, tr("Payday"), if (Hourly.isHourly(s)) tr("“Payday — confirm what arrived”") else tr("“Payday — log what arrived”")) {
        Switch(s.paydayNotificationEnabled) { store.updateSettings { it.copy(paydayNotificationEnabled = !it.paydayNotificationEnabled) } }
    }
    Caption(tr("Per-expense due-date reminders are set on each Recurring Expense, since their lead time is per-item."),Modifier.padding(start = t.spacing.u * 0.25f, top = t.spacing.gap))
}

@Composable
private fun BackupPage(state: UiState, store: AppStore) {
    val t = LocalTokens.current
    if (BackupOpen(state, store)) return
    var exporting by rememberSaveable { mutableStateOf(false) }
    if (exporting) { ExportStep(state, store) { exporting = false }; return }
    InfoNote(tr("A backup is one file holding every profile, every transaction and every setting. It stays where you put it — nothing is sent anywhere."))
    Button(tr("Export a backup"), ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth().padding(top = t.spacing.gap)) { exporting = true }
    Caption(
        if (state.lock != null) tr("The file is written with a password while the app PIN is on.") else tr("You can add a password to the file on the next step."),
        Modifier.padding(start = t.spacing.u * 0.25f, top = t.spacing.u / 2),
    )
    state.appState?.lastBackupExportDate?.let { Caption(trf("Last export: {0}.", formatDateFull(it)), Modifier.padding(start = t.spacing.u * 0.25f, top = t.spacing.u / 2)) }
    AutomaticCopy(state, store)
    Column(Modifier.padding(top = t.spacing.pad)) {
        FieldLabel(tr("Restore from a backup"))
        Button(tr("Choose a backup file"), ButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth()) { store.pickBackup() }
    }
    state.backupError?.let { FeasibilityNote(it, Modifier.padding(top = t.spacing.gap)) }
    Caption(tr("Restoring replaces what is on this device; you will be shown what the file holds and asked before anything changes. A file with a password asks for it first."), Modifier.padding(top = t.spacing.gap))
    state.appState?.lastBackupImportDate?.let { Caption(trf("Last restore: {0}.", formatDateFull(it)),Modifier.padding(top = t.spacing.gap)) }
}

@Composable
private fun ProfilesPage(state: UiState, store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    val active = state.data ?: return
    for (prof in state.profiles) {
        val isActive = prof.id == active.profileId
        Row(
            Modifier.fillMaxWidth().heightIn(min = t.spacing.target).background(p.surf2, RoundedCornerShape(t.radii.lg))
                .let { if (isActive) it else it.clickable(onClickLabel = trf("Switch to {0}", prof.name),role = Role.Button) { store.switchProfile(prof.id) } }
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
                    Text(if (isActive) tr("Open now") else tr("Switch →"), color = p.txt2, fontSize = t.type.caption)
                }
                RoundIconButton(Lucide.PENCIL, tr("Rename"), Modifier.zIndex(1f)) { store.openSheet(Sheet.RenameProfile(prof.id)) }
                if (state.profiles.size > 1) RoundIconButton(Lucide.TRASH_2, tr("Delete")) { store.openSheet(Sheet.DeleteProfile(prof.id)) }
            }
        }
    }
    // §8.3: the two notes, read before "New profile" is tapped
    FeasibilityNote(tr("A profile is for a genuinely separate pool of money — someone else’s, something you hold for someone else, or your own money bound by rules that make it wrong to mix with everyday Savings. A trust, an inheritance, a joint account."))
    InfoNote(tr("Money you have simply set aside mentally isn’t this — it is still your everyday money, and a goal without a target fits it better. This starts a separate, empty environment with its own everything."))
    AddRow(tr("New profile")) { store.push(Route.Onboarding(1, forNewProfile = true)) }
    InfoNote(tr("Each profile is a separate environment, not a second view of the same money — its own Savings, ledger, reports and settings, right down to density and currency. Switching changes everything the app shows."))
}

/** §11.6's condensed replay page: a static recap, one line per stop, with the replay button under it. */
@Composable
private fun ReplaySummaryPage(store: AppStore) {
    val t = LocalTokens.current; val p = t.palette
    InfoNote(tr("A static recap — not a live re-run. Replay the full tour below any time, and nothing here resets your data."))
    // the prototype's nine lines, verbatim -- one per stop of TUTORIAL_STOPS, Profiles included (§11.6)
    val stops = listOf(
        Triple(Lucide.WALLET, tr("Savings"), tr("What you have available — never inflated.")),
        Triple(Lucide.BANKNOTE, tr("Income"), tr("Off by default. Manual or auto-deposit, your choice.")),
        Triple(Lucide.REPEAT, tr("Recurring & Goals"), tr("Recurring deducts automatically; Goals are yours to add to anytime.")),
        Triple(Lucide.TAG, tr("Categories"), tr("What you set aside and what you spent.")),
        Triple(Lucide.CREDIT_CARD, tr("Activity"), tr("A clear window on credit cards and loans, no noise.")),
        Triple(Lucide.LIGHTBULB, tr("Learn"), tr("The science behind the app — look here first.")),
        Triple(Lucide.BAR_CHART_3, tr("Reports"), tr("Visual and written syntheses of money in relation to time.")),
        Triple(Lucide.SLIDERS_HORIZONTAL, tr("Appearance"), tr("Density, motion, low-distraction — all personal.")),
        Triple(Lucide.USERS, tr("Profiles"), tr("For money bound by different rules — someone else’s, or your own held apart.")),
    )
    Column(Modifier.padding(top = t.spacing.gap), verticalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        for ((icon, title, body) in stops) Row(Modifier.fillMaxWidth().background(p.surf, RoundedCornerShape(t.radii.lg)).padding(horizontal = t.spacing.u * 1.5f, vertical = t.spacing.u * 1.25f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u * 1.125f)) {
            EntityIcon(icon)
            Column { Text(title, color = p.txt, fontSize = t.type.body, fontWeight = FontWeight.Bold); Text(body, color = p.txt2, fontSize = t.type.caption) }
        }
    }
    Button(tr("Replay the full tour"),modifier = Modifier.fillMaxWidth().padding(top = t.spacing.gap)) { store.startTutorial(fromOnboarding = false) }
}
