"""One-off (2026-10-09): apply the owner's decisions -- Income inside the floating + only, the + in Confirm's
colours, the accounts note as a sheet -- and drop the rejected placement variants. Run from the project root."""
import os, re
K = "native/shared/src/commonMain/kotlin/app/scifin/"

def rd(p): return open(K + p, encoding="utf-8", newline="").read()
def wr(p, s): open(K + p, "w", encoding="utf-8", newline="").write(s)
def rep(p, a, b):
    s = rd(p); assert s.count(a) == 1, (p, a[:80], s.count(a)); wr(p, s.replace(a, b))

# 1. Derived.kt
rep("ui/Derived.kt", """/** The floating + is this share of the way from the ground to the accent (Oklab); a row's + (Add to a goal) is the lighter share. */
const val FLOATING_ADD_SHARE = 0.72f
const val ROW_ADD_SHARE = 0.40f""", """/** A row's + (Add to a goal) is this share of the way from the ground to the accent (Oklab). The floating + is the Confirm button's accent (owner, 2026-10-09). */
const val ROW_ADD_SHARE = 0.40f""")
rep("ui/Derived.kt", """ * txt / onAcc / bg / surf / acc reads best on it. tools/plus_colours.py checks all eight palettes: 4.42:1 at worst
 * (the floating + in Daylight and Dusk light, above WCAG's 3:1 for icons; the owner chose that over a near-black
 * glyph that outshone Savings, 2026-10-09), 4.71:1 or more for every other glyph.""", """ * txt / onAcc / bg / surf / acc reads best on it. tools/plus_colours.py checks all eight palettes: 4.71:1 at worst.""")

# 2. Ledger.kt
s = rd("ui/components/Ledger.kt")
s = s.replace("import app.scifin.ui.FLOATING_ADD_SHARE\n", "")
s = s.replace("/** The floating +: logs an expense (M3, the top action). Drawn and touched at the target size, 48dp on a phone. */",
              "/** The floating +: logs an expense or income (M3, the top action), in the Confirm button's colours. Drawn and touched at the target size. */")
s = s.replace("    val (fill, glyph) = t.palette.plusColours(FLOATING_ADD_SHARE)\n", "    val fill = t.palette.acc; val glyph = t.palette.onAcc\n")
i = s.index("/** Information with the accent kept as a rule at its start"); j = s.index("/** The Savings row, the same on Budget and Activity")
s = s[:i] + '''/** Information with the accent kept as a rule at its start, unfilled (it cannot be tapped). */
@Composable
fun RuledNote(text: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current; val p = t.palette
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(t.strokes.emph).fillMaxHeight().background(p.acc))
        Text(text, color = p.txt, fontSize = t.type.body, lineHeight = t.type.body * 1.6, modifier = Modifier.padding(start = t.spacing.u * 1.25f).measureCap(t))
    }
}

/** A one-line note whose whole line is the target, the info glyph before it and a chevron after: it opens [onClick]'s sheet. */
@Composable
fun NoteLine(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Row(
        modifier.fillMaxWidth().heightIn(min = t.spacing.target).clickable(role = Role.Button, onClick = onClick).padding(horizontal = t.spacing.u * 0.25f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u),
    ) {
        LucideIcon(Lucide.INFO, tint = p.txt2, size = t.icons.sm)
        Text(text, color = p.txt2, fontSize = t.type.caption, modifier = Modifier.weight(1f))
        LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.sm)
    }
}

''' + s[j:]
wr("ui/components/Ledger.kt", s)

# 3. QuickAddSheet
s = rd("ui/sheets/QuickAddSheet.kt")
s = s.replace("""    // TEMPORARY (2026-10-09, PlacementMock.logIncome C): Log income as a third direction of the floating +
    target == "savings" && app.scifin.ui.PlacementMock.logIncome in "CNP" && d.settings.incomeEnabled -> listOf("out" to "Money out", "in" to "Money in", "income" to "Income")""",
"""    // §3.3 (Decided 2026-10-09, with the owner): logging income is a third direction of the floating +, while Income is on
    target == "savings" && d.settings.incomeEnabled -> listOf("out" to "Money out", "in" to "Money in", "income" to "Income")""")
s = re.sub(r"    val prefill = if \(sheet\.incomeEntry \|\| \(app\.scifin\.ui\.PlacementMock[^\n]*\n", '    val prefill = if (sheet.incomeEntry) paycheck else ""\n', s)
s = re.sub(r"    // TEMPORARY \(PlacementMock\.logIncome P\)[^\n]*\n    val overdue = [^\n]*\n    var direction by remember \{ mutableStateOf\(if \(overdue\) \"income\" else opts\?\.first\(\)\?\.first \?: \"in\"\) \}",
           '    var direction by remember { mutableStateOf(opts?.first()?.first ?: "in") }', s)
