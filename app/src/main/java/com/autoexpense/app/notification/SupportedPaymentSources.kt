package com.autoexpense.app.notification

/**
 * A known payment application or messaging source.
 *
 * @param displayName  Human-readable name shown in the UI.
 * @param shortName    Short identifier used by the existing UI color logic.
 * @param packageNames All known package names for this application.
 * @param isMessaging  True for generic messaging apps whose notifications must be
 *                     inspected for financial content before being accepted.
 */
data class PaymentSource(
    val displayName: String,
    val shortName: String,
    val packageNames: List<String>,
    val isMessaging: Boolean = false
)

object SupportedPaymentSources {

    val ALL: List<PaymentSource> = listOf(
        // ── UPI payment apps ─────────────────────────────────────────────────
        PaymentSource(
            displayName = "Google Pay",
            shortName = "gpay",
            packageNames = listOf(
                "com.google.android.apps.nbu.paisa.user",
                "com.google.android.apps.walletnfcrel"
            )
        ),
        PaymentSource(
            displayName = "PhonePe",
            shortName = "phonepe",
            packageNames = listOf(
                "com.phonepe.app",
                "com.phonepe.app.preprod"
            )
        ),
        PaymentSource(
            displayName = "Paytm",
            shortName = "paytm",
            packageNames = listOf("net.one97.paytm")
        ),
        PaymentSource(
            displayName = "BHIM",
            shortName = "bhim",
            packageNames = listOf(
                "in.org.npci.upiapp",
                "com.upi.axispay"
            )
        ),

        // ── Messaging apps (require financial-pattern inspection) ─────────────
        PaymentSource(
            displayName = "Messages",
            shortName = "banksms",
            isMessaging = true,
            packageNames = listOf(
                "com.google.android.apps.messaging",   // Google Messages
                "com.android.mms",                     // AOSP Messages
                "com.samsung.android.messaging",       // Samsung Messages
                "com.oneplus.mms",                     // OnePlus Messages
                "com.oppo.mms",                        // OPPO Messages
                "com.coloros.mms",                     // ColorOS Messages
                "com.bbm",                             // BBM
                "com.vivo.mms",                        // Vivo Messages
                "com.android.messaging",               // generic Android Messages
                "com.miui.mms",                        // Xiaomi/MIUI Messages
                "com.miui.mms.ui",                     // Xiaomi/MIUI Messages variant
                "com.truecaller"                       // Truecaller
            )
        )
    )

    /** Returns the [PaymentSource] whose package list contains [packageName], or null. */
    fun findSource(packageName: String): PaymentSource? =
        ALL.firstOrNull { source -> source.packageNames.any { it == packageName } }

    /** Returns true if [packageName] belongs to any known payment application. */
    fun isKnownSource(packageName: String): Boolean = findSource(packageName) != null

    /** Returns true if [packageName] is a messaging app that requires content inspection. */
    fun isMessagingApp(packageName: String): Boolean {
        val lower = packageName.lowercase()
        val matchesConfig = findSource(packageName)?.isMessaging == true
        val matchesPattern = lower.contains("mms") || 
                             lower.contains("messaging") || 
                             lower.contains("sms") || 
                             lower.endsWith(".message")
        return matchesConfig || matchesPattern
    }
}

/**
 * Configurable registry of known bank markers present in Indian bank SMS alerts.
 *
 * Each entry maps one or more keywords found in the SMS text to a display name
 * and a UI short-name. Keywords are matched case-insensitively.
 */
object KnownBanks {
    data class BankInfo(val displayName: String, val shortName: String, val keywords: List<String>)

    val ALL: List<BankInfo> = listOf(
        BankInfo("SBI",          "sbi",     listOf("sbi", "state bank")),
        BankInfo("HDFC Bank",    "hdfc",    listOf("hdfc")),
        BankInfo("ICICI Bank",   "icici",   listOf("icici")),
        BankInfo("Axis Bank",    "axis",    listOf("axis bank", "axisbank")),
        BankInfo("Kotak Bank",   "kotak",   listOf("kotak")),
        BankInfo("Canara Bank",  "canara",  listOf("canara")),
        BankInfo("Indian Bank",  "indian",  listOf("indian bank")),
        BankInfo("PNB",          "pnb",     listOf("pnb", "punjab national")),
        BankInfo("BOB",          "bob",     listOf("bank of baroda", "bob")),
        BankInfo("Union Bank",   "union",   listOf("union bank")),
        BankInfo("Yes Bank",     "yes",     listOf("yes bank")),
        BankInfo("IndusInd",     "indusind",listOf("indusind"))
    )

    fun detect(text: String): BankInfo? {
        val lower = text.lowercase()
        return ALL.firstOrNull { bank -> bank.keywords.any { lower.contains(it) } }
    }
}
