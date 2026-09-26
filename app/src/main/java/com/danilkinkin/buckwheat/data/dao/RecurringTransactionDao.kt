package com.danilkinkin.buckwheat.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecurringTransactionDao {
    @Query("SELECT * FROM recurring_transactions ORDER BY nextOccurrence ASC")
    fun getAll(): Flow<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions ORDER BY nextOccurrence ASC")
    fun getAllLiveData(): LiveData<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 ORDER BY nextOccurrence ASC")
    fun getActive(): Flow<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 ORDER BY nextOccurrence ASC")
    suspend fun getActiveList(): List<RecurringTransaction>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 AND nextOccurrence <= :date ORDER BY nextOccurrence ASC")
    suspend fun getDueTransactions(date: LocalDate): List<RecurringTransaction>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: Long): RecurringTransaction?

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<RecurringTransaction?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recurringTransaction: RecurringTransaction): Long

    @Update
    suspend fun update(recurringTransaction: RecurringTransaction)

    @Delete
    suspend fun delete(recurringTransaction: RecurringTransaction)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAll()
}
