package com.healthtracker.data.local.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.LocalDate

/**
 * Room type converters for complex types.
 */
class Converters {
    
    private val gson = Gson()
    
    // LocalDate converters
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }
    
    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? {
        return epochDay?.let { LocalDate.ofEpochDay(it) }
    }
    
    // Instant converters
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }
    
    @TypeConverter
    fun toInstant(epochMilli: Long?): Instant? {
        return epochMilli?.let { Instant.ofEpochMilli(it) }
    }
    
    // List<String> converters
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toStringList(json: String?): List<String>? {
        return json?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(it, type)
        }
    }
    
    // Map<String, Double> converters (for standard deviations)
    @TypeConverter
    fun fromDoubleMap(map: Map<String, Double>?): String? {
        return map?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toDoubleMap(json: String?): Map<String, Double>? {
        return json?.let {
            val type = object : TypeToken<Map<String, Double>>() {}.type
            gson.fromJson(it, type)
        }
    }
}
