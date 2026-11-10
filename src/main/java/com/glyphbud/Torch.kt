package com.glyphbud

import android.content.Context
import android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
import android.hardware.camera2.CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL
import android.hardware.camera2.CameraManager
import android.os.Build.VERSION.SDK_INT

class Torch(context: Context) {
    private val cm = context.getSystemService(CameraManager::class.java)
    private val id = cm.cameraIdList.firstOrNull {
        cm.getCameraCharacteristics(it).get(FLASH_INFO_AVAILABLE) == true
    }

    val maxLevel = if (id != null && SDK_INT >= 33) {
        cm.getCameraCharacteristics(id).get(FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    } else 1

    // Hardware strength for L1..L5. Retune here if a step looks wrong on a real phone.
    val levels = IntArray(5) { maxOf(1, (it + 1) * maxLevel / 5) }

    val available get() = id != null

    /** 0 is off, 1..5 picks a strength from [levels]. Throws if the camera is busy. */
    fun set(level: Int) {
        val id = id ?: return
        when {
            level == 0 -> cm.setTorchMode(id, false)
            SDK_INT >= 33 && maxLevel > 1 -> cm.turnOnTorchWithStrengthLevel(id, levels[level - 1])
            else -> cm.setTorchMode(id, true)
        }
    }
}
