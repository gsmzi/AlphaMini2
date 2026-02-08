package com.ubtrobot.mini.sdkdemo.voicedialogue.v2

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Copies embedded TTS data from assets to app-internal storage on first run.
 */
object EmbeddedTtsInstaller {
    private const val TAG = "EmbeddedTtsInstaller"
    private const val ASSET_ROOT = "espeak-ng-data"
    private const val MARKER = ".installed"

    fun ensureInstalled(context: Context): File? {
        val targetDir = File(context.filesDir, ASSET_ROOT)
        val marker = File(targetDir, MARKER)
        if (marker.exists()) return targetDir

        return try {
            copyAssetDir(context, ASSET_ROOT, targetDir)
            marker.createNewFile()
            Log.d(TAG, "Embedded TTS data installed to ${targetDir.absolutePath}")
            targetDir
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install embedded TTS data", e)
            null
        }
    }

    private fun copyAssetDir(context: Context, assetPath: String, outDir: File) {
        val assets = context.assets
        val entries = assets.list(assetPath) ?: emptyArray()
        if (entries.isEmpty()) {
            // It's a file
            outDir.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                FileOutputStream(outDir).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        if (!outDir.exists()) outDir.mkdirs()

        for (entry in entries) {
            val childAsset = "$assetPath/$entry"
            val childOut = File(outDir, entry)
            val childEntries = assets.list(childAsset) ?: emptyArray()
            if (childEntries.isEmpty()) {
                assets.open(childAsset).use { input ->
                    FileOutputStream(childOut).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                copyAssetDir(context, childAsset, childOut)
            }
        }
    }
}
