package com.autoexpense.app.notification

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.autoexpense.app.BuildConfig
import com.autoexpense.app.Transaction
import com.autoexpense.app.TransactionRepository

/**
 * Processes raw [StatusBarNotification] objects and, when a valid outgoing payment is
 * detected, inserts a [Transaction] with status "review" into [TransactionRepository].
 *
 * Deduplication uses a two-tier fingerprint:
 * - Tier 1 (strongest): bank reference number.
 * - Tier 2 (fallback):  normalized amount + recipient + bank within 5-minute window.
 */
object NotificationProcessor {

    private const val TAG = "AutoExpenseNotification"
    private const val DEDUP_WINDOW_MS = 5 * 60 * 1000L  // 5 minutes

    // Financial keywords that reliably indicate an outgoing bank debit.
    // Used to rank candidates: a candidate containing any of these is tried
    // before candidates that do not, regardless of relative length.
    private val FINANCIAL_KEYWORDS = listOf(
        "debited by", "debited for", "debited with",
        "trf to", "transferred to",
        "upi", "rs.", "inr", "₹", "a/c"
    )

    private fun hasFinancialKeyword(text: String): Boolean {
        val lower = text.lowercase()
        return FINANCIAL_KEYWORDS.any { lower.contains(it) }
    }

    @Volatile private var fingerprintMap = HashMap<String, Long>()

    // ── Real notification path ────────────────────────────────────────────────

