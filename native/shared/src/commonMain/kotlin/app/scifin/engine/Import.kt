package app.scifin.engine

import app.scifin.model.AccountType
import app.scifin.model.Money
import app.scifin.model.ProfileData
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * §4.3 -- statement import: parse a CSV or OFX export on-device, annotate each row with what
 * the app already knows, and offer the result as a REVIEW -- never a write. Confirming goes
 * through the same write paths a quick-add uses (§3.6), each row carrying the statement's
 * own date.
 */

/** One parsed statement line. `amount` is signed: negative is money out. */
data class ImportRow(val date: LocalDate, val amount: Money, val description: String, val fitid: String? = null)

enum class ImportStatus { READY, INCOMING, DUPLICATE, RECURRING }

/** A row on the review screen: what the app thinks, and what the person starts with. */
data class ReviewRow(
    val row: ImportRow,
    val keys: List<String>,
    val payee: String,
    val status: ImportStatus,
    val note: String,
    val include: Boolean,
    /**
     * Where an outflow went: `cat:<id>`, `spend:<goalId>` (spent from a Goal), `charge:<acctId>` (charged to a card) or
     * `payoff:<acctId>` (paid off an Account, out of Savings); null until chosen. §4.3: nothing leaves Savings
     * untracked, so review confirms only once every included outflow has one. Money in: null is Savings, `goal:<id>`
     * goes on into that Goal.
     */
    val target: String?,
)

object Import {

    /**
     * Statement amounts are not app amounts: `1.234,56` and `1,234.56` both occur. When both
     * separators appear the LAST is the decimal point; with one, three trailing digits mean a
     * thousands separator and one or two mean a decimal point. `(12,40)` and `12,40-` are −12,40.
     */
    fun amount(raw: String?): Money? {
        if (raw == null) return null
        var t = raw.trim().filter { it.isDigit() || it in ".,-+()" }
        if (t.isEmpty()) return null
        val neg = (t.startsWith("(") && t.endsWith(")")) || t.startsWith("-") || t.endsWith("-")
        t = t.filter { it !in "()-+" }
        if (t.isEmpty()) return null
        val hasDot = '.' in t; val hasComma = ',' in t
        val n: Double = when {
            hasDot && hasComma -> {
                val dec = if (t.lastIndexOf(',') > t.lastIndexOf('.')) ',' else '.'
                val thou = if (dec == ',') '.' else ','
                t.replace(thou.toString(), "").replace(dec, '.').toDoubleOrNull() ?: return null
            }
            hasDot || hasComma -> {
                val sep = if (hasDot) '.' else ','
                val tail = t.substringAfterLast(sep)
                if (tail.length == 3) t.replace(sep.toString(), "").toDoubleOrNull() ?: return null
                else t.replace(sep, '.').toDoubleOrNull() ?: return null
            }
            else -> t.toDoubleOrNull() ?: return null
        }
        val cents = (n * 100).roundToLong()
        return if (neg) -cents else cents
    }

