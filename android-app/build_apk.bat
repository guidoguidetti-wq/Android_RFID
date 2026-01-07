@echo off
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d C:\_RFID\Android_RFID\android-app
call "%~dp0gradlew.bat" assembleDebug --stacktrace
