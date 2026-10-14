package app.scifin.ui.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import app.scifin.app.AppStore
import app.scifin.app.Sheet
import app.scifin.app.UiState
import app.scifin.ui.screens.AccountsNoteSheet
import app.scifin.ui.screens.DeleteAccountSheet
import app.scifin.ui.screens.RetypeCascadeSheet
import app.scifin.ui.screens.DeleteCategorySheet
import app.scifin.ui.screens.DeleteExpenseSheet
import app.scifin.ui.screens.LoanBorrowSheet
import app.scifin.ui.screens.LoanSaveSheet
import app.scifin.ui.screens.ReconcileSheet
import app.scifin.ui.screens.DeleteGoalSheet
import app.scifin.ui.screens.DeletePairSheet
import app.scifin.ui.screens.PayoffCommitSheet

/** The one open form, over whatever screen is showing (§4.8 rule 3 decides its shape). */
@Composable
fun Sheets(sheet: Sheet, state: UiState, store: AppStore) {
    val d = state.data ?: return
    // keyed by the sheet, so a sheet that replaces another of its kind starts from its own state
    key(sheet) {
        when (sheet) {
            is Sheet.Entry -> EntrySheet(sheet, d, store)
            Sheet.AccountsNote -> AccountsNoteSheet(store)
            Sheet.SavingsEditor -> SavingsEditorSheet(d, store)
            is Sheet.RenameProfile -> RenameProfileSheet(sheet, state, store)
            is Sheet.DeleteProfile -> DeleteProfileSheet(sheet, state, store)
            is Sheet.DeleteGoal -> DeleteGoalSheet(sheet, state, store)
            is Sheet.DeleteAccount -> DeleteAccountSheet(sheet, state, store)
            is Sheet.RetypeCascade -> RetypeCascadeSheet(sheet, state, store)
            is Sheet.DeletePair -> DeletePairSheet(sheet, state, store)
            is Sheet.PayoffCommit -> PayoffCommitSheet(sheet, state, store)
            is Sheet.DeleteCategory -> DeleteCategorySheet(sheet, state, store)
            is Sheet.DeleteExpense -> DeleteExpenseSheet(sheet, state, store)
            is Sheet.Reconcile -> ReconcileSheet(sheet, state, store)
            is Sheet.LoanSave -> LoanSaveSheet(sheet, state, store)
            is Sheet.LoanBorrow -> LoanBorrowSheet(sheet, state, store)
        }
    }
}
