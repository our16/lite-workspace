@echo off
setlocal

:: 设置 Gradle 缓存目录（可修改）
set GRADLE_HOME=%USERPROFILE%\.gradle

:: Gradle wrapper 版本
set GRADLE_VERSION=8.11.1
set GRADLE_ZIP_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip

:: IntelliJ Plugin Gradle 插件版本（与你项目中保持一致）
set INTELLIJ_PLUGIN_VERSION=1.17.4

:: 下载 Gradle ZIP 包
echo Downloading Gradle %GRADLE_VERSION%...
curl -o gradle-%GRADLE_VERSION%-bin.zip %GRADLE_ZIP_URL%

:: 解压到 Gradle wrapper 缓存目录
mkdir "%GRADLE_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin"
powershell -Command "Expand-Archive -Path gradle-%GRADLE_VERSION%-bin.zip -DestinationPath '%GRADLE_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin'"

:: 用 init 临时工程拉取 IntelliJ 插件依赖
mkdir .tmp-init
cd .tmp-init
echo plugins { id("org.jetbrains.intellij") version "%INTELLIJ_PLUGIN_VERSION%" } > build.gradle.kts
echo {} > settings.gradle.kts

:: 执行依赖解析
gradle build -x test || gradlew build -x test

cd ..
rd /s /q .tmp-init
del gradle-%GRADLE_VERSION%-bin.zip

echo Done! IntelliJ Gradle 插件开发依赖已缓存到 %GRADLE_HOME%

endlocal
pause
