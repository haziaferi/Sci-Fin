@file:OptIn(ExperimentalTime::class)

package app.scifin

import app.scifin.model.Account
import app.scifin.model.AccountType
import app.scifin.model.Cadence
import app.scifin.model.Category
import app.scifin.model.DateMode
import app.scifin.model.Goal
import app.scifin.model.GoalMode
import app.scifin.model.IncomeCadence
import app.scifin.model.ProfileData
import app.scifin.model.RateType
import app.scifin.model.RecurringExpense
import app.scifin.model.Savings
import app.scifin.model.Transaction
import app.scifin.model.moneyOf
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The parity fixture: the prototype's seeded world, exported by `tools/export_fixture.mjs`.
 * The prototype is the second oracle, not the source -- where the two disagree the spec
 * decides and §13.4 records it. Amounts in the file are major units (the prototype's floats);
 * they become minor units here.
 */
object Fixture {
    val root: JsonObject by lazy {
        val text = Fixture::class.java.getResourceAsStream("/prototype-seed.json")!!.readBytes().decodeToString()
        Json.parseToJsonElement(text).jsonObject
    }

    val simDate: LocalDate get() = LocalDate.parse(root["simDate"]!!.jsonPrimitive.content)

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { this !is JsonNull }
    private fun JsonElement?.money(): Long? = (this as? JsonPrimitive)?.doubleOrNull?.let { moneyOf(it) }
    private fun JsonElement?.date(): LocalDate? = str()?.let { LocalDate.parse(it) }
    private fun JsonElement?.bool(): Boolean = (this as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
    private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull

    const val PROFILE = "p-fixture"

    val transactions: List<Transaction> by lazy {
        root["transactions"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            Transaction(
                id = o["id"].str()!!, profileId = PROFILE,
                date = o["date"].date()!!,
                loggedAt = Instant.parse(o["loggedAt"].str()!!),
                payee = o["payee"].str()?.takeIf { it.isNotEmpty() },
                amount = o["amount"].money()!!,
                key = o["key"].str()!!, targetLabel = o["targetLabel"].str() ?: "",
                isIncome = o["isIncome"].bool(),
            )
        }
    }

    val goals: List<Goal> by lazy {
        root["goals"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            Goal(o["id"].str()!!, PROFILE, o["name"].str()!!, o["icon"].str(), saved = o["saved"].money() ?: 0,
                target = o["target"].money(), targetDate = o["targetDate"].date(),
                mode = o["mode"].str()?.let { GoalMode.of(it) }, monthly = o["monthly"].money())
        }
    }

    val categories: List<Category> by lazy {
        root["categories"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            Category(o["id"].str()!!, PROFILE, o["name"].str()!!, o["icon"].str(),
                planned = o["planned"].money() ?: 0, spent = o["spent"].money() ?: 0, lastResetMonth = o["lastResetMonth"].str())
        }
    }

    val recurringExpenses: List<RecurringExpense> by lazy {
        root["recurringExpenses"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            RecurringExpense(
                o["id"].str()!!, PROFILE, o["name"].str()!!, o["icon"].str(), o["amount"].money()!!, Cadence.of(o["cadence"].str()!!),
                firstDueDate = o["firstDueDate"].date()!!, dueDate = o["dueDate"].date()!!, dueDate2 = o["dueDate2"].date(),
                dueDateAnchorDay = o["dueDateAnchorDay"].int(), dateMode = o["dateMode"].str()?.let { DateMode.of(it) },
                intervalDays = o["intervalDays"].int(), apportionedShare = o["apportionedShare"].money(),
                reminderEnabled = o["reminderEnabled"].bool(), reminderLeadDays = o["reminderLeadDays"].int() ?: 3,
                lastSweptMonth = o["lastSweptMonth"].str(),
                endDate = o["endDate"].date(), finishedAt = o["finishedAt"].date(), linkedAccountId = o["linkedAccountId"].str(),
            )
        }
    }

    val accounts: List<Account> by lazy {
        root["accounts"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            Account(
                o["id"].str()!!, PROFILE, o["name"].str()!!, o["icon"].str(), AccountType.of(o["type"].str()!!),
                balance = o["balance"].money() ?: 0, apr = o["apr"].dbl(), linkedRecurringExpenseId = o["linkedRecurringExpenseId"].str(),
                autoUpdateFromPayments = o["autoUpdateFromPayments"].bool(), principal = o["principal"].money(),
                interestRate = o["interestRate"].dbl(), termMonths = o["termMonths"].int(),
                rateType = o["rateType"].str()?.let { RateType.of(it) }, originationDate = o["originationDate"].date(),
            )
        }
    }

    /** The whole seeded Profile, as the prototype held it at `simDate`. */
    val profile: ProfileData by lazy {
        val s = root["settings"]!!.jsonObject
        ProfileData.fresh(PROFILE, "Fixture").let { base ->
            base.copy(
                savings = Savings(PROFILE, root["savings"].money()),
                settings = base.settings.copy(
                    incomeEnabled = s["incomeEnabled"].bool(), income = s["income"].money() ?: 0,
                    incomeCadence = IncomeCadence.of(s["incomeCadence"].str()!!),
                    incomePayDate = s["incomePayDate"].date(), incomeEnabledDate = s["incomeEnabledDate"].date(),
                ),
                goals = goals, categories = categories, recurringExpenses = recurringExpenses,
                accounts = accounts, transactions = transactions,
            )
        }
    }

    val figures: JsonObject get() = root["figures"]!!.jsonObject
}
