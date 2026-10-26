package app.scifin.model

/**
 * One Profile's whole world, in memory -- the shape the engine works on and the shape the
 * prototype's `state` had. Loaded and saved through the Repository; every engine function is
 * `(ProfileData, inputs) -> ProfileData` (or a derived value), so the engine is testable with
 * no database at all, and §8.4's swap-in/swap-out on a Profile switch is one object.
 */
data class ProfileData(
    val profile: Profile,
    val savings: Savings,
    val settings: Settings,
    val importState: ImportState,
    val goals: List<Goal> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    /** §3.10's hours logged since the last payday that paid them. */
    val shifts: List<Shift> = emptyList(),
) {
    val profileId: String get() = profile.id

    companion object {
        /** §8: a new Profile is a separate environment with the app's defaults (§3.1's null Savings, §3.3's Income off).
         *  `onboardingComplete` is the caller's to say (§11.1: the first Profile has steps ahead, a later one does not). */
        fun fresh(id: String, name: String, onboardingComplete: Boolean = true): ProfileData = ProfileData(
            profile = Profile(id, name),
            savings = Savings(id, null),
            settings = Settings(profileId = id, onboardingComplete = onboardingComplete),
            importState = ImportState(profileId = id),
        )
    }
}
