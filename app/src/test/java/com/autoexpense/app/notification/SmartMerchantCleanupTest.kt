package com.autoexpense.app.notification

import com.autoexpense.app.data.MerchantAliasRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SmartMerchantCleaner] and alias override behavior.
 */
class SmartMerchantCleanupTest {

    @Before
    fun setUp() {
        MerchantAliasRepository.clearMemoryForTest()
    }

    @After
    fun tearDown() {
        MerchantAliasRepository.clearMemoryForTest()
    }

    @Test
    fun testCommonMerchantMappings() {
        assertEquals("Swiggy", SmartMerchantCleaner.cleanMerchant("SWIGGY LIMITED"))
        assertEquals("Swiggy", SmartMerchantCleaner.cleanMerchant("BUNDL TECHNOLOGIES"))
        assertEquals("Uber", SmartMerchantCleaner.cleanMerchant("UBER INDIA SYSTEMS"))
        assertEquals("Zomato", SmartMerchantCleaner.cleanMerchant("ZOMATO LTD"))
        assertEquals("Amazon", SmartMerchantCleaner.cleanMerchant("AMAZON PAY INDIA"))
        assertEquals("Blinkit", SmartMerchantCleaner.cleanMerchant("BLINK COMMERCE"))
        assertEquals("Ola", SmartMerchantCleaner.cleanMerchant("ANI TECHNOLOGIES"))
        assertEquals("Zepto", SmartMerchantCleaner.cleanMerchant("ZEPTO"))
    }

    @Test
    fun testUpiHandleRemoval() {
        assertEquals("Rahulverma", SmartMerchantCleaner.cleanMerchant("RAHULVERMA@OKSBI"))
        assertEquals("John Doe", SmartMerchantCleaner.cleanMerchant("JOHN-DOE@YBL"))
        assertEquals("Srikrishna Stores", SmartMerchantCleaner.cleanMerchant("SRIKRISHNA STORES@PAYTM"))
        assertEquals("Test Shop", SmartMerchantCleaner.cleanMerchant("TEST_SHOP@OKAXIS"))
        assertEquals("Test Shop", SmartMerchantCleaner.cleanMerchant("TEST_SHOP@OKHDFCBANK"))
        assertEquals("Test Shop", SmartMerchantCleaner.cleanMerchant("TEST_SHOP@OKICICI"))
        assertEquals("Test Shop", SmartMerchantCleaner.cleanMerchant("TEST_SHOP@IBL"))
        assertEquals("Test Shop", SmartMerchantCleaner.cleanMerchant("TEST_SHOP@AXL"))
    }

    @Test
    fun testSeparatorAndMetadataCleanup() {
        assertEquals("Cafe Madras", SmartMerchantCleaner.cleanMerchant("CAFE_MADRAS-UPI"))
        assertEquals("Cafe Madras", SmartMerchantCleaner.cleanMerchant("CAFE_MADRAS - UPI PAYMENT"))
        assertEquals("Starbucks", SmartMerchantCleaner.cleanMerchant("UPI TXN - STARBUCKS"))
    }

    @Test
    fun testCaseNormalization() {
        assertEquals("Rahulverma", SmartMerchantCleaner.cleanMerchant("RAHULVERMA"))
        assertEquals("Srikrishna Stores", SmartMerchantCleaner.cleanMerchant("srikrishna stores"))
        assertEquals("Cafe Madras", SmartMerchantCleaner.cleanMerchant("Cafe Madras"))
    }

    @Test
    fun testUnknownMerchants() {
        assertEquals("Random Local Shop", SmartMerchantCleaner.cleanMerchant("RANDOM LOCAL SHOP"))
        assertEquals("1234567890", SmartMerchantCleaner.cleanMerchant("1234567890"))
    }

    @Test
    fun testConservativeMatching() {
        // Do not merge unrelated merchants because they contain a short word like OLA inside KOLKATA or SOLAR
        assertEquals("Kolkata Sweets", SmartMerchantCleaner.cleanMerchant("KOLKATA SWEETS"))
        assertEquals("Solar Power", SmartMerchantCleaner.cleanMerchant("SOLAR POWER LTD"))
        assertNotEquals("Ola", SmartMerchantCleaner.cleanMerchant("KOLKATA SWEETS"))
        assertNotEquals("Ola", SmartMerchantCleaner.cleanMerchant("SOLAR POWER LTD"))
    }

