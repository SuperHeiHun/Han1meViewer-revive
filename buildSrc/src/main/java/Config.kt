@file:Suppress("UnstableApiUsage")

import org.gradle.api.Project
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * @author Yenaly Liew
 * @time 2023/11/25 025 17:55
 */
object Config {

    val Project.isRelease: Boolean
        get() = gradle.startParameter.taskNames.any { it.contains("Release") }

    object Version {

        const val DEBUG = "debug"
        const val RELEASE = "release"
        const val CI = "ci"

        /**
         * 创建版本号
         *
         * @return 版本号和版本名
         *
         *         Example:
         *         ci: 0.1.2-ci+21032500
         *         release: 0.1.2-release+21032500
         *         debug (fixed): debug+1
         */
        fun Project.createVersion(
            major: Int, minor: Int, patch: Int
        ): Pair<Int, String> {
            val source = this.source
            val versionCode: Int
            val versionName: String
            when (source) {
                DEBUG -> {
                    versionCode = 1
                    versionName = "$DEBUG+$versionCode"
                }

                else -> {
                    val now = LocalDateTime.now(Clock.systemUTC())
                    // yyMMddHH 后面追加一位 0-9 的分钟分桶，让同一小时内最多
                    // 十次 CI 构建都能得到不同且递增的 versionCode。
                    // 9 位数字仍远低于 Android 的 2,100,000,000 上限。
                    versionCode = now.format(
                        DateTimeFormatter.ofPattern("yyMMddHH")
                    ).toInt() * 10 + now.minute / 6
                    versionName = "${major}.${minor}.${patch}-$source+$versionCode"
                }
            }
            println("Version Code: $versionCode")
            println("Version Name: $versionName")
            return versionCode to versionName
        }

        /**
         * 版本来源，用于区分不同的版本
         *
         * @return ci 或 release 或 debug
         */
        val Project.source: String
            get() = System.getenv("HA1_VERSION_SOURCE") ?: kotlin.run {
                return if (isRelease) RELEASE else DEBUG
            }
    }

    val Project.lastCommitSha: String
        get() = providers.exec {
            commandLine = "git rev-parse --short=7 HEAD".split(' ')
        }.standardOutput.asText.get().trim()

    val thisYear: Int
        get() = LocalDateTime.now(Clock.system(ZoneId.of("UTC+8"))).year
}