package app.scifin.app

import app.scifin.data.SciFinDatabase
import app.scifin.engine.CatchUpSummary
import app.scifin.engine.AccountEditing
import app.scifin.engine.Accounts
import app.scifin.engine.Backup
import app.scifin.engine.CategoryEditing
import app.scifin.engine.Import
import app.scifin.engine.Ledger
import app.scifin.engine.LoanDraft
import app.scifin.engine.NewMonth
import app.scifin.engine.Recurring
import app.scifin.model.RecurringExpense
import app.scifin.engine.ReviewRow
import app.scifin.model.Account
import app.scifin.model.Money
import app.scifin.engine.BackupResult
import app.scifin.engine.Clock
import app.scifin.engine.GoalDraft
import app.scifin.engine.GoalEditing
import app.scifin.engine.Income
import app.scifin.engine.ValidatedBackup
import app.scifin.model.AppState
import app.scifin.model.ImportState
import app.scifin.model.IncomeCadence
import app.scifin.notify.NotificationPermission
import app.scifin.model.Profile
import app.scifin.model.ProfileData
import app.scifin.model.Settings
import app.scifin.model.Theme
import androidx.room.useWriterConnection
import app.scifin.engine.LockAfter
import app.scifin.engine.Pin
import app.scifin.engine.hintProblem
import app.scifin.model.AppLock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.plus
import kotlin.coroutines.cancellation.CancellationException

/** §4.3: the count and the two totals of a confirmed import. */
data class ImportDone(val count: Int, val out: Money, val inn: Money)

/**
 * Everything a screen can read, as one value. `data` is the active Profile's whole world
 * (§8.4's wrap); `routes` is where the app is; the rest is transient UI that must survive a
 * recomposition but never a restart.
 */
data class UiState(
    val ready: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    /** The one row above §8.4's wrap: the active Profile and the two backup dates. */
    val appState: AppState? = null,
    /** §4.7's review card: a validated backup waiting for Confirm, or the reason the file was refused. */
    val pendingBackup: ValidatedBackup? = null,
    val backupError: String? = null,
    val notificationPermission: NotificationPermission? = null,
    /** §4.3's review screen: the parsed, annotated rows waiting for Confirm, or why the file was refused. */
    val importReview: ImportReview? = null,
    /** What the last confirmed import wrote, for the screen to say so (in memory only; gone on the next open). */
    val importDone: ImportDone? = null,
    val importError: String? = null,
    val data: ProfileData? = null,
    val routes: List<Route> = listOf(Route.TabRoute(Tab.BUDGET)),
    val sheet: Sheet? = null,
    /** §11.1 screen 1's live preview: the theme under the sheet before any Profile holds it. */
    val previewTheme: Theme? = null,
    /** §11.6: the stop the tour is on, or null when it is not running; and whether it is step 3 of onboarding. */
    val tutorialStop: Int? = null,
    val tutorialFromOnboarding: Boolean = false,
    /** §12: the store was unreadable and moved aside as this copy; the screen that says so is owed. */
    val damagedCopy: String? = null,
    /** The routes Settings was opened from, which Back returns to; null elsewhere. */
    val settingsFrom: List<Route>? = null,
    /** §4.7's app lock as Settings and the gate need it -- never the PIN or its hash; null while no PIN is set. */
    val lock: LockSummary? = null,
    /** The gate is up: nothing beneath it is drawn. */
    val locked: Boolean = false,
    /** The last PIN tried, on the gate or to turn the lock off, was wrong. */
    val pinWrong: Boolean = false,
    /** While set and not yet passed, no PIN is checked (Pin.waitAfter). */
    val pinWaitEnds: TimeMark? = null,
) {
    val route: Route get() = routes.last()
    /** §3.9's unread card, read from where it is stored so a restart cannot lose it. */
    val catchUp: CatchUpSummary? get() = data?.settings?.let(NewMonth::persistedSummary)
    val tab: Tab get() = (routes.first() as Route.TabRoute).tab
    val sub: String? get() = (routes.first() as Route.TabRoute).sub
    /** The active Profile's settings, or the defaults while nothing is loaded -- the frame needs tokens before data; a preview theme wins while screen 1 is up. */
    val settings: Settings get() = (data?.settings ?: Settings(profileId = "")).let { s -> previewTheme?.let { s.copy(theme = it) } ?: s }
    /** A fresh install: nothing exists until §11.1's screen 1 creates the first Profile. */
    val needsFirstProfile: Boolean get() = ready && data == null
    /** §11.1's steps 2-4 are pages without the tab bar (the prototype clears it); screen 1 is a sheet over Budget and keeps it. */
    val inOnboardingPage: Boolean get() = (route as? Route.Onboarding)?.let { it.step > 1 } ?: false
}

