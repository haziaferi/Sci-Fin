package app.scifin

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import app.scifin.ui.tokensFor
import androidx.activity.result.contract.ActivityResultContracts
import app.scifin.app.AppStore
import app.scifin.app.Files
import app.scifin.app.PickedFile
import app.scifin.data.sciFinDamageNotice
import app.scifin.ui.SciFinApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** §2.5: the touch target minimum on Android. */
private const val TARGET_DP = 48

/**
 * Holds the store for as long as the person is in the app, across rotation, a dark-mode flip, a
 * font-scale or locale change -- the route stack, an open sheet, an import under review and an
 * editor's draft all survive, and §3.9's catch-up runs once per process, not once per
 * recreation. Its [files] forwards to whichever Activity is current.
 */
class StoreViewModel(app: Application) : AndroidViewModel(app) {
    private val sciFin = app as SciFinApplication
    val files = ForwardingFiles()

    /** The open dialogs' answers, held here: after a rotation the result reaches the next Activity's launcher. */
    var pendingPermission: CompletableDeferred<Boolean>? = null
    var pendingOpen: CompletableDeferred<Uri?>? = null
    var pendingSave: CompletableDeferred<Uri?>? = null

    /** Built off the main thread: opening the store runs §12's quick_check and snapshot, both whole-file passes. */
    val store: Deferred<AppStore> = viewModelScope.async {
        val db = withContext(Dispatchers.IO) { sciFin.db }
        AppStore(sciFin.session, db, viewModelScope, files, sciFinDamageNotice(app)).also { s -> viewModelScope.launch { s.start() } }
    }
}

/** A [Files] with no dialog of its own: the current Activity lends it one, and takes it back when it goes. */
class ForwardingFiles : Files {
    @Volatile var host: Files? = null
    override suspend fun open(extensions: List<String>): PickedFile? = host?.open(extensions)
    override suspend fun save(suggestedName: String, text: String): Boolean = host?.save(suggestedName, text) ?: false
}

/**
 * The Android host. Owns the ActivityResult launchers the scheduler's permission request and the
 * Storage Access Framework need (§11.5, §4.7, §4.3), and shows the app; the store, the database
 * and the session outlive it (above, and [SciFinApplication]).
 * §2.5: the touch target minimum is 48dp here. The frame draws edge to edge -- the palette's
 * `bg` runs under both system bars and the bar glyphs follow the app's own light/dark
 * resolution, not the OS theme.
 */
class MainActivity : ComponentActivity() {

    private val vm: StoreViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.pendingPermission?.complete(granted); vm.pendingPermission = null
    }

    // §4.7's backup and §4.3's import go through the Storage Access Framework: the person picks the place, the app never sees a path
    private val openLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> vm.pendingOpen?.complete(uri); vm.pendingOpen = null }
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> vm.pendingSave?.complete(uri); vm.pendingSave = null }
    private val files = object : Files {
        override suspend fun open(extensions: List<String>): PickedFile? {
            val d = CompletableDeferred<Uri?>(); vm.pendingOpen = d
            openLauncher.launch(arrayOf("*/*"))
            val uri = d.await() ?: return null
            // the application's resolver: after a rotation this Activity may be gone; IO, as a cloud provider can be slow
            val resolver = application.contentResolver
            return withContext(Dispatchers.IO) {
                val name = resolver.query(uri, null, null, null, null)?.use { c -> val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && i >= 0) c.getString(i) else null } ?: "file"
                val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@withContext null
                PickedFile(name, text)
            }
        }
        override suspend fun save(suggestedName: String, text: String): Boolean {
            val d = CompletableDeferred<Uri?>(); vm.pendingSave = d
            saveLauncher.launch(suggestedName)
            val uri = d.await() ?: return false
            val resolver = application.contentResolver
            return withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
            }
        }
    }

    private val requester: suspend (String) -> Unit = { permission ->
        val d = CompletableDeferred<Boolean>(); vm.pendingPermission = d
        permissionLauncher.launch(permission); d.await()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        (application as SciFinApplication).scheduler.permissionRequester = requester
        vm.files.host = files

        lifecycleScope.launch {
            val store: AppStore = vm.store.await()
            // Back closes a sheet, then pops a push while there is one; at a tab root the callback is simply off and the
            // system decides. Enabled from state, never switched off inside the handler: Android 12+ backgrounds the
            // root activity rather than finishing it.
            val back = onBackPressedDispatcher.addCallback(this@MainActivity) { store.back() }
            launch { repeatOnLifecycle(Lifecycle.State.STARTED) { store.state.collect { back.isEnabled = store.handlesBack(it) } } }

            setContent {
                val state by store.state.collectAsState()
                val dark = tokensFor(state.settings, isSystemInDarkTheme(), TARGET_DP).dark
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
                }
                SciFinApp(store, targetDp = TARGET_DP)
            }
        }
    }

    /** §4.7: the permission is re-read whenever the app comes back -- it may have been changed in system settings meanwhile. */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { vm.store.await().refreshPermission() }
    }

    override fun onDestroy() {
        // an outstanding dialog belongs to this instance unless it is only being recreated (its result then reaches the
        // next one): answer "cancelled" rather than leave its caller waiting forever
        if (!isChangingConfigurations) {
            vm.pendingOpen?.complete(null); vm.pendingSave?.complete(null); vm.pendingPermission?.complete(false)
            vm.pendingOpen = null; vm.pendingSave = null; vm.pendingPermission = null
        }
        if (vm.files.host === files) vm.files.host = null
        (application as SciFinApplication).scheduler.let { if (it.permissionRequester === requester) it.permissionRequester = null }
        super.onDestroy()
    }
}
