package com.autoexpense.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {
    private lateinit var db: AutoExpenseDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AutoExpenseDatabase::class.java).build()
        dao = db.transactionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun createDummyEntity(
        id: String,
        status: String,
        fingerprint: String = "fp_$id"
    ) = TransactionEntity(
        id = id,
        merchantOrRecipient = "Test Merchant",
        amount = "-₹100",
        source = "test_source",
        category = "❓ Unknown",
        status = status,
        timestamp = System.currentTimeMillis(),
        detectionReason = "Test",
        safeNotificationExcerpt = "Test excerpt",
        transactionFingerprint = fingerprint,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    @Test
    fun insertAndObserveNeedsReview() = runBlocking {
        val entity = createDummyEntity("txn1", "review")
        dao.insert(entity)

        val needsReviewList = dao.observeNeedsReview().first()
        assertEquals(1, needsReviewList.size)
        assertEquals("txn1", needsReviewList[0].id)
    }

    @Test
    fun confirmTransaction() = runBlocking {
        val entity = createDummyEntity("txn2", "review")
        dao.insert(entity)

        dao.confirmTransaction("txn2", "🍔 Food")

        val confirmedList = dao.observeConfirmed().first()
        assertEquals(1, confirmedList.size)
        assertEquals("txn2", confirmedList[0].id)
        assertEquals("🍔 Food", confirmedList[0].category)

        val needsReviewList = dao.observeNeedsReview().first()
        assertEquals(0, needsReviewList.size)
    }

    @Test
    fun ignoreTransaction() = runBlocking {
        val entity = createDummyEntity("txn3", "review")
        dao.insert(entity)

        dao.ignoreTransaction("txn3")

        val needsReviewList = dao.observeNeedsReview().first()
        assertEquals(0, needsReviewList.size)

        val confirmedList = dao.observeConfirmed().first()
        assertEquals(0, confirmedList.size)
    }

    @Test
    fun checkDuplicateFingerprint() = runBlocking {
        val entity = createDummyEntity("txn4", "review", fingerprint = "t2|100.0|test|bank")
        dao.insert(entity)

        val exists = dao.existsByFingerprint("t2|100.0|test|bank")
        assertTrue(exists)

        val doesNotExist = dao.existsByFingerprint("non_existent_fp")
        assertFalse(doesNotExist)
    }
}
