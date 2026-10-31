plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("com.theexpanse.preview.PreviewKt")
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

tasks.named<JavaExec>("run") {
    // Os PNGs vão para docs/preview na raiz do repositório.
    args = listOf(rootProject.layout.projectDirectory.dir("docs/preview").asFile.absolutePath)
}
