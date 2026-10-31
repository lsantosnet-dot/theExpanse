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
    }
}

rootProject.name = "theExpanse"

// :core é Kotlin/JVM puro: física, simulação e a lista de desenho (sem Android, sem AWT).
include(":core")
// :preview rasteriza a lista de desenho do :core em PNG (JVM), para ver as telas sem aparelho.
include(":preview")

// :app (Android) só entra quando há Android SDK (ANDROID_HOME / ANDROID_SDK_ROOT ou sdk.dir em local.properties).
val localProps = file("local.properties")
val hasSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null ||
    (localProps.exists() && localProps.readText().contains("sdk.dir"))
if (hasSdk) include(":app") else logger.lifecycle("Android SDK não encontrado: módulo :app ignorado.")