    /**
     * Statement dates arrive in at least four shapes and the ambiguous one is real: 03/04/2026
     * is 3 April in most of Europe and 4 March in the US. Day-first is assumed (§12's default
     * currency is EUR), and the review screen shows the result before anything is written.
     */
    fun date(raw: String?): LocalDate? {
        val t = raw?.trim() ?: return null
        Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""").find(t)?.let { m ->
            return runCatching { LocalDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})""").find(t)?.let { m ->
            val y = m.groupValues[3].let { if (it.length == 2) "20$it" else it }.toInt()
            return runCatching { LocalDate(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        return null
    }

    /** OFX: each `<STMTTRN>` carries FITID, so dedup is identity rather than resemblance. */
    fun parseOFX(text: String): List<ImportRow> {
        val blocks = Regex("<STMTTRN>[\\s\\S]*?</STMTTRN>", RegexOption.IGNORE_CASE).findAll(text).map { it.value }
        fun field(b: String, tag: String) = Regex("<$tag>([^<\r\n]*)", RegexOption.IGNORE_CASE).find(b)?.groupValues?.get(1)?.trim() ?: ""
        return blocks.mapNotNull { b ->
            val posted = field(b, "DTPOSTED").take(8)
            if (posted.length != 8) return@mapNotNull null
            val d = runCatching { LocalDate(posted.substring(0, 4).toInt(), posted.substring(4, 6).toInt(), posted.substring(6, 8).toInt()) }.getOrNull() ?: return@mapNotNull null
            val amt = amount(field(b, "TRNAMT"))?.takeIf { it != 0L } ?: return@mapNotNull null   // as CSV: a zero row moves nothing
            val name = field(b, "NAME").ifEmpty { field(b, "MEMO") }.ifEmpty { "Statement entry" }
            ImportRow(d, amt, name, field(b, "FITID").ifEmpty { null })
        }.toList()
    }

    private val DATE_KEYS = listOf("date", "data", "datum", "booking date", "transaction date", "value date", "fecha")
    private val DESC_KEYS = listOf("description", "descrizione", "payee", "merchant", "details", "beschreibung", "verwendungszweck", "concepto", "narrative", "reference")
    private val AMOUNT_KEYS = listOf("amount", "importo", "betrag", "value", "importe", "bedrag")
    private val DEBIT_KEYS = listOf("debit", "uscite", "soll", "withdrawal", "paid out")
    private val CREDIT_KEYS = listOf("credit", "entrate", "haben", "deposit", "paid in")

    /** The text's records: a newline ends one only outside quotes, so a quoted multi-line memo stays one field. */
    private fun csvRecords(text: String): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder(); var inQ = false
        for (ch in text) {
            when {
                ch == '"' -> { inQ = !inQ; cur.append(ch) }
                ch == '\n' && !inQ -> { out.add(cur.toString().removeSuffix("\r")); cur.setLength(0) }
                else -> cur.append(ch)
            }
        }
        out.add(cur.toString().removeSuffix("\r"))
        return out.filter { it.isNotBlank() }
    }

    fun splitCsvLine(line: String, delim: Char): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder(); var inQ = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> if (inQ && i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ } else inQ = !inQ
                ch == delim && !inQ -> { out.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out.map { it.trim() }
    }

    private fun words(s: String) = s.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /**
     * The column for [keys], skipping columns another field already claimed. Keys are tried in
     * their listed order -- so "amount" beats "value" -- and a key matches a header that is the
     * key itself or the key plus one more word ("Booking date", "Amount EUR"), never a longer
     * phrase that merely contains it ("Credit card number"). An exact header wins over that.
     */
    private fun find(header: List<String>, keys: List<String>, claimed: Set<Int>): Int {
        val free = header.indices.filter { it !in claimed }
        for (k in keys) free.firstOrNull { header[it].trim() == k }?.let { return it }
        for (k in keys) {
            val kw = words(k)
            free.firstOrNull { i ->
                val hw = words(header[i])
                hw.size <= kw.size + 1 && hw.windowed(kw.size).any { it == kw }
            }?.let { return it }
        }
        // a one-word compound still carries its key: German "Buchungsdatum", "Umsatzbetrag"
        for (k in keys) free.firstOrNull { i -> words(header[i]).let { it.size == 1 && ' ' !in k && it[0].contains(k) } }?.let { return it }
        return -1
    }

    /** CSV: no id, variable columns, `;` where `,` is the decimal separator. Headers by keyword, never by position. */
    fun parseCSV(text: String): List<ImportRow> {
        val lines = csvRecords(text)
        if (lines.isEmpty()) return emptyList()
        val delim = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
        val header = splitCsvLine(lines[0], delim).map { it.lowercase() }
        val iDate = find(header, DATE_KEYS, emptySet())
        val iDesc = find(header, DESC_KEYS, setOf(iDate))
        // split columns first, so "Debit Amount" / "Credit Amount" are never taken as the signed amount
        val iDeb = find(header, DEBIT_KEYS, setOf(iDate, iDesc))
        val iCred = find(header, CREDIT_KEYS, setOf(iDate, iDesc, iDeb))
        val iAmt = find(header, AMOUNT_KEYS, setOf(iDate, iDesc, iDeb, iCred))
        if (iDate < 0 || (iAmt < 0 && iDeb < 0 && iCred < 0)) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val c = splitCsvLine(line, delim)
            val d = date(c.getOrNull(iDate)) ?: return@mapNotNull null
            var amt = if (iAmt >= 0) amount(c.getOrNull(iAmt)) else null
            if (amt == null && iDeb >= 0) amount(c.getOrNull(iDeb))?.takeIf { it != 0L }?.let { amt = -abs(it) }
            if (amt == null && iCred >= 0) amount(c.getOrNull(iCred))?.takeIf { it != 0L }?.let { amt = abs(it) }
            val a = amt ?: return@mapNotNull null
            if (a == 0L) return@mapNotNull null
            val desc = (if (iDesc >= 0) c.getOrNull(iDesc) else null)?.replace(Regex("\\s*\r?\n\\s*"), " ")
            ImportRow(d, a, desc?.ifEmpty { null } ?: "Statement entry")
        }
    }

    /** Both keys where the format allows: `fit:` is identity, `syn:` catches a CSV-then-OFX of one period. */
    fun keys(r: ImportRow): List<String> {
        val syn = "syn:${r.date}|${r.amount.toMajor2()}|${r.description.take(40).lowercase()}"
        return if (r.fitid != null) listOf("fit:${r.fitid}", syn) else listOf(syn)
    }