/** §4.7: what is known about the lock outside the store -- enough to draw it, nothing to break it. */
data class LockSummary(val pinLength: Int, val hint: String?, val after: LockAfter)

private fun AppLock.summary() = LockSummary(pinLength, hint, LockAfter.of(lockAfter))

/** What a call with nothing to do returns, so a caller may still join it. */
private val DONE: Job = Job().apply { complete() }

private const val READ_FAILED = "Could not read that file. It may have moved, or the app may no longer have access to it."
private const val NOT_A_STATEMENT = "That file doesn’t look like a bank statement the app can read (CSV or OFX). Nothing was imported."

/** §4.3: a statement parsed and annotated, under review. */
data class ImportReview(val fileName: String, val format: String, val rows: List<ReviewRow>)

/**
 * The one place state changes. Screens read [state] and call the methods below; every write
 * to a Profile goes through [dispatch] as a pure function over [ProfileData] (the engine's
 * `Writes`, `Income`, `Goals`... are all of that shape), is saved whole through the DAO, and
 * re-arms the reminder schedule -- the prototype's `syncReminderSchedule()` on every path,
 * done once here instead. Testable without Compose: it is a class over a database and a scope.
 */
class AppStore(
    private val session: AppSession,
    private val db: SciFinDatabase,
    hostScope: CoroutineScope,
    private val files: Files = Files.None,
    private val damage: DamageNotice = DamageNotice.None,
    private val copies: LocalCopies = LocalCopies.None,
    /** §4.7's "lock after" and the wrong-PIN wait are timed on this, never on the wall clock a person can change. */
    private val time: TimeSource = TimeSource.Monotonic,
    /** Where the PIN is hashed: a third of a second must not land on the main thread. */
    private val hashing: CoroutineContext = Dispatchers.Default,
) {
    /**
     * The host's scope with a backstop: a write or a file that fails must not take the process
     * down with it. The paths the person starts by hand -- import, export, restore -- catch
     * their own failures below and say what went wrong on the screen they are on.
     */
    private val scope = hostScope + CoroutineExceptionHandler { _, e -> println("Sci-Fin: an action failed and was not applied: $e") }

    /** One file or store step the person started; a failure becomes [onFail]'s line instead of a crash. */
    private suspend fun <T> attempt(onFail: (String) -> Unit, message: String, block: suspend () -> T): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { onFail(message); null }

    private fun importFailed(m: String) = _state.update { it.copy(importReview = null, importError = m) }
    private fun backupFailed(m: String) = _state.update { it.copy(pendingBackup = null, backupError = m) }

    val clock: Clock get() = session.clock
    val newId: () -> String get() = session.newId

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private val writes = Mutex()

    /** §3.9's trigger is the app opening: run the session, then show what it loaded. */
    suspend fun start() {
        val data = session.open()
        // §11.1: a Profile exists from screen 1's Continue onward, so "no Profile" only means
        // screen 1. An unfinished flow is resumed from the step it stored (step 2 when none is) --
        // step 3 is the tour, which has no Route of its own and is started below.
        val resume = data?.settings?.takeIf { !it.onboardingComplete }?.let { it.onboardingStep ?: 2 }
        // §12's screen is owed only while nothing exists yet: a Profile made or restored since answered it
        val owed = if (data == null) damage.keptAs else { damage.clear(); null }
        // §4.7: every open of the app starts locked while a PIN is set, and a wait owed before it closed is owed again
        val lock = db.dao().appLock()
        _state.update {
            it.copy(
                lock = lock?.summary(), locked = lock != null, pinWaitEnds = lock?.let { l -> waitFrom(l.failedAttempts) },
                ready = true, data = data,
                damagedCopy = owed, profiles = db.dao().profiles(), appState = db.dao().appState(),
                notificationPermission = readPermission(data?.settings),
                routes = when {
                    data == null -> listOf(Route.TabRoute(Tab.BUDGET), Route.Onboarding(1))
                    resume != null && resume != 3 -> listOf(Route.TabRoute(Tab.BUDGET), Route.Onboarding(resume))
                    else -> it.routes
                },
            )
        }
        if (resume == 3) startTutorial(fromOnboarding = true)
    }

    /** After a restore (§4.7): everything on the device may have changed, so load it all again. */
    private suspend fun reloadAll() {
        val dao = db.dao()
        val d = dao.appState()?.let { session.activate(it.activeProfileId) }
        _state.update { it.forProfile(d).copy(profiles = dao.profiles(), appState = dao.appState()) }
    }

    /** A change of Profile keeps nothing read against the old one (§8.3); Settings' origin keeps only its tab. */
    private fun UiState.forProfile(d: ProfileData?): UiState = copy(
        data = d, sheet = null, importReview = null, importError = null, importDone = null,
        notificationPermission = readPermission(d?.settings),
        settingsFrom = settingsFrom?.let { listOf(it.first()) },
    )

    // ---- §11.1 ----------------------------------------------------------------------------

    fun previewTheme(theme: Theme) = _state.update { it.copy(previewTheme = theme) }

    /** Screen 1's Continue. The first Profile goes on to step 2; a later one (§8.3) straight to the Currency landing. */
    fun createProfile(name: String, theme: Theme, forNewProfile: Boolean): Job = scope.launch {
        writes.withLock {
            val d = session.createProfile(name, theme, first = !forNewProfile)
            val next = if (forNewProfile) Route.Onboarding(4, forNewProfile = true) else Route.Onboarding(2)
            _state.update { it.copy(data = d, profiles = db.dao().profiles(), appState = db.dao().appState(), previewTheme = null, routes = listOf(Route.TabRoute(Tab.BUDGET), next), sheet = null) }
        }
    }

    /**
     * The OS's answer, read now (§12: never trusted from storage). Android 13+ reports a denial
     * as NOT_ASKED, so once the stored answer shows the question was asked, NOT_ASKED means DENIED.
     */
    private fun readPermission(s: Settings?): NotificationPermission =
        session.notificationPermission().let { p -> if (p == NotificationPermission.NOT_ASKED && s?.notificationPermissionGranted != null) NotificationPermission.DENIED else p }

    /** The host calls this when the app returns to the foreground: the person may have changed it in system settings. */
    fun refreshPermission() = _state.update { it.copy(notificationPermission = readPermission(it.data?.settings)) }

    /** Step 2 on a host with a prompt: ask, record the answer on the Profile (§12), move on. */
    fun requestNotificationPermissionAndContinue(): Job = scope.launch {
        val answer = session.requestNotificationPermission()
        // asked and not granted is a denial, whatever Android 13+ reports it as afterwards
        _state.update { it.copy(notificationPermission = if (answer == NotificationPermission.NOT_ASKED) NotificationPermission.DENIED else answer) }
        dispatch { it.copy(settings = it.settings.copy(notificationPermissionGranted = answer == NotificationPermission.GRANTED)) }.join()
        // joined: the step marker is a Room write on Dispatchers.IO, and a caller awaiting this job must see it
        advanceOnboarding().join()
    }

    /** The next step of §11.1: 2 → 3 (the tour); 4 → home is [completeOnboarding]. */
    fun advanceOnboarding(): Job {
        val cur = _state.value.route as? Route.Onboarding ?: return DONE
        if (cur.step == 2) {
            val job = rememberOnboardingStep(3)
            startTutorial(fromOnboarding = true)
            return job
        }
        val job = rememberOnboardingStep(4)
        _state.update { st -> st.copy(routes = st.routes.dropLast(1) + Route.Onboarding(4, cur.forNewProfile)) }
        return job
    }

    /**
     * §11.1: store the step so a flow interrupted here resumes at it rather than vanishing.
     * Written straight to the store rather than through [dispatch], because a step marker
     * changes no money and nothing about it can alter what the OS has been asked to deliver.
     */
    private fun rememberOnboardingStep(step: Int): Job = scope.launch {
        writes.withLock {
            val before = _state.value.data ?: return@withLock
            if (before.settings.onboardingComplete || before.settings.onboardingStep == step) return@withLock
            val after = before.copy(settings = before.settings.copy(onboardingStep = step))
            db.dao().save(after)
            _state.update { it.copy(data = after) }
        }
    }

    // ---- §11.6 the tour --------------------------------------------------------------------------

    /** Step 3, or a replay from Settings: the overlay over the live app, starting at stop 1's screen. */
    fun startTutorial(fromOnboarding: Boolean) {
        _state.update { it.copy(tutorialStop = 0, tutorialFromOnboarding = fromOnboarding, sheet = null) }
        goToStop(0)
    }

    private fun goToStop(i: Int) {
        val stop = TUTORIAL_STOPS[i]
        _state.update { st ->
            val routes = buildList<Route> {
                add(Route.TabRoute(stop.tab, stop.sub))
                stop.settingsPage?.let { add(Route.Settings(it)) }
            }
            st.copy(tutorialStop = i, routes = routes, sheet = null)
        }
    }

    /** Next navigates the app to the next stop's screen; on the last stop it is Done. */
    fun tutorialNext() {
        val i = _state.value.tutorialStop ?: return
        if (i >= TUTORIAL_STOPS.lastIndex) tutorialFinish() else goToStop(i + 1)
    }

    /** Skipping at any point and finishing every stop land on the same next step (§11.1's step 4); a replay returns to Settings. */
    fun tutorialFinish(): Job {
        if (_state.value.tutorialStop == null) return DONE
        val job = if (_state.value.tutorialFromOnboarding) rememberOnboardingStep(4) else DONE
        _state.update { st ->
            val routes = if (st.tutorialFromOnboarding) listOf(Route.TabRoute(Tab.BUDGET), Route.Onboarding(4)) else listOf(Route.TabRoute(Tab.SETTINGS))
            st.copy(tutorialStop = null, tutorialFromOnboarding = false, routes = routes, sheet = null)
        }
        return job
    }

    /** Step 5: Budget home. `onboardingComplete` is one-way (§12); §11.3's prompt waits for Savings on its own. */
    fun completeOnboarding(): Job {
        val job = dispatch { it.copy(settings = it.settings.copy(onboardingComplete = true, onboardingStep = null)) }
        _state.update { it.copy(routes = listOf(Route.TabRoute(Tab.BUDGET)), sheet = null) }
        return job
    }

    // ---- navigation ------------------------------------------------------------------------

    fun selectTab(tab: Tab) = _state.update {
        val from = if (tab == Tab.SETTINGS) (if (it.tab == Tab.SETTINGS) it.settingsFrom else it.routes) else null
        it.copy(routes = listOf(Route.TabRoute(tab)), settingsFrom = from)
    }

    fun selectSub(sub: String) = _state.update { s ->
        val top = s.routes.first() as Route.TabRoute
        s.copy(routes = listOf(top.copy(sub = sub)) + s.routes.drop(1))
    }

    fun push(route: Route) = _state.update { it.copy(routes = it.routes + route) }

    /** §4.8's second column: what a row opens replaces what the column showed, rather than stacking on it. */
    fun openDetail(route: Route) = _state.update { it.copy(routes = listOf(it.routes.first(), route)) }

    /** §3.6: the last target and direction logged, which the floating + and Ctrl+N open on; this session only. */
    var lastEntry: Pair<String, String>? = null

    /** §3.6's forms and §4.8 rule 3's dialogs: one open at a time, over whatever screen is showing. */
    fun openSheet(sheet: Sheet) = _state.update { it.copy(sheet = sheet) }
    fun closeSheet() = _state.update { it.copy(sheet = null) }

    /** Whether Back has something to do here: an open sheet that may be dismissed (Reconcile must be answered, §3.11), or a push. */
    fun closesOnBack(s: UiState): Boolean = !s.locked && s.sheet != null && s.sheet !is Sheet.Reconcile

    /**
     * Where Back is taken and does nothing: a sheet that must be answered (§3.11) and the
     * first Profile's flow (§11.1: screen 1 has no Back, step 4 has Continue in place of one) -- popping any of
     * them would leave the screen beneath without what it stands on.
     */
    private fun holdsBack(s: UiState): Boolean =
        s.sheet is Sheet.Reconcile || (s.route as? Route.Onboarding)?.let { !it.forNewProfile || it.step > 1 } ?: false

    // §4.7: Back on the lock screen is the system's -- it leaves the app, it never reaches what is beneath
    fun handlesBack(s: UiState): Boolean = !s.locked && (s.tutorialStop != null || holdsBack(s) || closesOnBack(s) || s.routes.size > 1 || (s.tab == Tab.SETTINGS && s.settingsFrom != null))

    /** Back closes an open sheet first, then pops a push; on a tab root there is nothing to pop and the host decides (Android: finish). */
    fun back(): Boolean {
        val s = _state.value
        if (s.locked) return false
        s.tutorialStop?.let { i -> if (i > 0) goToStop(i - 1) else tutorialFinish(); return true }   // §11.6: the previous stop; before the first, the tour ends
        if (holdsBack(s)) return true
        if (closesOnBack(s)) { closeSheet(); return true }
        if (s.routes.size <= 1 && s.tab == Tab.SETTINGS && s.settingsFrom != null) {
            _state.update { it.copy(routes = it.settingsFrom!!, settingsFrom = null) }; return true
        }
        if (s.routes.size <= 1) return false
        _state.update { it.copy(routes = it.routes.dropLast(1)) }
        return true
    }

    // ---- writes ----------------------------------------------------------------------------

    /** Apply a pure change to the active Profile, persist it whole, re-sync notifications. The Job completes after the save. */
    fun dispatch(write: (ProfileData) -> ProfileData): Job =
        scope.launch {
            writes.withLock {
                val before = _state.value.data ?: return@withLock
                val after = write(before)
                if (after == before) return@withLock
                db.dao().save(after)
                session.syncNotifications(after)
                _state.update { it.copy(data = after, profiles = db.dao().profiles()) }
            }
        }

    // ---- §6 Goals -----------------------------------------------------------------------------

    fun saveGoal(draft: GoalDraft): Job = dispatch { GoalEditing.save(it, draft, clock, newId) }

    /** Closes the sheet and leaves the editor, which no longer has a Goal to edit. */
    fun deleteGoal(id: String, returnSaved: Boolean): Job {
        val job = dispatch { GoalEditing.delete(it, id, returnSaved, clock, newId) }
        _state.update { st -> st.copy(sheet = null, routes = st.routes.filterNot { it is Route.GoalEditor && it.id == id }) }
        return job
    }

    // ---- §9.1 the two hand-offs -------------------------------------------------------------------

    /** "Save for it": a Goal for the principal over the loan's own term; the calculator closes onto Budget, where the Goal now is. */
    fun loanSaveForIt(draft: LoanDraft, name: String): Job {
        val job = dispatch { d ->
            val m = Accounts.loanMath(draft.principal, draft.rate, draft.termMonths, draft.rateType)
            d.copy(goals = d.goals + Accounts.saveForItGoal(draft.principal, m, name, clock.today(), newId(), d.profileId))
        }
        _state.update { it.copy(sheet = null, routes = listOf(Route.TabRoute(Tab.BUDGET))) }
        return job
    }

    /** "Get loan": the Account and its payment, atomic; the calculator closes onto Activity, where both now are. */
    fun loanGetLoan(draft: LoanDraft, accountName: String, expenseName: String, autoUpdate: Boolean): Job {
        val job = dispatch { Accounts.getLoan(it, draft.principal, draft.rate, draft.termMonths, draft.rateType, accountName, expenseName, autoUpdate, clock, newId) }
        _state.update { it.copy(sheet = null, routes = listOf(Route.TabRoute(Tab.ACTIVITY))) }
        return job
    }

    // ---- §3.11 Recurring Expenses ----------------------------------------------------------------

    /** Create runs §3.11's four creation branches; an edit may leave a reconcile to ask about (the sheet opens after the save). */
    fun saveExpense(draft: RecurringExpense, isNew: Boolean): Job = scope.launch {
        writes.withLock {
            val before = _state.value.data ?: return@withLock
            var reconcile: Recurring.Reconcile? = null
            val after = if (isNew) Recurring.create(before, draft, clock, newId) else Recurring.edit(before, draft, clock.today()).let { (d, r) -> reconcile = r; d }
            if (after != before) { db.dao().save(after); session.syncNotifications(after) }
            _state.update { it.copy(data = after, sheet = reconcile?.let { r -> Sheet.Reconcile(r) } ?: it.sheet) }
        }
    }

    /** "Leave it as swept" passes null; either way an optional rate reset lands on the linked Loan (§9.1). */
    fun resolveReconcile(apply: Recurring.Reconcile?, accountId: String?, rate: Double?): Job {
        val job = dispatch { d ->
            var next = d
            if (apply != null) next = Recurring.applyReconcile(next, apply, clock, newId)
            if (accountId != null && rate != null) next = next.copy(accounts = next.accounts.map { if (it.id == accountId) it.copy(interestRate = rate) else it })
            next
        }
        closeSheet()
        return job
    }

    fun deleteExpense(id: String, reverse: Boolean): Job {
        val job = dispatch { Recurring.delete(it, id, reverse, clock, newId) }
        _state.update { st -> st.copy(sheet = null, routes = st.routes.filterNot { it is Route.RecurringEditor && it.id == id }) }
        return job
    }

    /** §3.9's card is dismissed by the person, and the engine's flag is what let it survive a background. */
    fun dismissCatchUpCard(): Job = dispatch { it.copy(settings = NewMonth.dismissed(it.settings)) }

    // ---- §4.3 Categories ------------------------------------------------------------------------

    fun saveCategory(draft: CategoryEditing.Draft): Job = dispatch { CategoryEditing.save(it, draft, clock, newId) }

    fun deleteCategory(id: String): Job {
        val job = dispatch { CategoryEditing.delete(it, id) }
        _state.update { st -> st.copy(sheet = null, routes = st.routes.filterNot { it is Route.CategoryEditor && it.id == id }) }
        return job
    }

    // ---- §7 Accounts, §9.2, §7.3 ---------------------------------------------------------------

    fun saveAccount(draft: Account): Job = dispatch { AccountEditing.save(it, draft, clock, newId) }

    /** §7.2's cascade; the editor and the detail beneath it close, having nothing left to show. */
    fun deleteAccount(id: String, reverse: Boolean = false): Job {
        val job = dispatch { Accounts.deleteCascade(it, id, clock, reverse, newId) }
        _state.update { st -> st.copy(sheet = null, routes = st.routes.filterNot { (it is Route.AccountEditor && it.id == id) || (it is Route.AccountDetail && it.id == id) }) }
        return job
    }

    /** §7.2's Loan-to-Credit retype, confirmed: the linked payment goes (reversed if chosen) and the editor closes. */
    fun retypeAccountToCredit(draft: Account, reverse: Boolean): Job {
        val job = dispatch { Accounts.retypeToCreditCascade(it, draft, reverse, clock, newId) }
        _state.update { st -> st.copy(sheet = null, routes = st.routes.filterNot { it is Route.AccountEditor && it.id == draft.id }) }
        return job
    }

    /** §9.2: the APR typed on the detail screen is kept on the account. */
    fun setAccountApr(id: String, apr: Double?): Job = dispatch { d -> d.copy(accounts = d.accounts.map { if (it.id == id) it.copy(apr = apr) else it }) }

    fun commitPayoff(accountId: String, amount: Money, name: String): Job = dispatch { Accounts.commitFixedPayment(it, accountId, amount, name, clock, newId()) }

    fun deletePair(id: String, otherId: String): Job {
        val job = dispatch { Ledger.deletePair(it, id, otherId) }
        closeSheet()
        return job
    }

    // ---- §4.3 Statement import ------------------------------------------------------------------

    /** Pick, parse by format, annotate against what is already imported or swept; nothing is written. */
    fun pickStatement(): Job = scope.launch {
        _state.update { it.copy(importDone = null) }
        val picked = attempt(::importFailed, READ_FAILED) { inDialog { files.open(listOf("csv", "ofx", "qfx", "txt")) } } ?: return@launch
        val d = _state.value.data ?: return@launch
        val isOfx = Regex("<STMTTRN>", RegexOption.IGNORE_CASE).containsMatchIn(picked.text) || Regex("\\.(ofx|qfx)$", RegexOption.IGNORE_CASE).containsMatchIn(picked.name)
        val rows = attempt(::importFailed, NOT_A_STATEMENT) { if (isOfx) Import.parseOFX(picked.text) else Import.parseCSV(picked.text) } ?: return@launch
        if (rows.isEmpty()) {
            _state.update { it.copy(importReview = null, importError = if (isOfx) "No transactions found in that OFX file." else "That file doesn’t look like a bank statement the app can read: a CSV needs a header row naming at least a date column and an amount (or separate debit and credit columns). Nothing was imported.") }
            return@launch
        }
        val review = Import.annotate(d, rows).sortedByDescending { it.row.date }
        _state.update { it.copy(importReview = ImportReview(picked.name, if (isOfx) "OFX" else "CSV", review), importError = null) }
    }

    private fun editImportRow(index: Int, f: (ReviewRow) -> ReviewRow) =
        _state.update { st -> st.copy(importReview = st.importReview?.let { r -> r.copy(rows = r.rows.mapIndexed { i, row -> if (i == index) f(row) else row }) }) }

    fun toggleImportRow(index: Int) = editImportRow(index) { it.copy(include = !it.include) }
    fun setImportTarget(index: Int, target: String?) = editImportRow(index) { it.copy(target = target) }
    fun cancelImport() = _state.update { it.copy(importReview = null, importError = null) }

    /** The write: through the same paths a quick-add uses, each row on its statement date (§3.6). */
    fun confirmImport(): Job {
        val review = _state.value.importReview ?: return DONE
        // §4.3: nothing leaves Savings untracked; the screen's button is off until every outflow has a target
        if (_state.value.data?.let { Import.unassigned(it, review.rows).isNotEmpty() } != false) return DONE
        val included = review.rows.filter { it.include }
        val done = ImportDone(included.size, included.filter { it.row.amount < 0 }.sumOf { -it.row.amount }, included.filter { it.row.amount > 0 }.sumOf { it.row.amount })
        val job = dispatch { Import.confirm(it, review.rows, clock, newId) }
        _state.update { it.copy(importReview = null, importError = null, importDone = done) }
        return job
    }

    // ---- §4.7 Settings ----------------------------------------------------------------------

    /** §2.5's appearance settings and the Currency: a plain field change, persisted whole like every other write. */
    fun updateSettings(change: (Settings) -> Settings): Job = dispatch { it.copy(settings = change(it.settings)) }

    /** §3.3: the toggle carries its clearing set and its anchor marker; the engine owns both. */
    fun setIncomeEnabled(enabled: Boolean): Job = dispatch { Income.setEnabled(it, enabled, clock.today()) }
    fun setIncomeCadence(cadence: IncomeCadence): Job = dispatch { Income.setCadence(it, cadence) }
    fun setAutoDeposit(on: Boolean): Job = dispatch { Income.setAutoDeposit(it, on, clock.today()) }

    fun updateImportState(change: (ImportState) -> ImportState): Job = dispatch { it.copy(importState = change(it.importState)) }

    // ---- §4.7 Data & backup --------------------------------------------------------------------

    /** A file dialog of the app's own is open: the host reports the app as left, and it was not (§4.7). */
    @Volatile private var dialogsOpen = 0

    private suspend fun <T> inDialog(block: suspend () -> T): T {
        dialogsOpen++
        try { return block() } finally { dialogsOpen-- }
    }

    /** Export: one file holding every Profile (§8.4's wrap is the unit); the app keeps only the date. */
    fun exportBackup(): Job = scope.launch {
        writes.withLock {
            val dao = db.dao()
            val app = dao.appState() ?: return@withLock
            val all = dao.profiles().mapNotNull { dao.load(it.id) }
            val today = clock.today()
            val text = Backup.encode(Backup.build(all, app.activeProfileId, today))
            if (attempt(::backupFailed, "Could not save the backup there. Try another place.") { inDialog { files.save("sci-fin-backup-$today.json", text) } } == true) {
                dao.upsertAppState(app.copy(lastBackupExportDate = today))
                _state.update { it.copy(appState = dao.appState()) }
            }
        }
    }

    /** Restore, step one: pick and validate whole; nothing changes until [confirmBackup]. */
    fun pickBackup(): Job = scope.launch {
        val picked = attempt(::backupFailed, READ_FAILED) { inDialog { files.open(listOf("json")) } } ?: return@launch
        when (val r = Backup.validate(picked.text)) {
            is BackupResult.Ok -> _state.update { it.copy(pendingBackup = r.backup, backupError = null) }
            is BackupResult.Refused -> _state.update { it.copy(pendingBackup = null, backupError = r.reason) }
        }
    }

    /** §12's screen, "Start fresh": the kept copy stays where it is; §11.1's screen 1 follows. */
    fun startFresh() {
        damage.clear()
        _state.update { it.copy(damagedCopy = null, backupError = null) }
    }

    fun cancelBackup() = _state.update { it.copy(pendingBackup = null, backupError = null) }

    /** Restore, step two: replace, not merge (§4.7), then load everything again. */
    fun confirmBackup(): Job = scope.launch {
        writes.withLock {
            val b = _state.value.pendingBackup ?: return@withLock
            // §11.1: restoring from screen 1 -- the one step that offers it -- ends the flow
            // rather than returning to it; the restored Profile carries its own onboarding
            // state and is never re-onboarded.
            val fromOnboarding = (_state.value.route as? Route.Onboarding)?.step == 1
            // one transaction: a failure leaves the device as it was, and says so
            attempt(::backupFailed, "The restore did not finish, so nothing on this device was changed.") { Backup.apply(db, b, clock.today()) } ?: return@withLock
            val routes = if (fromOnboarding) listOf(Route.TabRoute(Tab.BUDGET))
            else listOf(Route.TabRoute(Tab.SETTINGS), Route.Settings(SettingsPage.BACKUP))
            damage.clear()
            _state.update { it.copy(pendingBackup = null, backupError = null, sheet = null, previewTheme = null, routes = routes, damagedCopy = null) }
            reloadAll()
        }
    }

    // ---- §8.3 Profiles -------------------------------------------------------------------------

    /** Switch: the leaving Profile is already saved (every write is), so load the next and make it active. */
    fun switchProfile(id: String): Job = scope.launch {
        writes.withLock {
            val dao = db.dao()
            if (id == _state.value.data?.profileId) return@withLock
            val d = session.activate(id) ?: return@withLock
            // nothing transient survives a switch: an editor open on one Profile's Goal must not reopen against another's
            _state.update { it.forProfile(d).copy(appState = dao.appState(), profiles = dao.profiles()) }
        }
    }

    fun renameProfile(id: String, name: String): Job = scope.launch {
        writes.withLock {
            val dao = db.dao()
            val trimmed = name.trim(); if (trimmed.isEmpty()) return@withLock
            val d = dao.load(id) ?: return@withLock
            val renamed = d.copy(profile = d.profile.copy(name = trimmed))
            dao.save(renamed)
            _state.update { it.copy(data = if (it.data?.profileId == id) renamed else it.data, profiles = dao.profiles()) }
        }
    }

    /** Delete: never the last Profile; deleting the active one makes the first remaining one active. */
    fun deleteProfile(id: String): Job = scope.launch {
        writes.withLock {
            val dao = db.dao()
            val remaining = dao.profiles().filter { it.id != id }
            if (remaining.isEmpty()) return@withLock
            dao.deleteProfileWhole(id)
            if (_state.value.data?.profileId == id) {
                val next = session.activate(remaining.first().id)!!
                _state.update { it.forProfile(next) }
            }
            _state.update { it.copy(profiles = dao.profiles(), appState = dao.appState(), sheet = null) }
        }
    }

    // ---- §4.7 Security & lock ------------------------------------------------------------------

    /** When the app was left (switched away from, minimised); null while it is in front or no PIN is set. */
    @Volatile private var leftAt: TimeMark? = null

    private fun waitFrom(failed: Int): TimeMark? = Pin.waitAfter(failed).takeIf { it.isPositive() }?.let { time.markNow() + it }

    /** Whether a PIN may be checked now: no wait is running. */
    private fun waiting(s: UiState) = s.pinWaitEnds?.hasNotPassedNow() == true

    /** Turns the lock on. The caller checked [Pin.valid], the match of the two entries, and [hintProblem]. */
    fun setPin(pin: String, hint: String): Job = scope.launch {
        if (!Pin.valid(pin) || hintProblem(pin, hint, "PIN") != null) return@launch
        val lock = withContext(hashing) { Pin.lockFor(pin, hint, LockAfter.ONE_MINUTE) }
        writes.withLock {
            db.dao().upsertAppLock(lock)
            _state.update { it.copy(lock = lock.summary(), pinWrong = false, pinWaitEnds = null) }
        }
    }

    fun setLockAfter(after: LockAfter): Job = scope.launch {
        writes.withLock {
            val l = db.dao().appLock() ?: return@withLock
            db.dao().upsertAppLock(l.copy(lockAfter = after.token))
            _state.update { it.copy(lock = it.lock?.copy(after = after)) }
        }
    }

    fun lockNow() = _state.update { if (it.lock == null) it else it.copy(locked = true, sheet = null, pinWrong = false) }

    /**
     * Checks [pin], on the gate or to turn the lock off. A wrong one counts towards the wait wherever it was
     * typed: turning the lock off is no side door for guessing. Nothing is checked while a wait runs.
     */
    private suspend fun checkPin(pin: String): Boolean? {
        if (waiting(_state.value)) return null
        val lock = db.dao().appLock() ?: return null
        val right = withContext(hashing) { Pin.matches(lock, pin) }
        writes.withLock {
            val now = db.dao().appLock() ?: return null
            val failed = if (right) 0 else now.failedAttempts + 1
            if (failed != now.failedAttempts) db.dao().upsertAppLock(now.copy(failedAttempts = failed))
            _state.update { it.copy(pinWrong = !right, pinWaitEnds = waitFrom(failed)) }
        }
        return right
    }

    fun unlock(pin: String): Job = scope.launch {
        if (checkPin(pin) == true) _state.update { it.copy(locked = false, pinWrong = false) }
    }

    /** Turning the lock off asks for the PIN, so a phone left unlocked cannot have its lock removed. */
    fun turnOffLock(pin: String): Job = scope.launch {
        if (checkPin(pin) != true) return@launch
        writes.withLock {
            db.dao().deleteAppLock()
            leftAt = null
            _state.update { it.copy(lock = null, locked = false, pinWrong = false, pinWaitEnds = null) }
        }
    }

    /**
     * A new PIN (or only a new hint: the same PIN typed as the new one), behind the current PIN. The "lock after"
     * choice is kept; the salt is new. Returns, through the job, nothing -- [UiState.pinWrong] says a wrong current PIN.
     */
    fun changePin(current: String, new: String, hint: String): Job = scope.launch {
        if (!Pin.valid(new) || hintProblem(new, hint, "PIN") != null) return@launch
        if (checkPin(current) != true) return@launch
        val after = _state.value.lock?.after ?: LockAfter.ONE_MINUTE
        val lock = withContext(hashing) { Pin.lockFor(new, hint, after) }
        writes.withLock {
            db.dao().upsertAppLock(lock)
            _state.update { it.copy(lock = lock.summary(), pinWrong = false, pinWaitEnds = null) }
        }
    }

    /** The field changed: a "wrong" line about the last attempt no longer describes what is typed. */
    fun clearPinWrong() = _state.update { if (it.pinWrong) it.copy(pinWrong = false) else it }

    /** The host: the app went to the background (Android) or lost focus (Windows). The app's own file dialogs don't count. */
    fun appLeft() {
        val s = _state.value
        if (s.lock != null && !s.locked && dialogsOpen == 0) leftAt = time.markNow()
    }

    /** The host: the app is in front again. Locks when it was away for at least the chosen time. */
    fun appReturned() {
        val left = leftAt ?: return
        leftAt = null
        val s = _state.value
        if (s.lock != null && left.elapsedNow() >= s.lock.after.wait) lockNow()
    }

    /**
     * §4.7's forgotten PIN: everything on this device goes -- every Profile, the app row, the lock, the kept
     * snapshots -- and the app opens on §11.1's screen 1, which offers a restore. The person typed ERASE first.
     */
    fun eraseEverything(): Job = scope.launch {
        writes.withLock {
            db.dao().eraseAll()
            // the rows are gone; VACUUM rewrites the file so their pages are too, and the log is folded in and emptied
            runCatching { db.useWriterConnection { c -> c.usePrepared("VACUUM") { it.step() }; c.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() } } }
            copies.eraseAll()
            damage.clear()
            session.clearSchedule()
            leftAt = null
            _state.value = UiState(ready = true, routes = listOf(Route.TabRoute(Tab.BUDGET), Route.Onboarding(1)), notificationPermission = readPermission(null))
        }
    }
}
