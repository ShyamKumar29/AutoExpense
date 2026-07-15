package com.autoexpense.app.notification

import com.autoexpense.app.data.MerchantAliasRepository
import java.util.Locale

object SmartMerchantCleaner {

    /**
     * Priority order:
     * 1. User-learned merchant alias
     * 2. Common merchant mapping (~40 Indian brands, conservative matching)
     * 3. Safe generic cleanup
     * 4. Original merchant name as fallback
     */
    fun cleanMerchant(rawMerchant: String): String {
        val trimmedRaw = rawMerchant.trim()
        if (trimmedRaw.isEmpty() || trimmedRaw.equals("Unknown Merchant", ignoreCase = true) || trimmedRaw.equals("❓ Unknown", ignoreCase = true)) {
            return "Unknown Merchant"
        }

        // 1. User-learned merchant alias
        val learnedAlias = MerchantAliasRepository.getAlias(trimmedRaw)
        if (!learnedAlias.isNullOrBlank()) {
            return learnedAlias
        }

        // 2. Common merchant mapping
        val commonMapped = matchCommonMerchant(trimmedRaw)
        if (commonMapped != null) {
            return commonMapped
        }

        // 3. Safe generic cleanup
        val cleaned = performGenericCleanup(trimmedRaw)
        if (cleaned.isNotBlank() && cleaned.any { it.isLetterOrDigit() }) {
            return cleaned
        }

        // 4. Original merchant name as fallback
        return trimmedRaw
    }

    private fun matchCommonMerchant(raw: String): String? {
        // Normalize string for conservative check (remove punctuation except spaces, upper case, collapse spaces)
        val cleanUpper = raw.uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleanUpper.isEmpty()) return null

        fun matchesKeyword(keyword: String): Boolean {
            return cleanUpper == keyword ||
                   cleanUpper.startsWith("$keyword ") ||
                   cleanUpper.endsWith(" $keyword") ||
                   cleanUpper.contains(" $keyword ")
        }

        fun matchesExactPhrase(phrase: String): Boolean {
            return cleanUpper.contains(phrase)
        }

        // Food
        if (matchesKeyword("SWIGGY") || matchesExactPhrase("BUNDL TECHNOLOGIES")) return "Swiggy"
        if (matchesKeyword("ZOMATO")) return "Zomato"
        if (matchesKeyword("BLINKIT") || matchesExactPhrase("BLINK COMMERCE") || matchesKeyword("GROFERS")) return "Blinkit"
        if (matchesKeyword("ZEPTO") || matchesExactPhrase("KIRANAKART")) return "Zepto"
        if (matchesKeyword("BIGBASKET") || matchesExactPhrase("BIG BASKET") || matchesExactPhrase("SUPERMARKET GROCERY")) return "BigBasket"
        if (matchesKeyword("INSTAMART") || matchesExactPhrase("SWIGGY INSTAMART")) return "Instamart"
        if (matchesKeyword("DOMINOS") || matchesExactPhrase("JUBILANT FOODWORKS")) return "Domino’s"
        if (matchesKeyword("MCDONALDS") || matchesExactPhrase("HARDCASTLE RESTAURANTS")) return "McDonald’s"

        // Transport
        if (matchesKeyword("UBER") || matchesExactPhrase("UBER INDIA SYSTEMS") || matchesExactPhrase("UBER TECHNOLOGIES")) return "Uber"
        if (matchesExactPhrase("OLA CABS") || matchesExactPhrase("ANI TECHNOLOGIES") || matchesKeyword("OLACABS") || cleanUpper == "OLA" || cleanUpper.startsWith("OLA ")) return "Ola"
        if (matchesKeyword("RAPIDO") || matchesExactPhrase("ROPPEN TRANSPORTATION")) return "Rapido"
        if (matchesKeyword("REDBUS") || matchesExactPhrase("PILANI SOFTLABS")) return "RedBus"
        if (matchesKeyword("IRCTC")) return "IRCTC"

        // Shopping
        if (matchesKeyword("AMAZON") || matchesExactPhrase("AMAZON PAY") || matchesExactPhrase("AMAZON SELLER SERVICES")) return "Amazon"
        if (matchesKeyword("FLIPKART") || matchesExactPhrase("FLIPKART INTERNET") || matchesExactPhrase("FLIPKART INDIA")) return "Flipkart"
        if (matchesKeyword("MYNTRA") || matchesExactPhrase("MYNTRA DESIGNS")) return "Myntra"
        if (matchesKeyword("MEESHO") || matchesExactPhrase("FASHNEAR TECHNOLOGIES")) return "Meesho"
        if (matchesKeyword("AJIO") || matchesExactPhrase("RELIANCE RETAIL AJIO")) return "Ajio"

