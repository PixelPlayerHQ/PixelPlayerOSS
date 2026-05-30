package com.lostf1sh.pixelplayeross.baselineprofile

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_APP_ID = "com.lostf1sh.pixelplayeross"
internal const val BENCHMARK_EXTRA = "is_benchmark"

private const val TAG = "BenchmarkEnvironment"
private const val DEFAULT_FOREGROUND_TIMEOUT_MS = 15_000L

internal fun benchmarkTargetPackageName(): String =
    InstrumentationRegistry.getArguments().getString("targetAppId") ?: TARGET_APP_ID

internal fun MacrobenchmarkScope.setupBenchmarkPermissions(packageName: String = benchmarkTargetPackageName()) {
    val deniedPermissions = requiredRuntimePermissions().mapNotNull { permission ->
        val grantOutput = executeShellCommandSafely("pm grant $packageName $permission")
        if (isPermissionGranted(packageName, permission)) {
            null
        } else {
            "$permission (state=denied, grant=${grantOutput.compactShellOutput()})"
        }
    }

    executeShellCommandSafely("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")

    if (deniedPermissions.isNotEmpty()) {
        throw IllegalStateException(
            "Cannot run benchmarks because required runtime permissions were not granted for " +
                "$packageName: ${deniedPermissions.joinToString()}"
        )
    }
}

internal fun MacrobenchmarkScope.executeBenchmarkShellCommand(command: String): String =
    executeShellCommandSafely(command)

internal fun MacrobenchmarkScope.waitForAppForeground(
    context: String,
    packageName: String = benchmarkTargetPackageName(),
    timeoutMs: Long = DEFAULT_FOREGROUND_TIMEOUT_MS
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        device.waitForIdle(250L)
        if (isTargetPackageVisible(packageName)) {
            return
        }
        Thread.sleep(250L)
    }
    assertAppForeground(context, packageName)
}

internal fun MacrobenchmarkScope.assertAppForeground(
    context: String,
    packageName: String = benchmarkTargetPackageName()
) {
    val currentPackage = device.currentPackageName
    if (isTargetPackageVisible(packageName)) {
        return
    }

    val focus = executeShellCommandSafely(
        "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp|mFocusedWindow' | head -n 5"
    ).compactShellOutput()

    throw IllegalStateException(
        "$context attempted while the target app was not visible. Expected visible package " +
            "$packageName, but currentPackage(lastAccessibilityPackage)=$currentPackage, focus=$focus"
    )
}

internal fun MacrobenchmarkScope.waitForTargetPackageVisible(
    packageName: String,
    timeoutMs: Long = DEFAULT_FOREGROUND_TIMEOUT_MS
) {
    if (!device.wait(Until.hasObject(By.pkg(packageName)), timeoutMs)) {
        throw IllegalStateException("Timed out waiting for $packageName to render a UI hierarchy")
    }
}

private fun requiredRuntimePermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_AUDIO)
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.POST_NOTIFICATIONS)
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.R) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
    }
}

private fun MacrobenchmarkScope.executeShellCommandSafely(command: String): String =
    try {
        device.executeShellCommand(command).orEmpty()
    } catch (exception: Exception) {
        Log.w(TAG, "Ignoring shell failure for: $command", exception)
        exception.message.orEmpty()
    }

private fun MacrobenchmarkScope.isPermissionGranted(packageName: String, permission: String): Boolean =
    executeShellCommandSafely("dumpsys package $packageName")
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$permission:") }
        ?.contains("granted=true") == true

private fun MacrobenchmarkScope.isTargetPackageVisible(packageName: String): Boolean =
    device.hasObject(By.pkg(packageName))

private fun String.compactShellOutput(): String =
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(separator = " ")
        .take(240)
