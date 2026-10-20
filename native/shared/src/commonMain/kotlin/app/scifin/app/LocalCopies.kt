package app.scifin.app

/**
 * The copies of the store the app keeps beside it: §12's pre-migration snapshots and the damaged copies
 * [DamageNotice] names. §4.7's forgotten-PIN erase removes them too -- "everything on this device" would
 * otherwise leave a readable copy of everything behind.
 */
interface LocalCopies {
    /** Deletes every kept copy and the damage marker. Never throws; returns how many files were removed. */
    fun eraseAll(): Int

    /** For tests, the headless renders and any host without a file store. */
    object None : LocalCopies {
        override fun eraseAll() = 0
    }
}
