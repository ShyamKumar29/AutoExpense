package com.autoexpense.app

import com.autoexpense.app.data.CustomCategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptsAndCorrectionTest {

    @Test
    fun testTransactionCorrectionInvariants() {
        val original = Transaction(
            id = "tx_101",
            merchant = "Swiggy",
            sub = "Food",
            source = "gpay",
            category = "🍔 Food & Dining",
            amount = "₹450.00",
            date = "14 Jul, 1:30 PM",
            status = "confirmed",
            timestamp = 1700000000000L,
            note = ""
        )

        // Simulate user editing merchant, category, and note while amount remains read-only
        val updated = original.copy(
            merchant = "Swiggy Gourmet",
            category = "Groceries",
            note = "Team lunch reimbursement"
        )

        // 1. Transaction ID must remain exactly the same (not creating a new ID)
        assertEquals("tx_101", updated.id)
        // 2. Detected payment amount must be preserved exactly
        assertEquals("₹450.00", updated.amount)
        // 3. Updated fields reflect corrections
        assertEquals("Swiggy Gourmet", updated.merchant)
        assertEquals("Groceries", updated.category)
        assertEquals("Team lunch reimbursement", updated.note)
    }

    @Test
    fun testSearchQueryFiltering() {
        val tx1 = Transaction("1", "Swiggy", "", "gpay", "Food & Dining", "₹450", "14 Jul", "confirmed", timestamp = 1000L, note = "")
        val tx2 = Transaction("2", "Amazon", "", "phonepe", "Shopping", "₹1,200", "14 Jul", "confirmed", timestamp = 2000L, note = "Birthday gift")
        val tx3 = Transaction("3", "Uber", "", "paytm", "Transport", "₹250", "14 Jul", "confirmed", timestamp = 3000L, note = "")
        val all = listOf(tx1, tx2, tx3)

        // Search by merchant (case insensitive and trimmed)
        val resMerchant = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(searchQuery = "  swiggy  "))
        assertEquals(1, resMerchant.size)
        assertEquals("1", resMerchant[0].id)

        // Search by category
        val resCat = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(searchQuery = "shopping"))
        assertEquals(1, resCat.size)
        assertEquals("2", resCat[0].id)

        // Search by note
        val resNote = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(searchQuery = "birthday"))
        assertEquals(1, resNote.size)
        assertEquals("2", resNote[0].id)

        // Search by source
        val resSource = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(searchQuery = "paytm"))
        assertEquals(1, resSource.size)
        assertEquals("3", resSource[0].id)
    }

    @Test
    fun testDateRangeFiltering() {
        val tx1 = Transaction("1", "Store A", "", "gpay", "Food", "₹100", "1 Jul", "confirmed", timestamp = 1000L)
        val tx2 = Transaction("2", "Store B", "", "gpay", "Food", "₹200", "10 Jul", "confirmed", timestamp = 5000L)
        val tx3 = Transaction("3", "Store C", "", "gpay", "Food", "₹300", "20 Jul", "confirmed", timestamp = 10000L)
        val all = listOf(tx1, tx2, tx3)

        val dateFilter = DateFilter(startMs = 3000L, endMs = 7000L, label = "Mid July")
        val filtered = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(dateFilter = dateFilter))

        assertEquals(1, filtered.size)
        assertEquals("2", filtered[0].id)
    }

    @Test
    fun testCategoryTypeFiltering() {
        val txBuiltIn = Transaction("1", "Swiggy", "", "gpay", "Food & Dining", "₹200", "14 Jul", "confirmed", timestamp = 1000L)
        val txCustom = Transaction("2", "Gadget Store", "", "phonepe", "Gadgets", "₹5,000", "14 Jul", "confirmed", timestamp = 2000L)
        val all = listOf(txBuiltIn, txCustom)

        val customCats = listOf(CustomCategoryEntity(id = 1, name = "Gadgets", iconName = "📱"))

        val builtInOnly = ReceiptsViewModel.filterAndSortTransactions(all, customCats, ReceiptsFilterSettings(categoryFilter = CategoryFilterOption.BUILT_IN))
        assertEquals(1, builtInOnly.size)
        assertEquals("1", builtInOnly[0].id)

        val customOnly = ReceiptsViewModel.filterAndSortTransactions(all, customCats, ReceiptsFilterSettings(categoryFilter = CategoryFilterOption.USER_CREATED))
        assertEquals(1, customOnly.size)
        assertEquals("2", customOnly[0].id)
    }

    @Test
    fun testPaymentSourceFiltering() {
        val tx1 = Transaction("1", "Store A", "", "gpay", "Food", "₹100", "14 Jul", "confirmed", timestamp = 1000L)
        val tx2 = Transaction("2", "Store B", "", "phonepe", "Food", "₹200", "14 Jul", "confirmed", timestamp = 2000L)
        val tx3 = Transaction("3", "Store C", "", "paytm", "Food", "₹300", "14 Jul", "confirmed", timestamp = 3000L)
        val all = listOf(tx1, tx2, tx3)

        val gpayRes = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(sourceFilter = "gpay"))
        assertEquals(1, gpayRes.size)
        assertEquals("1", gpayRes[0].id)

        val phonepeRes = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(sourceFilter = "phonepe"))
        assertEquals(1, phonepeRes.size)
        assertEquals("2", phonepeRes[0].id)
    }

    @Test
    fun testAmountRangeFiltering() {
        val tx1 = Transaction("1", "Store A", "", "gpay", "Food", "₹100.00", "14 Jul", "confirmed", timestamp = 1000L)
        val tx2 = Transaction("2", "Store B", "", "gpay", "Food", "₹450.50", "14 Jul", "confirmed", timestamp = 2000L)
        val tx3 = Transaction("3", "Store C", "", "gpay", "Food", "₹1,200.00", "14 Jul", "confirmed", timestamp = 3000L)
        val all = listOf(tx1, tx2, tx3)

        val rangeRes = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(minAmount = 200.0, maxAmount = 800.0))
        assertEquals(1, rangeRes.size)
        assertEquals("2", rangeRes[0].id)
    }

    @Test
    fun testSortingBehavior() {
        val tx1 = Transaction("1", "Zebra Store", "", "gpay", "Food", "₹100", "14 Jul", "confirmed", timestamp = 1000L)
        val tx2 = Transaction("2", "Apple Store", "", "gpay", "Food", "₹500", "14 Jul", "confirmed", timestamp = 3000L)
        val tx3 = Transaction("3", "Mango Store", "", "gpay", "Food", "₹300", "14 Jul", "confirmed", timestamp = 2000L)
        val all = listOf(tx1, tx2, tx3)

        // Newest first
        val newest = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(sortOption = SortOption.NEWEST_FIRST))
        assertEquals(listOf("2", "3", "1"), newest.map { it.id })

        // Oldest first
        val oldest = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(sortOption = SortOption.OLDEST_FIRST))
        assertEquals(listOf("1", "3", "2"), oldest.map { it.id })

        // Highest amount
        val highest = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(sortOption = SortOption.HIGHEST_AMOUNT))
        assertEquals(listOf("2", "3", "1"), highest.map { it.id })

        // Lowest amount
        val lowest = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(sortOption = SortOption.LOWEST_AMOUNT))
        assertEquals(listOf("1", "3", "2"), lowest.map { it.id })

        // Merchant A-Z
        val az = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(sortOption = SortOption.MERCHANT_AZ))
        assertEquals(listOf("Apple Store", "Mango Store", "Zebra Store"), az.map { it.merchant })

        // Merchant Z-A
        val za = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), ReceiptsFilterSettings(sortOption = SortOption.MERCHANT_ZA))
        assertEquals(listOf("Zebra Store", "Mango Store", "Apple Store"), za.map { it.merchant })
    }

    @Test
    fun testMultiFilterCombinations() {
        val tx1 = Transaction("1", "Uber Rides", "", "gpay", "Transport", "₹180", "14 Jul", "confirmed", timestamp = 1000L)
        val tx2 = Transaction("2", "Uber Eats", "", "phonepe", "Food", "₹450", "14 Jul", "confirmed", timestamp = 2000L)
        val tx3 = Transaction("3", "Uber Lux", "", "gpay", "Transport", "₹1,500", "14 Jul", "confirmed", timestamp = 3000L)
        val tx4 = Transaction("4", "Amazon", "", "gpay", "Shopping", "₹300", "14 Jul", "confirmed", timestamp = 4000L)
        val all = listOf(tx1, tx2, tx3, tx4)

        // Filter for "Uber", minAmount = 150, maxAmount = 600, source = "phonepe", sorted by highest amount
        val settings = ReceiptsFilterSettings(
            searchQuery = "Uber",
            sourceFilter = "phonepe",
            minAmount = 150.0,
            maxAmount = 600.0,
            sortOption = SortOption.HIGHEST_AMOUNT
        )

        val result = ReceiptsViewModel.filterAndSortTransactions(all, emptyList(), settings)
        assertEquals(1, result.size)
        assertEquals("2", result[0].id)
    }

    @Test
    fun testDeleteTransactionRecalculatesTotals() {
        val nowMs = System.currentTimeMillis()
        val tx1 = Transaction("1", "Store A", "", "gpay", "Food", "₹300.00", "14 Jul", "confirmed", timestamp = nowMs)
        val tx2 = Transaction("2", "Store B", "", "gpay", "Shopping", "₹700.00", "14 Jul", "confirmed", timestamp = nowMs)

        val beforeDelete = listOf(tx1, tx2)
        assertEquals("₹1,000.00", DashboardViewModel.computeTotalSpent(beforeDelete, nowMs))

        // Simulate deleting tx1
        val afterDelete = beforeDelete.filter { it.id != "1" }
        assertEquals("₹700.00", DashboardViewModel.computeTotalSpent(afterDelete, nowMs))
    }
}
