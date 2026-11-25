object AppBuildConfig {
    object Android {
        const val compileSdk = 37
        const val minSdk = 29
        const val targetSdk = 37
    }

    object Java {
        const val toolchainVersion = 25
        const val version = 17
        const val kotlinJvmTarget = "17"
    }

    object Application {
        const val packageName = "elovaire.music.droidbeauty.app"
        const val applicationId = "com.vimitt.verso"
        const val versionCode = 261001193
        const val versionName = "Cavaquinho 1.1"
        const val macrobenchmarkNamespace = "elovaire.music.droidbeauty.app.macrobenchmark"
    }

    object Testing {
        const val instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
