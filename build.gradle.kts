plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "org.liteworkspace"
version = "1.24.28"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

intellij {
    // 固定 IDE 版本，避免每次构建都去查询最新版
    version.set("2024.1.7")
    type.set("IC") // IntelliJ Community Edition
    plugins.set(listOf("com.intellij.java", "JUnit"))

    // 关闭自动 update 检查，避免 GitHub NPE
    updateSinceUntilBuild.set(false)
}

tasks {
    // Java 编译配置
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
    }

    // Kotlin 编译配置
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("241")    // 对应 2024.1.* 版本
        untilBuild.set("252.*")  // 允许兼容未来小版本
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
