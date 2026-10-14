package app.scifin.ui.screens

import kotlinx.datetime.LocalDate

/*
 * §9.3's Learn topic and its country list. The prose is written from §9.3's own bullets and
 * never from the prototype's card text (the document-notes rule), and the topic is deliberately
 * not linked from either calculator: it names a tension this app's own stance creates, and a
 * link from a calculator would read as the recommendation §9.3 rules out.
 *
 * Nothing here computes or predicts a score, and nothing here tells anyone what to do.
 */

const val CREDIT_TITLE = "Credit scores, and what this app doesn’t do"

val CREDIT_BODY = listOf(
    "This app leans toward keeping money rather than borrowing it. That is a design choice, and it has a " +
        "consequence worth naming. Nothing here calculates a credit score or predicts one: the formulas are " +
        "proprietary, the app does not hold the data they run on, and the rules differ by country and, in the " +
        "United States, by state.",
    "In countries that score credit — primarily the United States — having no credit history at all is its own " +
        "state, usually called being credit invisible. It is not the same as having bad credit. It means there is " +
        "too little record to score you in either direction. It can carry real friction: an application assessed " +
        "by hand rather than automatically, or an offer only from higher-cost lenders.",
    "So an app that steers away from debt can steer someone toward that state. The tension is real, it is created " +
        "by this app’s own stance, and naming it is as far as this goes. There is no recommendation here, in " +
        "either direction.",
    "It also does not fall evenly. Research by the Consumer Financial Protection Bureau finds that Black and " +
        "Hispanic consumers are considerably more likely to be credit invisible or unscorable than White or Asian " +
        "consumers; that people in low-income neighbourhoods are affected at roughly seven times the rate of " +
        "people in upper-income ones; and that young adults and recent immigrants, establishing credit for the " +
        "first time, are over-represented too. That is a fact about how the on-ramps into the scored-credit " +
        "system were built, not about the people standing outside them.",
    "Whether a score exists at all, and whether you can obtain one about yourself, differs by country.",
)

/**
 * One country. [scored] answers §9.3's classification test -- *can an ordinary person obtain a
 * score about themselves and act on it?* -- which is a question about what they can get and use,
 * not about whether a formula is standardised or the body producing it is public.
 *
 * [checked] is the day the entry's source was last confirmed. Nothing re-checks it afterwards:
 * that is a known, accepted limitation, which is why the date is shown rather than hidden.
 */
data class CreditResource(
    val country: String,
    val scored: Boolean,
    val body: String,
    val source: String,
    val url: String,
    val checked: LocalDate,
)

const val CREDIT_LIST_LABEL = "Where a score exists, by country"
const val CREDIT_SCORED_LABEL = "Where a score exists and you can get it"
const val CREDIT_UNSCORED_LABEL = "No consumer-facing credit score"
const val CREDIT_SOURCING_NOTE =
    "Government and central-bank sources only — never a credit bureau’s own site, and never a commercial blog, " +
        "since those tend to sell credit-building products. Each link is only as current as its date: treat this " +
        "as a starting point, not a substitute for getting properly informed."

val CREDIT_RESOURCES = listOf(
    CreditResource(
        "United States", true,
        "A consumer-facing score you can obtain about yourself.",
        "Consumer Financial Protection Bureau",
        "consumerfinance.gov/consumer-tools/credit-reports-and-scores/",
        LocalDate(2026, 8, 4),
    ),
    CreditResource(
        "Canada", true,
        "Scored, but with no single standardized formula — Equifax and TransUnion each run their own model.",
        "Financial Consumer Agency of Canada",
        "canada.ca/en/financial-consumer-agency/services/credit-reports-score.html",
        LocalDate(2026, 8, 4),
    ),
    CreditResource(
        "Germany", true,
        "Scored, with no single standardized formula: private bureaus each produce their own consumer-facing " +
            "score you can request about yourself. BaFin, the financial regulator, does not supervise credit " +
            "bureaus or their scoring, so the government source here is the federal data-protection authority.",
        "BfDI, the federal data-protection authority",
        "bfdi.bund.de/DE/Buerger/Inhalte/Allgemein/Auskunfteien/FAQ-Auskunfteien.html",
        LocalDate(2026, 9, 2),
    ),
    CreditResource(
        "Italy", false,
        "Creditworthiness is assessed through the Banca d’Italia’s Centrale dei Rischi: a public register of " +
            "debts above a reporting threshold, not a score and not a blacklist.",
        "Banca d’Italia",
        "bancaditalia.it/servizi-cittadino/servizi/accesso-cr/",
        LocalDate(2026, 8, 4),
    ),
    CreditResource(
        "Spain", false,
        "Creditworthiness is assessed through the Banco de España’s CIRBE — the same public-register shape as Italy’s.",
        "Banco de España",
        "bde.es/wbe/en/para-ciudadano/gestiones/informacion-riesgos-cir/",
        LocalDate(2026, 8, 4),
    ),
    CreditResource(
        "France", false,
        "The Banque de France keeps negative-incident registries: FICP for credit-repayment incidents, FCC for " +
            "cheque incidents. Each bank then runs its own undisclosed scoring on top, which is worth naming even " +
            "though no resource can point at it.",
        "Banque de France",
        "banque-france.fr/fr/a-votre-service/particuliers/fichiers-incident-bancaire",
        LocalDate(2026, 8, 4),
    ),
)
