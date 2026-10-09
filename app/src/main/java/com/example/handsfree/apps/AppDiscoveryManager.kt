package com.example.handsfree.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator

data class LaunchableApp(val packageName: String, val name: String, val icon: Bitmap) {
    fun matches(query: String): Boolean = query.trim().let {
        name.contains(it, ignoreCase = true) || packageName.contains(it, ignoreCase = true)
    }
}

enum class LaunchResult { REQUESTED, UNAVAILABLE, BLOCKED }

class AppDiscoveryManager(context: Context) {
    private val context = context.applicationContext
    private val packageManager = context.packageManager

    // Called on Dispatchers.IO: package queries and icon loading can block.
    fun discover(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        val collator = Collator.getInstance()
        return activities.filter {
            it.activityInfo.enabled && it.activityInfo.exported &&
                it.activityInfo.applicationInfo.enabled && it.activityInfo.packageName != context.packageName
        }.distinctBy { it.activityInfo.packageName }.mapNotNull {
            try {
                val info = it.activityInfo.applicationInfo
                LaunchableApp(info.packageName, info.loadLabel(packageManager).toString(),
                    info.loadIcon(packageManager).toBitmap(96, 96))
            } catch (_: PackageManager.NameNotFoundException) {
                null // App was removed while loading the inventory.
            }
        }.sortedWith { a, b ->
            collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.packageName.compareTo(b.packageName)
        }
    }

    fun open(packageName: String): LaunchResult = try {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) LaunchResult.UNAVAILABLE else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            LaunchResult.REQUESTED
        }
    } catch (_: ActivityNotFoundException) {
        LaunchResult.UNAVAILABLE
    } catch (_: SecurityException) {
        LaunchResult.BLOCKED
    }
}