assert "PlacementMock" not in s, "quick-add"
wr("ui/sheets/QuickAddSheet.kt", s)

# 4. SavingsRow
s = rd("ui/components/SavingsRow.kt")
a = """        val choices = PlacementMock.logIncome == 'A'
        SavingsLedgerRow(
            if (unset) "—" else formatMoney(d.savings.amount!!, d.settings.currency),
            onClick = { store.openSheet(if (choices) Sheet.SavingsActions else Sheet.SavingsEditor) },
            clickLabel = if (choices) "Savings actions" else "Edit Savings directly",
        )"""
assert a in s
s = s.replace(a, """        SavingsLedgerRow(
            if (unset) "—" else formatMoney(d.savings.amount!!, d.settings.currency),
            onClick = { store.openSheet(Sheet.SavingsEditor) }, clickLabel = "Edit Savings directly",
        )""")
s = s.replace("import app.scifin.ui.PlacementMock\n", "")
s = s.replace(" * an expense is the floating +, and a tap on the row reaches the rarer actions.", " * an expense or income is the floating +, and a tap on the row opens the full editor (§3.6).")
wr("ui/components/SavingsRow.kt", s)

# 5. The choice sheet out, the accounts-note sheet in
rep("app/Route.kt", """    /** TEMPORARY (2026-10-09, PlacementMock.logIncome A): tapping Savings offers Log income and the full editor. */
    data object SavingsActions : Sheet
""", """    /** §4.4 (Decided 2026-10-09, with the owner): the accounts note, opened from its one line on Activity. */
    data object AccountsNote : Sheet
""")
rep("ui/sheets/Sheets.kt", "        Sheet.SavingsActions -> SavingsActionsSheet(d, store)\n", "        Sheet.AccountsNote -> app.scifin.ui.screens.AccountsNoteSheet(store)\n")
s = rd("ui/sheets/SavingsEditorSheet.kt"); s = s[:s.index("\n/**\n * TEMPORARY (2026-10-09, PlacementMock.logIncome A)")] + "\n"; wr("ui/sheets/SavingsEditorSheet.kt", s)

# 6. BudgetScreen
s = rd("ui/screens/BudgetScreen.kt")
s = re.sub(r"\n            // TEMPORARY \(2026-10-09, PlacementMock\.logIncome B\)[^\n]*\n            slot = if \(PlacementMock\.logIncome == 'B'\)[^\n]*\n", "\n", s)
s = re.sub(r"Income\.noIncomeLoggedDays\(d, today\)\?\.let \{ days ->\n            RuledNote\(\"No income logged in the last \$days \$\{if \(days == 1\) \"day\" else \"days\"\}\.\",\n[^\n]*\n[^\n]*\n        \}",
           'Income.noIncomeLoggedDays(d, today)?.let { days -> RuledNote("No income logged in the last $days ${if (days == 1) "day" else "days"}.") }', s)
s = s.replace("import app.scifin.ui.PlacementMock\n", "").replace("import app.scifin.ui.plusColours\n", "")
assert "PlacementMock" not in s, "budget"
wr("ui/screens/BudgetScreen.kt", s)

# 7. Activity: the note is one line opening a sheet
s = rd("ui/screens/ActivityScreens.kt")
i = s.index("/** The accounts note, its first clause on screen"); j = s.index("// ---- §4.2 Account detail")
s = s[:i] + '''/** §4.4 (Decided 2026-10-09, with the owner): the note's first clause on screen, the rest in a sheet, not expanded in place. */
@Composable
private fun AccountsNote(store: AppStore) {
    app.scifin.ui.components.NoteLine("Accounts are informational.", Modifier.padding(top = LocalTokens.current.spacing.gap).probeAs("note")) { store.openSheet(Sheet.AccountsNote) }
}

/** The accounts note's sheet. */
@Composable
fun AccountsNoteSheet(store: AppStore) {
    app.scifin.ui.components.SheetScaffold(title = "Accounts", onDismiss = store::closeSheet) {
        Text(ACCOUNTS_NOTE, color = LocalTokens.current.palette.txt, fontSize = LocalTokens.current.type.body, lineHeight = LocalTokens.current.type.body * 1.5)
    }
}

''' + s[j:]
wr("ui/screens/ActivityScreens.kt", s)

# 8. PlacementMock keeps only the desktop question
wr("ui/PlacementMock.kt", '''package app.scifin.ui

/**
 * TEMPORARY (2026-10-09): the desktop placement, rescored with desktop parameters at the owner's request.
 * Rendered by PlacementRenderTest; removed once decided.
 */
object PlacementMock {
    /** The floating + at two columns: 1 bottom-end of the window, 2 bottom-end of the first column, 3 at the top of the rail, 4 the rail's + with a label. */
    var desktopAdd = 1
}
''')
print("applied")
