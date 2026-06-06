@echo off
setlocal

set "JDK17_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
if not exist "%JDK17_HOME%\bin\java.exe" (
  echo [ERROR] JDK 17 not found at "%JDK17_HOME%".
  echo [ERROR] Update assessment-backend\mvn17.cmd to point to a valid JDK 17 installation.
  exit /b 1
)

set "JAVA_HOME=%JDK17_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

call mvn.cmd %*
exit /b %ERRORLEVEL%
