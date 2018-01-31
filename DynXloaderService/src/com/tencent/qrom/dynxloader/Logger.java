/**
 * 
 */
package com.tencent.qrom.dynxloader;
import android.util.Log;

/*
 * Copyright (C) 2005-2011 TENCENT Inc.All Rights Reserved.		
 * 
 */


public final class Logger {
    // Switch to control whether to enable log
    private static final boolean ENABLE_LOG = true;

    private static final String LOG_TAG = "DynXloaderService";

    private Logger() {
    }

    /**
     * Send a {@link #VERBOSE} log message.
     * @param msg The message you would like logged.
     */
    public static void v(String msg) {
        if (ENABLE_LOG) {
            Log.v(LOG_TAG, msg);
        }
    }

    /**
     * Send a {@link #VERBOSE} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void v(String tag, String msg) {
        if (ENABLE_LOG) {
            Log.v(tag, msg);
        }
    }

    /**
     * Send a {@link #VERBOSE} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static void v(String tag, String msg, Throwable tr) {
        if (ENABLE_LOG) {
            Log.v(tag, msg, tr);
        }
    }

    /**
     * Send a {@link #DEBUG} log message.
     * @param msg The message you would like logged.
     */
    public static void d(String msg) {
        if (ENABLE_LOG) {
            Log.d(LOG_TAG, msg);
        }
    }

    /**
     * Send a {@link #DEBUG} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void d(String tag, String msg) {
        if (ENABLE_LOG) {
            Log.d(tag, msg);
        }
    }

    /**
     * Send a {@link #DEBUG} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static void d(String tag, String msg, Throwable tr) {
        if (ENABLE_LOG) {
            Log.d(tag, msg, tr);
        }
    }

    /**
     * Send a {@link #INFO} log message.
     * @param msg The message you would like logged.
     */
    public static void i(String msg) {
        if (ENABLE_LOG) {
            Log.i(LOG_TAG, msg);
        }
    }
    
    /**
     * Send an {@link #INFO} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void i(String tag, String msg) {
        if (ENABLE_LOG) {
            Log.i(tag, msg);
        }
    }

    /**
     * Send a {@link #INFO} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static void i(String tag, String msg, Throwable tr) {
        if (ENABLE_LOG) {
            Log.i(tag, msg, tr);
        }
    }

    /**
     * Send a {@link #WARN} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void w(String tag, String msg) {
        if (ENABLE_LOG) {
            Log.w(tag, msg);
        }
    }

    /**
     * Send a {@link #WARN} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static void w(String tag, String msg, Throwable tr) {
        if (ENABLE_LOG) {
            Log.d(tag, msg, tr);
        }
    }

    /**
     * Checks to see whether or not log is enabled.
     */
    public static boolean isLogEnabled() {
        return ENABLE_LOG;
    }

    /**
     * Send an {@link #ERROR} log message.
     * @param msg The message you would like logged.
     */
    public static int e(String msg) {
        return Log.e(LOG_TAG, msg);
    }

    /**
     * Send an {@link #ERROR} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int e(String tag, String msg) {
        return Log.e(tag, msg);
    }

    /**
     * Send a {@link #ERROR} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int e(String tag, String msg, Throwable tr) {
        return Log.e(tag, msg, tr);
    }
}
