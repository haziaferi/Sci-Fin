"""One-off (2026-10-09): desktop variants R (actions on the objects, desktopAdd 5) and I (R + an inline entry row at
the top of the Activity log, desktopAdd 6), for the owner's desktop rescoring. Run from the project root."""
K = "native/shared/src/commonMain/kotlin/app/scifin/"

def rd(p): return open(K + p, encoding="utf-8", newline="").read()
def wr(p, s): open(K + p, "w", encoding="utf-8", newline="").write(s)
def rep(p, a, b):
    s = rd(p); assert s.count(a) == 1, (p, a[:80], s.count(a)); wr(p, s.replace(a, b))

rep("ui/PlacementMock.kt", "    var desktopAdd = 1\n",
    "    var desktopAdd = 1   // 5 R: a labelled Add on the Savings row and a + on each account, nothing floating; 6 I: R + an inline entry row atop the Activity log\n")

# Ledger.kt: the labelled Add button, and a slot for it in the Savings row's name area
rep("ui/components/Ledger.kt", "/** The Savings row, the same on Budget and Activity (§3.6, §3.7): a tap opens what [onClick] opens. */",
'''/** TEMPORARY (PlacementMock.desktopAdd 5/6): a labelled Add in the Confirm button's colours, for wide windows. */
@Composable
fun AddButton(onClick: () -> Unit) {
    val t = LocalTokens.current; val p = t.palette
    Row(
        Modifier.probeAs("fab").heightIn(min = t.spacing.target).clip(RoundedCornerShape(t.radii.md)).background(p.acc).clickable(onClick = onClick)
            .semantics { role = Role.Button }.padding(horizontal = t.spacing.u * 1.5f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.u / 2),
    ) { LucideIcon(Lucide.PLUS, tint = p.onAcc, size = t.icons.sm); Text("Add", color = p.onAcc, fontSize = t.type.body, fontWeight = FontWeight.Bold) }
}

/** The Savings row, the same on Budget and Activity (§3.6, §3.7): a tap opens what [onClick] opens. */''')
rep("ui/components/Ledger.kt", "fun SavingsLedgerRow(amount: String, onClick: () -> Unit, clickLabel: String, slot: (@Composable () -> Unit)? = null) {",
    "fun SavingsLedgerRow(amount: String, onClick: () -> Unit, clickLabel: String, slot: (@Composable () -> Unit)? = null, action: (@Composable () -> Unit)? = null) {")
rep("ui/components/Ledger.kt", "                LedgerLabel(\"Savings\"); LucideIcon(Lucide.INFO, tint = p.txt3, size = t.spacing.u * 1.6f)\n",
    "                LedgerLabel(\"Savings\"); LucideIcon(Lucide.INFO, tint = p.txt3, size = t.spacing.u * 1.6f)\n                if (action != null) Box(Modifier.padding(start = t.spacing.gap)) { action() }\n")

# SavingsRow: R/I put Add on the row at two columns
rep("ui/components/SavingsRow.kt", '''            onClick = { store.openSheet(Sheet.SavingsEditor) }, clickLabel = "Edit Savings directly",
        )''', '''            onClick = { store.openSheet(Sheet.SavingsEditor) }, clickLabel = "Edit Savings directly",
            action = if (app.scifin.ui.LocalWidthClass.current != app.scifin.ui.WidthClass.COMPACT && app.scifin.ui.PlacementMock.desktopAdd >= 5)
                ({ AddButton { store.openSheet(Sheet.QuickAdd("savings")) } }) else null,
        )''')

# Frame: nothing floats and the rail stays bare for 5/6
rep("ui/Frame.kt", "top = if (PlacementMock.desktopAdd >= 3 && showsFloatingAdd(state, wc))", "top = if (PlacementMock.desktopAdd in 3..4 && showsFloatingAdd(state, wc))")

# Activity: accounts carry their + at two columns under 5/6
rep("ui/screens/ActivityScreens.kt", "                            slot = { LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.sm) },\n",
'''                            slot = if (app.scifin.ui.LocalWidthClass.current != app.scifin.ui.WidthClass.COMPACT && app.scifin.ui.PlacementMock.desktopAdd >= 5)
                                ({ app.scifin.ui.components.RowAdd("Quick-add ${a.name}") { store.openSheet(Sheet.QuickAdd("acct:${a.id}")) } })
                            else ({ LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.sm) }),
''')

# Ledger screen: I's inline entry row under the title
rep("ui/screens/LedgerScreen.kt", '''        TopRow("Activity log", onBack = { store.back() })
''', '''        TopRow("Activity log", onBack = { store.back() })
        if (app.scifin.ui.PlacementMock.desktopAdd == 6 && app.scifin.ui.LocalWidthClass.current != app.scifin.ui.WidthClass.COMPACT) InlineEntry(d, store)
''')
s = rd("ui/screens/LedgerScreen.kt")
s = s.rstrip("\n") + '''

/** TEMPORARY (PlacementMock.desktopAdd 6): a register-style entry row -- direction, amount, payee, Add -- writing to Savings as quick-add does. */
@Composable
private fun InlineEntry(d: app.scifin.model.ProfileData, store: app.scifin.app.AppStore) {
    val t = LocalTokens.current
    val dirs = if (d.settings.incomeEnabled) listOf("out" to "Money out", "in" to "Money in", "income" to "Income") else listOf("out" to "Money out", "in" to "Money in")
    var dir by remember { mutableStateOf("out") }
    var amount by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(bottom = t.spacing.gap), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(t.spacing.gap)) {
        Column(Modifier.weight(1.2f)) { FieldLabel("Direction"); Dropdown(dirs, dir, { dir = it }) }
        Column(Modifier.weight(1f)) { FieldLabel("Amount"); TextField(amount, { amount = it }, prefix = app.scifin.ui.currencySymbol(d.settings.currency), keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal) }
        Column(Modifier.weight(1.4f)) { FieldLabel("Payee"); TextField(payee, { payee = it }) }
        app.scifin.ui.components.AddButton {
            val amt = app.scifin.ui.parseMoney(amount) ?: return@AddButton
            if (amt <= 0) return@AddButton
            store.dispatch { p -> app.scifin.ui.sheets.quickAddWrite(p, app.scifin.app.Sheet.QuickAdd("savings"), amt, dir, payee.trim().ifEmpty { null }, store.clock.today(), store.clock, store.newId) }
            amount = ""; payee = ""
        }
    }
}
'''
wr("ui/screens/LedgerScreen.kt", s)
print("applied")
