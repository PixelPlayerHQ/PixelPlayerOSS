package com.lostf1sh.pixelplayeross.utils

import timber.log.Timber

object LogUtils {

    private const val MAX_TAG_LENGTH = 23

    private fun getTag(instance: Any): String {
        val className = (instance as? String) ?: instance.javaClass.simpleName
        return className.take(MAX_TAG_LENGTH)
    }

    private fun buildLogMessage(message: String): String {
        return "[${Thread.currentThread().name}] - $message"
    }

    fun d(tagProvider: Any, message: String, vararg args: Any?) {
        Timber.tag(getTag(tagProvider)).d(buildLogMessage(message), *args)
    }

    fun i(tagProvider: Any, message: String, vararg args: Any?) {
        Timber.tag(getTag(tagProvider)).i(buildLogMessage(message), *args)
    }

    fun w(tagProvider: Any, message: String, vararg args: Any?) {
        Timber.tag(getTag(tagProvider)).w(buildLogMessage(message), *args)
    }

    fun e(tagProvider: Any, throwable: Throwable? = null, message: String, vararg args: Any?) {
        Timber.tag(getTag(tagProvider)).e(throwable, buildLogMessage(message), *args)
    }

    fun v(tagProvider: Any, message: String, vararg args: Any?) {
        Timber.tag(getTag(tagProvider)).v(buildLogMessage(message), *args)
    }
}
