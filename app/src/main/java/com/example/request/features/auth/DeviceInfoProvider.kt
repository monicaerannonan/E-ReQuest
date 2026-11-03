package com.example.request.features.auth

import android.content.Context
import android.os.Build
import java.util.Locale
import java.util.UUID

data class StudentDeviceInfo(
    val deviceId: String,
    val brand: String,
    val model: String,
    val deviceName: String,
    val osVersion: String,
    val sdkInt: Int,
    val appVersion: String,
    val platform: String = "android"
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "deviceId" to deviceId,
            "brand" to brand,
            "model" to model,
            "deviceName" to deviceName,
            "osVersion" to osVersion,
            "sdkInt" to sdkInt,
            "appVersion" to appVersion,
            "platform" to platform
        )
    }
}

object DeviceInfoProvider {

    private const val DEVICE_PREFS_NAME = "erequest_device_preferences"
    private const val KEY_INSTALLATION_ID = "installation_id"

    fun getOrCreateInstallationId(context: Context): String {
        val appContext = context.applicationContext
        val androidId = try {
            android.provider.Settings.Secure.getString(
                appContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )?.trim()?.ifEmpty { null }
        } catch (_: Exception) {
            null
        }

        if (!androidId.isNullOrBlank()) {
            // Deterministic RFC-4122 v3 UUID derived from the device's persistent hardware-backed ANDROID_ID.
            // This survives uninstalls and reinstalls on the exact same physical device!
            return UUID.nameUUIDFromBytes("erequest_device_$androidId".toByteArray(Charsets.UTF_8)).toString()
        }

        val prefs = appContext.getSharedPreferences(
            DEVICE_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        var installationId = prefs.getString(KEY_INSTALLATION_ID, null)
        if (installationId.isNullOrBlank()) {
            installationId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, installationId).apply()
        }
        return installationId
    }

    fun getDeviceInfo(context: Context): StudentDeviceInfo {
        val appContext = context.applicationContext
        val deviceId = getOrCreateInstallationId(appContext)
        val rawBrand = (Build.MANUFACTURER ?: "Android").trim()
        val brand = rawBrand.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
        val model = (Build.MODEL ?: "Device").trim()
        val deviceName = if (model.startsWith(brand, ignoreCase = true)) {
            model
        } else {
            "$brand $model"
        }
        val osVersion = "Android ${Build.VERSION.RELEASE ?: "Unknown"} (SDK ${Build.VERSION.SDK_INT})"
        val appVersion = getAppVersionName(appContext)

        return StudentDeviceInfo(
            deviceId = deviceId,
            brand = brand,
            model = model,
            deviceName = deviceName,
            osVersion = osVersion,
            sdkInt = Build.VERSION.SDK_INT,
            appVersion = appVersion
        )
    }

    fun getAppVersionName(context: Context): String {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }
}
