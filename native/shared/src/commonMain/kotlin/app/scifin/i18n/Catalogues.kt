package app.scifin.i18n

/**
 * Every language other than English, as English text -> its translation. [tr] reads these whatever [SHIPPED] says,
 * so a catalogue can be rendered and tested before people are offered it.
 */
val CATALOGUES: Map<Language, Map<String, String>> = mapOf(
    Language.IT to ITALIAN_UI + ITALIAN_LEARN,
)

/**
 * The languages beyond English that people are offered -- in Appearance and as the device fallback. A language joins
 * only once its whole catalogue, Learn topics included, is complete and reviewed by the owner (§2.7).
 */
val SHIPPED: Set<Language> = setOf(Language.IT)   // owner review of the Italian Learn topics, 2026-10-27
