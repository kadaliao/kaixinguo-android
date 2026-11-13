pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // openhongguo 的临时发布：上游 main（2769fb6，含播放签名修复 beilunyang/openhongguo#7
        // 和弹幕接口）打的 v0.3.0-kx.1，aar 直接从 kadaliao/openhongguo 的 GitHub Release 下载。
        // 上游发布 0.3.0 后删掉这一段，libs.versions.toml 里的 openhongguo 改回 io.github.beilunyang。
        exclusiveContent {
            forRepository {
                ivy("https://github.com/kadaliao/openhongguo/releases/download") {
                    name = "openhongguo-fork"
                    patternLayout { artifact("v[revision]/[module]-[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("io.github.kadaliao", "openhongguo") }
        }
    }
}

rootProject.name = "Kaixinguo"
include(":app")
