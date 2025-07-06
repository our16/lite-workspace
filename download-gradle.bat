@echo off
setlocal

REM ✅ 配置版本号
set GRADLE_VERSION=8.11.1
set GRADLE_DIST=gradle-%GRADLE_VERSION%-bin.zip

REM ✅ 配置缓存路径（Gradle 默认缓存）
set GRADLE_CACHE=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin

REM ✅ 阿里云镜像地址
set ALIYUN_URL=https://mirrors.aliyun.com/gradle/%GRADLE_VERSION%/%GRADLE_DIST%

echo Downloading %GRADLE_DIST% from Aliyun...
powershell -Command "Invoke-WebRequest -Uri %ALIYUN_URL% -OutFile %GRADLE_DIST%"

REM ✅ 创建缓存结构
echo Creating Gradle cache structure...
for /f %%i in ('certutil -hashfile %GRADLE_DIST% SHA256 ^| find /i /v "SHA256" ^| find /i /v "CertUtil"') do set HASH=%%i
set CACHE_DIR=%GRADLE_CACHE%\%HASH%

mkdir "%CACHE_DIR%"
echo Extracting zip to %CACHE_DIR% ...
powershell -Command "Expand-Archive -Path %GRADLE_DIST% -DestinationPath '%CACHE_DIR%'"

del %GRADLE_DIST%
echo ✅ Done. Gradle %GRADLE_VERSION% cached at:
echo %CACHE_DIR%

endlocal
pause
