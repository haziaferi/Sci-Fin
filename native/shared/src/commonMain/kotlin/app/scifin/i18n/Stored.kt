package app.scifin.i18n

/*
 * Text the app itself writes into the data (owner, 2026-10-26, §2.7): a ledger row's payee such as "Auto-deposit",
 * an end-of-term notice, a default name. It is stored in English and shown in the active language, so switching
 * language changes old rows too. [stored] is the identity; it marks the English text as a key, which
 * `LocalisationTest` collects like any `tr` key, so every catalogue must translate it.
 */

/** [en] unchanged -- the data stays English -- marked as a key every catalogue translates. */
fun stored(en: String): String = en

/**
 * Stored labels with a name inside them, which [tr] matches when no exact key does: "Toward Holidays" is shown as
 * the catalogue's "Toward {0}" with the name kept as typed.
 */
internal val STORED_TEMPLATES: List<String> = listOf(
    stored("Toward {0}"),
    stored("Spent from {0}"),
    stored("Payment to {0}"),
    stored("{0} payment"),
)

/** [text] as one of [STORED_TEMPLATES] in the current language, or null when none matches. */
internal fun storedTemplate(text: String, catalogue: Map<String, String>): String? {
    for (t in STORED_TEMPLATES) {
        val (before, after) = t.split("{0}").let { it[0] to it[1] }
        if (text.length > before.length + after.length && text.startsWith(before) && text.endsWith(after)) {
            val name = text.substring(before.length, text.length - after.length)
            return catalogue[t]?.replace("{0}", name)
        }
    }
    return null
}
