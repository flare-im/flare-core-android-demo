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
        mavenLocal()
        // kit 的远端来源（main 分支用）。顺序即优先级：mavenLocal 命中则用本地，
        // 否则回落到这里的发布版——于是单独 clone 本仓也能构建。
        // 详见 flare-im-design/docs/DISTRIBUTION-DESIGN.md。
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/flare-im/flare-im-design")
            credentials {
                username = (providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))).orNull ?: ""
                password = (providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))).orNull ?: ""
            }
        }
    }
}

rootProject.name = "flare-core-android-app"
include(":app")
include(":flare-core-android-sdk")

project(":flare-core-android-sdk").projectDir = file("../../packages/flare-core-android-sdk")
