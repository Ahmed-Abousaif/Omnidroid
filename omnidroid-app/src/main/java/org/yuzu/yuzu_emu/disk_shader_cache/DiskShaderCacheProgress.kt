package org.yuzu.yuzu_emu.disk_shader_cache

import androidx.annotation.Keep

@Keep
object DiskShaderCacheProgress {
    var progressListener: ((stage: Int, progress: Int, max: Int) -> Unit)? = null

    @JvmStatic
    fun loadProgress(stage: Int, progress: Int, max: Int) {
        progressListener?.invoke(stage, progress, max)
    }

    @Keep
    enum class LoadCallbackStage {
        Prepare,
        Build,
        Complete,
    }
}

