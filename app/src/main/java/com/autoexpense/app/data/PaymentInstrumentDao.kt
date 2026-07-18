package com.autoexpense.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentInstrumentDao {
    @Query("SELECT * FROM payment_instruments WHERE isArchived = 0 ORDER BY displayName ASC")
    fun observeActive(): Flow<List<PaymentInstrumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(instrument: PaymentInstrumentEntity)
}
