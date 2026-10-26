package app.scifin.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale

/*
 * Localisation (owner, 2026-10-26). The English text is the key: every piece of text the person reads is written
 * in English where it is used and passed through [tr], [trf] or [trp], and each other language is a catalogue
 * mapping those English strings to its own ([CATALOGUES]). A string missing from a catalogue falls back to
 * English, and `LocalisationTest` fails the build while any is missing, so nothing ships half-translated.
 *
 * Keys must be plain string literals -- no templates, no concatenation -- so the test can read them from the
 * source. Values go in through numbered placeholders, `{0}`, `{1}`, which a translation may reorder.
 *
 * The language is per Profile (owner: in Appearance), falling back to the device's; before a Profile exists,
 * and on the PIN gate, it is the device's. [current] is snapshot state, so a change recomposes every screen,
 * and code outside composition -- the engine's wording, the notification bodies -- reads the same value.
 */

enum class Language(val tag: String, val label: String) {
    EN("en", "English"),
    IT("it", "Italiano");

    companion object {
        fun of(tag: String?): Language? = entries.firstOrNull { it.tag == tag }
    }
}

/** The languages a person can be shown: English, plus every language whose catalogue ships. */
val SUPPORTED: List<Language> get() = listOf(Language.EN) + CATALOGUES.keys.sortedBy { it.ordinal }

object I18n {
    private var state by mutableStateOf(Language.EN)

    /** The language every [tr] reads. Set from [resolve], which only ever yields a shipped language. */
    var current: Language
        get() = state
        set(value) { state = value }

    /** The device's language where the app has it, else English. */
    fun device(): Language = Language.of(runCatching { Locale.current.language }.getOrNull())?.takeIf { it in SUPPORTED } ?: Language.EN

    /** A Profile's stored choice (null = follow the device), resolved. */
    fun resolve(stored: String?): Language = Language.of(stored)?.takeIf { it in SUPPORTED } ?: device()
}

/** [en] in the current language; stored text with a name inside it is matched against [STORED_TEMPLATES]. */
fun tr(en: String): String {
    if (I18n.current == Language.EN) return en
    val cat = CATALOGUES[I18n.current] ?: return en
    return cat[en] ?: storedTemplate(en, cat) ?: en
}

/** [en] in the current language, with `{0}`, `{1}`… replaced by [args] in order. */
fun trf(en: String, vararg args: Any?): String {
    var s = tr(en)
    for ((i, a) in args.withIndex()) s = s.replace("{$i}", a.toString())
    return s
}

/**
 * A typed confirmation word (DELETE, UPDATE, ERASE): the word in the reader's language, or the English one, which
 * keeps working whatever the language -- someone who learned it in English is never locked out by a translation.
 */
fun typedWordMatches(typed: String, en: String): Boolean = typed.trim().uppercase().let { it == en.uppercase() || it == tr(en).uppercase() }

/** One of two forms by [n] -- [one] when it is exactly 1, else [other] -- each with `{0}` as [n] and then [args]. */
fun trp(one: String, other: String, n: Number, vararg args: Any?): String =
    trf(if (n.toDouble() == 1.0) one else other, n, *args)
