package com.example.bruffwalkingtour

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object LogUtils {
    
    private val logEntries = ConcurrentLinkedQueue<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private const val MAX_LOG_ENTRIES = 500 // Keep last 500 log entries
    
    private fun addLogEntry(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "$timestamp $level/$tag: $message"
        
        logEntries.add(logEntry)
        
        // Remove old entries if we exceed the limit
        while (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.poll()
        }
    }
    
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG_LOGGING) {
            Log.d(tag, message)
            addLogEntry("D", tag, message)
        }
    }
    
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG_LOGGING) {
            Log.i(tag, message)
            addLogEntry("I", tag, message)
        }
    }
    
    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG_LOGGING) {
            Log.w(tag, message)
            addLogEntry("W", tag, message)
        }
    }
    
    fun w(tag: String, message: String, throwable: Throwable) {
        if (BuildConfig.DEBUG_LOGGING) {
            Log.w(tag, message, throwable)
            addLogEntry("W", tag, "$message\n${throwable.stackTraceToString()}")
        }
    }
    
    fun e(tag: String, message: String) {
        // Always log errors, even in production
        Log.e(tag, message)
        addLogEntry("E", tag, message)
    }
    
    fun e(tag: String, message: String, throwable: Throwable) {
        // Always log errors, even in production
        Log.e(tag, message, throwable)
        addLogEntry("E", tag, "$message\n${throwable.stackTraceToString()}")
    }
    
    fun getAllLogs(): List<String> {
        return logEntries.toList()
    }
    
    fun clearLogs() {
        logEntries.clear()
        addLogEntry("I", "LogUtils", "Logs cleared by admin")
    }
}