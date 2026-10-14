package app.scifin.app

/**
 * §12: a store SQLite could not read was moved aside before the app
 * opened, and the person has not yet answered the screen that says so. The host's marker is the
 * source, so a restart before answering shows the screen again.
 */
interface DamageNotice {
    /** The kept copy's file name while the screen is owed; null otherwise. */
    val keptAs: String?

    /** The person answered: restored a backup or chose to start fresh. The kept copy stays. */
    fun clear()

    /** For tests, the headless renders and any host without a file store. */
    object None : DamageNotice {
        override val keptAs: String? = null
        override fun clear() {}
    }
}