    private fun Money.toMajor2(): String {
        val neg = this < 0; val a = abs(this)
        return (if (neg) "-" else "") + "${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    }

    fun normalisePayee(s: String): String = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Annotate every parsed row with what the app already knows. Nothing here writes. */
    fun annotate(d: ProfileData, rows: List<ImportRow>): List<ReviewRow> {
        val seen = d.importState.importedKeys.toHashSet()
        val recurring = d.recurringExpenses.map { words(normalisePayee(it.name)) }.filter { it.isNotEmpty() }
        return rows.map { r ->
            val ks = keys(r)
            val payee = normalisePayee(r.description)
            val pw = words(payee)
            var status = ImportStatus.READY; var note = ""
            when {
                ks.any { it in seen } -> {
                    status = ImportStatus.DUPLICATE
                    note = if (r.fitid != null) "Already imported — matched on the bank’s own transaction id" else "Already imported — matched on date, amount and description"
                }
                r.amount > 0 -> { status = ImportStatus.INCOMING; note = "Money in — goes to Savings unless you say otherwise" }
                // whole words either way round: "Rent" is in "Affitto rent", not in "Parent teacher assoc"
                pw.isNotEmpty() && recurring.any { nw -> pw.windowed(nw.size).any { it == nw } || nw.windowed(pw.size).any { it == pw } } -> {
                    status = ImportStatus.RECURRING
                    note = "Looks like a recurring expense the app already sweeps — skipped so it isn’t counted twice"
                }
            }
            val rule = d.importState.payeeRules[payee]?.takeIf { id -> d.categories.any { it.id == id } }
            ReviewRow(r, ks, payee, status, note,
                include = status == ImportStatus.READY || status == ImportStatus.INCOMING,
                target = if (r.amount > 0) null else rule?.let { "cat:$it" })
        }.sortedByDescending { it.row.date }
    }

    /** Where money in can go (§4.3): Savings, or on through Savings into a Goal. Savings is the null target. */
    fun inflowTargets(d: ProfileData): List<Pair<String, String>> =
        listOf("" to "Savings") + d.goals.map { "goal:${it.id}" to "Add to ${it.name}" }

    /** Where an outflow of [amount] can go: every Category, the Goals that hold enough, the cards to charge, every Account to pay off. */
    fun outflowTargets(d: ProfileData, amount: Money): List<Pair<String, String>> =
        d.categories.map { "cat:${it.id}" to it.name } +
            d.goals.filter { it.saved >= amount }.map { "spend:${it.id}" to "Spent from ${it.name}" } +
            d.accounts.filter { it.type == AccountType.CREDIT }.map { "charge:${it.id}" to "Charged to ${it.name}" } +
            d.accounts.map { "payoff:${it.id}" to "Paid off ${it.name}" }

    fun targetExists(d: ProfileData, target: String, amount: Money): Boolean = outflowTargets(d, amount).any { it.first == target }

    /** The included outflows without a usable target; review cannot confirm while there are any. Rows spending from the
     *  same Goal are taken in turn, as [confirm] writes them, so two that together overdraw it are caught here. */
    fun unassigned(d: ProfileData, review: List<ReviewRow>): List<ReviewRow> {
        val holds = d.goals.associate { it.id to it.saved }.toMutableMap()
        return review.filter { rr ->
            if (!rr.include || rr.row.amount >= 0) return@filter false
            val amt = -rr.row.amount; val t = rr.target ?: return@filter true
            if (t.startsWith("spend:")) {
                val left = holds[t.removePrefix("spend:")] ?: return@filter true
                if (amt > left) return@filter true
                holds[t.removePrefix("spend:")] = left - amt; false
            } else !targetExists(d, t, amt)
        }
    }

    /**
     * The write: every included row through the same paths a quick-add uses, with the
     * statement date; assignments are learned as payee rules; both dedup keys are recorded.
     */
    fun confirm(d: ProfileData, review: List<ReviewRow>, clock: Clock, ids: () -> String): ProfileData {
        var next = d
        var rules = d.importState.payeeRules
        val keys = d.importState.importedKeys.toMutableList()
        for (rr in review.filter { it.include }) {
            val r = rr.row
            val amt = abs(r.amount)
            next = when {
                r.amount > 0 -> {
                    val saved = Writes.savingsQuickAdd(next, amt, r.description, false, clock, ids(), r.date)
                    // a Goal deleted while the review was open falls back to Savings
                    val goal = rr.target?.removePrefix("goal:")?.takeIf { id -> saved.goals.any { it.id == id } }
                    if (goal == null) saved else Writes.goalQuickAdd(saved, goal, amt, r.description, clock, ids(), r.date)
                }
                else -> {
                    val target = requireNotNull(rr.target?.takeIf { targetExists(next, it, amt) }) { "an outflow needs a target: ${r.description}" }
                    val id = target.substringAfter(':')
                    when (target.substringBefore(':')) {
                        "cat" -> {
                            if (rr.payee.isNotEmpty()) rules = rules + (rr.payee to id)
                            Writes.categoryQuickAdd(next, id, amt, r.description, clock, ids(), r.date)
                        }
                        "spend" -> Writes.goalSpend(next, id, amt, r.description, clock, ids(), r.date)
                        "charge" -> Accounts.quickAdd(next, id, amt, Accounts.Direction.CHARGE, r.description, false, clock, r.date, ids = ids)
                        else -> Accounts.quickAdd(next, id, amt, Accounts.Direction.PAYMENT, r.description, true, clock, r.date, ids = ids)
                    }
                }
            }
            rr.keys.forEach { if (it !in keys) keys.add(it) }
        }
        return next.copy(importState = next.importState.copy(importedKeys = keys, payeeRules = rules, lastImportDate = clock.today()))
    }
}
