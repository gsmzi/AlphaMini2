@echo off
cd /d "%~dp0"
if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
)
call "%~dp0gradlew.bat" :sdkdemo:assembleDebug