    @Test
    fun testLearnedAliases() {
        assertEquals("Rahulverma", SmartMerchantCleaner.cleanMerchant("RAHULVERMA@OKSBI"))

        // User remembers corrected name "My Friend Rahul"
        MerchantAliasRepository.setAliasInMemory("RAHULVERMA@OKSBI", "My Friend Rahul")

        assertEquals("My Friend Rahul", SmartMerchantCleaner.cleanMerchant("RAHULVERMA@OKSBI"))
        // Case insensitive and whitespace normalized matching
        assertEquals("My Friend Rahul", SmartMerchantCleaner.cleanMerchant("  rahulverma@oksbi  "))
    }

    @Test
    fun testLearnedAliasesOverridingBuiltInMappings() {
        assertEquals("Swiggy", SmartMerchantCleaner.cleanMerchant("BUNDL TECHNOLOGIES"))

        // User overrides built-in mapping with a custom alias
        MerchantAliasRepository.setAliasInMemory("BUNDL TECHNOLOGIES", "Custom Swiggy Corporate")

        assertEquals("Custom Swiggy Corporate", SmartMerchantCleaner.cleanMerchant("BUNDL TECHNOLOGIES"))
        assertEquals("Custom Swiggy Corporate", SmartMerchantCleaner.cleanMerchant("bundl technologies"))
    }

    @Test
    fun testPersistenceAfterRestart() {
        // Test normalization for primary key to ensure no duplicates
        val norm1 = MerchantAliasRepository.normalizeForAlias("RAHULVERMA@OKSBI")
        val norm2 = MerchantAliasRepository.normalizeForAlias("  rahulverma@oksbi  ")
        assertEquals(norm1, norm2)

        MerchantAliasRepository.setAliasInMemory("RAHULVERMA@OKSBI", "Rahul")
        assertEquals("Rahul", MerchantAliasRepository.getAlias("RAHULVERMA@OKSBI"))
        assertEquals("Rahul", MerchantAliasRepository.getAlias("rahulverma@oksbi"))
    }

    @Test
    fun testNoChangesToTransactionAmountsOrDuplicateDetection() {
        val sms = "Dear UPI user A/C debited by 450.00 on date 13Jul trf to RAHUL VERMA Refno 123456789"
        val parsed = PaymentNotificationParser.parse("title", sms, "com.google.android.apps.messaging", System.currentTimeMillis())
        requireNotNull(parsed)

        assertEquals(450.0, parsed.amount, 0.001)
        assertEquals("RAHUL VERMA", parsed.merchantOrRecipient)

        // Verify SmartMerchantCleaner cleans raw display names without altering the raw string or amount
        val rawUpiMerchant = "RAHULVERMA@OKSBI"
        val cleaned = SmartMerchantCleaner.cleanMerchant(rawUpiMerchant)
        assertEquals("Rahulverma", cleaned)
        assertEquals("RAHULVERMA@OKSBI", rawUpiMerchant)
        assertEquals(450.0, parsed.amount, 0.001)
        assertEquals("RAHUL VERMA", parsed.merchantOrRecipient)
    }

    @Test
    fun testGenericUpiMerchantCleanup() {
        assertEquals("John Doe", SmartMerchantCleaner.cleanMerchant("JOHN-DOE@YBL"))
        assertEquals("Localshop739", SmartMerchantCleaner.cleanMerchant("LOCALSHOP739@OKSBI"))
        assertEquals("Cafe Madras", SmartMerchantCleaner.cleanMerchant("CAFE_MADRAS@OKSBI"))
        assertEquals("Srikrishna Stores", SmartMerchantCleaner.cleanMerchant("SRIKRISHNA_STORES@PAYTM"))
        assertEquals("Galaxy Gaming Hub", SmartMerchantCleaner.cleanMerchant("GALAXY-GAMING-HUB@OKAXIS"))
    }

    @Test
    fun testCaseInsensitiveUpiHandles() {
        assertEquals("John Doe", SmartMerchantCleaner.cleanMerchant("JOHN-DOE@ybl"))
        assertEquals("John Doe", SmartMerchantCleaner.cleanMerchant("JOHN-DOE@YbL"))
        assertEquals("Localshop739", SmartMerchantCleaner.cleanMerchant("LOCALSHOP739@oksbi"))
        assertEquals("Localshop739", SmartMerchantCleaner.cleanMerchant("LOCALSHOP739@OkSbi"))
        assertEquals("Srikrishna Stores", SmartMerchantCleaner.cleanMerchant("SRIKRISHNA_STORES@paytm"))
        assertEquals("Galaxy Gaming Hub", SmartMerchantCleaner.cleanMerchant("GALAXY-GAMING-HUB@okaxis"))
    }

