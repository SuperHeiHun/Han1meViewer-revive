package com.yenaly.han1meviewer

object FirebaseConstants {
    // <editor-fold desc="Remote Config">

    /**
     * 是否启用 CI 更新。
     *
     * 通过 Remote Config 控制 CI 更新渠道。
     */
    const val ENABLE_CI_UPDATE = "enable_ci_update"

    val remoteConfigDefaults: Map<String, Any> = mapOf(
        ENABLE_CI_UPDATE to true
    )

    // </editor-fold>
}