    fun process(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName.isNullOrBlank()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "DROPPED reason=null_or_blank_package")
            return
        }

        val notification = sbn.notification
        if (notification == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "DROPPED pkg=$packageName reason=null_notification_object")
            return
        }

        val extras = notification.extras
        if (extras == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "DROPPED pkg=$packageName reason=null_extras_bundle")
            return
        }

        val id = sbn.id

        // Note: group-summary notifications are NOT filtered because some OEM ROMs
        // (Xiaomi/MIUI, etc.) may only deliver group summaries. They are logged for
        // diagnostic purposes. Deduplication prevents double-counting if both
        // individual and summary notifications arrive.
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

        // ── Source filter ─────────────────────────────────────────────────────
        val isKnown = SupportedPaymentSources.isKnownSource(packageName)
        val isMessaging = SupportedPaymentSources.isMessagingApp(packageName)
        if (!isKnown && !isMessaging) {
            if (BuildConfig.DEBUG) Log.d(TAG, "FILTERED pkg=$packageName id=$id reason=unknown_source_package")
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "SOURCE_PASSED pkg=$packageName id=$id isKnown=$isKnown isMessaging=$isMessaging")
        }

        // ── Text extraction ──────────────────────────────────────────────────
        // Sources tried in order of typical richness:
        //   tickerText    – set at notification post time before MIUI can truncate extras;
        //                   often contains the full SMS as "Sender: full message body"
        //   EXTRA_BIG_TEXT – expanded view; usually fuller than EXTRA_TEXT
        //   EXTRA_TEXT     – collapsed preview, may be truncated by the OS or OEM ROM
        //   EXTRA_TEXT_LINES – inbox-style lines (one per pending message)
        //   EXTRA_MESSAGES / EXTRA_HISTORIC_MESSAGES – MessagingStyle bundles
        //   EXTRA_SUB_TEXT / EXTRA_SUMMARY_TEXT – secondary fields

        val tickerText = notification.tickerText?.toString()?.trim() ?: ""
        val extraText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val extraBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""
        val extraSub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
        val extraSummary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim() ?: ""

        // Also try getCharSequence for EXTRA_TITLE – getString() silently returns null
        // when the stored value is a SpannableString (common on Android 13+).
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""

        val linesList = mutableListOf<String>()
        try {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (lines != null) {
                for (line in lines) {
                    val s = line?.toString()?.trim() ?: ""
                    if (s.isNotBlank()) linesList.add(s)
                }
            }
        } catch (_: Exception) {}

        // On Android 13+ (API 33) the untyped getParcelableArray() is deprecated and
        // silently returns null for Bundle arrays. Use the typed overload there.
        val messagingStyleTexts = mutableListOf<String>()
        fun extractBundleArray(key: String): Array<out android.os.Parcelable>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try { extras.getParcelableArray(key, Bundle::class.java) } catch (_: Exception) { null }
            } else {
                @Suppress("DEPRECATION")
                try { extras.getParcelableArray(key) } catch (_: Exception) { null }
            }

        for (key in listOf(Notification.EXTRA_MESSAGES, Notification.EXTRA_HISTORIC_MESSAGES)) {
            try {
                val arr = extractBundleArray(key)
                if (arr != null) {
                    for (item in arr) {
                        val b = item as? Bundle ?: continue
                        val textVal = b.getCharSequence("text")?.toString()?.trim() ?: ""
                        if (textVal.isNotBlank()) messagingStyleTexts.add(textVal)
                    }
                }
            } catch (_: Exception) {}
        }

        // ── Detailed field-length log (no private content) ───────────────────
        if (BuildConfig.DEBUG) {
            val lineLengths = if (linesList.isEmpty()) "[]"
                else linesList.mapIndexed { i, s -> "line$i=${s.length}" }.joinToString(",", "[", "]")
            val msgLengths = if (messagingStyleTexts.isEmpty()) "[]"
                else messagingStyleTexts.mapIndexed { i, s -> "msg$i=${s.length}" }.joinToString(",", "[", "]")
            Log.d(TAG, "FIELDS pkg=$packageName id=$id isGroupSummary=$isGroupSummary " +
                "titleLen=${title.length} tickerLen=${tickerText.length} " +
                "extraTextLen=${extraText.length} extraBigTextLen=${extraBigText.length} " +
                "extraSubLen=${extraSub.length} extraSummaryLen=${extraSummary.length} " +
                "textLines=$lineLengths msgStyle=$msgLengths")
        }

        // ── Candidate assembly ───────────────────────────────────────────────
        // Collect distinct non-blank candidates. The title (sender name) is NOT
        // added as a body candidate – it will be passed separately to the parser.
        val bodiesRaw = LinkedHashSet<String>()
        if (tickerText.isNotBlank()) bodiesRaw.add(tickerText)
        if (extraBigText.isNotBlank()) bodiesRaw.add(extraBigText)
        if (extraText.isNotBlank()) bodiesRaw.add(extraText)
        if (extraSub.isNotBlank()) bodiesRaw.add(extraSub)
        if (extraSummary.isNotBlank()) bodiesRaw.add(extraSummary)
        bodiesRaw.addAll(linesList)
        bodiesRaw.addAll(messagingStyleTexts)

        // Sort: candidates with financial keywords first (those are the only ones
        // that can produce a ParsedPayment), then by length descending so the
        // most complete version of the SMS is tried before any truncated preview.
        val bodies = bodiesRaw.sortedWith(
            compareByDescending<String> { hasFinancialKeyword(it) }
                .thenByDescending { it.length }
        )

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "RECEIVED pkg=$packageName id=$id " +
                "candidates=${bodies.size} " +
                "financialCandidates=${bodies.count { hasFinancialKeyword(it) }} " +
                "longestLen=${bodies.firstOrNull()?.length ?: 0}")
        }

        if (bodies.isEmpty()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "DROPPED pkg=$packageName id=$id reason=no_body_candidates")
            return
        }

        for (body in bodies) {
            processRawNotification(title, body, packageName, sbn.postTime, id)
        }
    }

    // ── Debug / test path ─────────────────────────────────────────────────────

    fun simulateNotification(title: String, body: String, packageName: String) {
        processRawNotification(title, body, packageName, System.currentTimeMillis(), 999)
    }

    // ── Shared logic ──────────────────────────────────────────────────────────

    private fun processRawNotification(
        title: String,
        body: String,
        packageName: String,
        timestamp: Long,
        notificationId: Int
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "PARSER_REACHED pkg=$packageName id=$notificationId " +
                "bodyLen=${body.length} titleLen=${title.length} " +
                "hasFinancial=${hasFinancialKeyword(body)}")
        }

        val payment = PaymentNotificationParser.parse(
            title = title,
            body = body,
            packageName = packageName,
            timestamp = timestamp
        )

        if (payment == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "PARSER_REJECTED pkg=$packageName id=$notificationId")
            }
            return
        }

        if (isDuplicate(payment)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DUPLICATE pkg=$packageName id=$notificationId")
            }
            return
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "ACCEPTED pkg=$packageName id=$notificationId " +
                "amt=${payment.amount} merchant=${payment.merchantOrRecipient} source=${payment.sourceApplication}")
        }

        try {
            TransactionRepository.addTransaction(convertToTransaction(payment))
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "INSERTED pkg=$packageName id=$notificationId txnId=${payment.id}")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "INSERT_FAILED pkg=$packageName id=$notificationId", e)
            }
        }
    }

    @Synchronized
    private fun isDuplicate(payment: ParsedPayment): Boolean {
        val now = System.currentTimeMillis()

        // Tier 1 – bank reference number
        if (!payment.bankRefNumber.isNullOrBlank()) {
            val refKey = "ref|${payment.bankRefNumber}"
            val lastSeen = fingerprintMap[refKey]
            if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) return true
            fingerprintMap[refKey] = now
        }

        // Tier 2 – normalized amount + recipient + bank
        val normalizedRecipient = payment.merchantOrRecipient.lowercase().replace(Regex("\\s+"), "")
        val bank = KnownBanks.detect(payment.sourceApplication)?.shortName
            ?: payment.sourcePackage.take(8)
        val tier2Key = "t2|${payment.amount}|$normalizedRecipient|$bank"
        val lastSeen2 = fingerprintMap[tier2Key]
        if (lastSeen2 != null && (now - lastSeen2) < DEDUP_WINDOW_MS) return true
        fingerprintMap[tier2Key] = now

        // Prune expired entries
        fingerprintMap.entries.removeIf { (_, v) -> (now - v) > DEDUP_WINDOW_MS }

        return false
    }

    private fun convertToTransaction(payment: ParsedPayment): Transaction {
        val source = SupportedPaymentSources.findSource(payment.sourcePackage)
            ?.takeIf { !it.isMessaging }
            ?.shortName
            ?: KnownBanks.detect(payment.sourceApplication)?.shortName
            ?: "banksms"

        val amountStr = if (payment.amount % 1.0 == 0.0) {
            "−₹${String.format(java.util.Locale.US, "%,.0f", payment.amount)}"
        } else {
            "−₹${String.format(java.util.Locale.US, "%,.2f", payment.amount)}"
        }

        val dateStr = java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.US)
            .format(java.util.Date(payment.timestamp))

        return Transaction(
            id = payment.id,
            merchant = payment.merchantOrRecipient,
            sub = payment.sourceApplication,
            source = source,
            category = "❓ Unknown",
            amount = amountStr,
            date = dateStr,
            status = "review",
            notificationExcerpt = payment.safeNotificationExcerpt,
            detectionReason = payment.detectionReason
        )
    }
}
