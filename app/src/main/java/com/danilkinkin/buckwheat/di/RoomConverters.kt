package com.danilkinkin.buckwheat.di

import androidx.room.TypeConverter
import com.danilkinkin.buckwheat.data.entities.RecurrenceInterval
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class RoomConverters {
    @TypeConverter
    fun dateToDateStamp(input: Date): Long = input.time

    @TypeConverter
    fun dateStampToCalendar(input: Long): Date = Date(input)

    @TypeConverter
    fun bigDecimalToString(input: BigDecimal): String = input.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(input: String): BigDecimal = BigDecimal(input)

    @TypeConverter
    fun localDateToEpochDay(input: LocalDate?): Long? = input?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(input: Long?): LocalDate? = input?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun intervalToString(input: RecurrenceInterval?): String? = input?.name

    @TypeConverter
    fun stringToInterval(input: String?): RecurrenceInterval? = input?.let { RecurrenceInterval.valueOf(it) }
}