        // Entertainment
        if (matchesKeyword("NETFLIX") || matchesExactPhrase("NETFLIX ENTERTAINMENT")) return "Netflix"
        if (matchesKeyword("SPOTIFY") || matchesExactPhrase("SPOTIFY INDIA")) return "Spotify"
        if (matchesKeyword("YOUTUBE") || matchesExactPhrase("GOOGLE YOUTUBE")) return "YouTube"
        if (matchesKeyword("BOOKMYSHOW") || matchesExactPhrase("BIGTREE ENTERTAINMENT")) return "BookMyShow"
        if (matchesKeyword("STEAM") || matchesExactPhrase("VALVE CORPORATION STEAM") || matchesExactPhrase("STEAM PURCHASE")) return "Steam"

        // Bills and recharge
        if (matchesKeyword("JIO") || matchesExactPhrase("RELIANCE JIO") || matchesExactPhrase("MYJIO") || matchesExactPhrase("JIO PREPAID")) return "Jio"
        if (matchesKeyword("AIRTEL") || matchesExactPhrase("BHARTI AIRTEL") || matchesExactPhrase("AIRTEL PAYMENTS")) return "Airtel"
        if (matchesKeyword("VI") && (raw.contains("Vodafone Idea", ignoreCase = true) || raw.contains("Vi Prepaid", ignoreCase = true) || raw.contains("Vi Postpaid", ignoreCase = true))) return "Vi"
        if (matchesExactPhrase("VODAFONE IDEA")) return "Vi"
        if (matchesKeyword("BSNL") || matchesExactPhrase("BHARAT SANCHAR NIGAM")) return "BSNL"
        if (matchesExactPhrase("GOOGLE PLAY") || matchesExactPhrase("GOOGLE PLAY STORE") || matchesExactPhrase("GOOGLE PLAY APPS")) return "Google Play"

        // Travel and services
        if (matchesKeyword("MAKEMYTRIP") || matchesExactPhrase("MMT INDIA")) return "MakeMyTrip"
        if (matchesKeyword("GOIBIBO")) return "Goibibo"
        if (matchesKeyword("CLEARTRIP")) return "Cleartrip"
        if (matchesKeyword("PORTER") || matchesExactPhrase("SMARTSHIFT LOGISTICS")) return "Porter"
        if (matchesExactPhrase("URBAN COMPANY") || matchesKeyword("URBANCLAP") || matchesExactPhrase("URBANCLAP TECHNOLOGIES")) return "Urban Company"

        return null
    }

    private fun performGenericCleanup(raw: String): String {
        var s = raw.trim()

        // 1. Safely remove common UPI handles only when they appear as handle suffixes
        // e.g. @oksbi @okaxis @okhdfcbank @okicici @ybl @ibl @axl @paytm @sbi @icici @axisbank @hdfcbank @postbank etc.
        s = s.replace(Regex("@[a-zA-Z0-9.-]+$", RegexOption.IGNORE_CASE), "").trim()
        if (s.isEmpty()) return raw.trim()

        // 2. Remove obvious standalone payment metadata suffixes or tokens such as UPI, UPI PAYMENT, UPI TXN, PAYMENT, TXN, POS, ECOM, PVT, LTD, LIMITED
        // Remove trailing hyphens/underscores/separators followed by UPI/PAYMENT variants (e.g. -UPI, - UPI PAYMENT)
        var previous: String
        do {
            previous = s
            s = s.replace(Regex("[_\\-*\\s]+(UPI PAYMENT|UPI TXN|UPI|PAYMENT|TXN|POS|ECOM|PVT LTD|PVT|LTD|LIMITED)$", RegexOption.IGNORE_CASE), "").trim()
        } while (s != previous && s.isNotBlank() && s.any { it.isLetterOrDigit() })

        if (s.isEmpty() || !s.any { it.isLetterOrDigit() }) return raw.trim()

        // Strip leading metadata prefixes like UPI- or PAYMENT-
        do {
            previous = s
            s = s.replace(Regex("^(UPI PAYMENT|UPI TXN|UPI|PAYMENT|TXN)[_\\-*\\s]+", RegexOption.IGNORE_CASE), "").trim()
        } while (s != previous && s.isNotBlank() && s.any { it.isLetterOrDigit() })

        if (s.isEmpty() || !s.any { it.isLetterOrDigit() }) return raw.trim()

        // 3. Replace underscores and unnecessary separators with spaces
        s = s.replace(Regex("[_\\-]+"), " ")

        // 4. Normalize repeated spaces and leading/trailing punctuation
        s = s.replace(Regex("^[^a-zA-Z0-9]+|[^a-zA-Z0-9.()]+$"), "")
        s = s.replace(Regex("\\s+"), " ").trim()

        if (s.isEmpty() || !s.any { it.isLetterOrDigit() }) return raw.trim()

        // 5. Normalize all-uppercase (or all-lowercase) merchant names into readable Title Case
        val hasLower = s.any { it.isLowerCase() }
        val hasUpper = s.any { it.isUpperCase() }

        if (!hasLower || !hasUpper) {
            s = s.split(" ").joinToString(" ") { word ->
                if (word.isNotEmpty()) {
                    word.lowercase(Locale.US).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                } else ""
            }
        }

        return s
    }
}