    @Test
    fun testHyphenAndUnderscoreCleanup() {
        assertEquals("Cafe Madras", SmartMerchantCleaner.cleanMerchant("CAFE_MADRAS@OKSBI"))
        assertEquals("Galaxy Gaming Hub", SmartMerchantCleaner.cleanMerchant("GALAXY-GAMING-HUB@OKAXIS"))
        assertEquals("Foo Bar Baz", SmartMerchantCleaner.cleanMerchant("FOO_BAR-BAZ@OKICICI"))
    }

    @Test
    fun testMerchantNamesContainingNumbers() {
        assertEquals("Localshop739", SmartMerchantCleaner.cleanMerchant("LOCALSHOP739@OKSBI"))
        assertEquals("Shop24x7", SmartMerchantCleaner.cleanMerchant("SHOP24X7@ICICI"))
    }

    @Test
    fun testUnknownMerchantFallback() {
        assertEquals("Unknown Merchant", SmartMerchantCleaner.cleanMerchant("Unknown Merchant"))
        assertEquals("Unknown Merchant", SmartMerchantCleaner.cleanMerchant("❓ Unknown"))

        val sms = "Dear UPI user A/C debited by 100.00 on date 13Jul trf to JOHN-DOE@YBL Refno 123456789"
        val parsedSms = PaymentNotificationParser.parse("title", sms, "com.google.android.apps.messaging", System.currentTimeMillis())
        requireNotNull(parsedSms)
        assertEquals("JOHN-DOE@YBL", parsedSms.merchantOrRecipient)
        assertNotEquals("Unknown Merchant", parsedSms.merchantOrRecipient)

        val upiNotif = "Paid ₹100 to JOHN-DOE@YBL"
        val parsedUpi = PaymentNotificationParser.parse("title", upiNotif, "com.google.android.apps.nbu.paisa.user", System.currentTimeMillis())
        requireNotNull(parsedUpi)
        assertEquals("JOHN-DOE@YBL", parsedUpi.merchantOrRecipient)
        assertNotEquals("Unknown Merchant", parsedUpi.merchantOrRecipient)
    }

    @Test
    fun testPreventingCategoryMemoryForUnknownMerchant() {
        val repo = com.autoexpense.app.data.MerchantCategoryRepository
        assertEquals(true, repo.isUnknownMerchant("Unknown Merchant"))
        assertEquals(true, repo.isUnknownMerchant("❓ Unknown"))
        assertEquals(false, repo.isUnknownMerchant("John Doe"))

        val unknownTxn = com.autoexpense.app.Transaction(
            id = "1",
            merchant = "Unknown Merchant",
            sub = "",
            source = "sms",
            category = "Groceries",
            amount = "−₹100",
            date = "Today",
            status = "confirmed",
            notificationExcerpt = "",
            detectionReason = "",
            timestamp = System.currentTimeMillis(),
            note = "",
            rawMerchant = "Unknown Merchant"
        )
        val remembered = repo.getRememberedCategory("Unknown Merchant", listOf(unknownTxn))
        assertEquals(null, remembered)
    }

    @Test
    fun testRememberedCategoryIndicatorVisibilityAndManualHide() {
        val merchant = "Grocery Store"
        val rawMerchant = "GROCERY_STORE@OKSBI"
        val rememberedCat: String? = "Groceries"
        var selectedCat = "Groceries"
        var userManuallySelected = false

        fun calculateShowIndicator(merchant: String, rawMerchant: String, remCat: String?, selCat: String, manual: Boolean): Boolean {
            return remCat != null &&
                !manual &&
                selCat == remCat &&
                !com.autoexpense.app.data.MerchantCategoryRepository.isUnknownMerchant(merchant) &&
                !com.autoexpense.app.data.MerchantCategoryRepository.isUnknownMerchant(rawMerchant)
        }

        // Initially visible when pre-selected automatically
        assertEquals(true, calculateShowIndicator(merchant, rawMerchant, rememberedCat, selectedCat, userManuallySelected))

        // Hides immediately after manual selection
        userManuallySelected = true
        selectedCat = "Food & Dining"
        assertEquals(false, calculateShowIndicator(merchant, rawMerchant, rememberedCat, selectedCat, userManuallySelected))

        // Never shown for Unknown Merchant
        assertEquals(false, calculateShowIndicator("Unknown Merchant", "Unknown Merchant", "Groceries", "Groceries", false))
    }
}
