@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF)
@REM Maven Wrapper startup script for Windows
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
set MAVEN_OPTS=-Xmx1024m

@REM Find java.exe - prefer PATH over JAVA_HOME
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

@REM Fallback to JAVA_HOME if java not in PATH
if defined JAVA_HOME (
    set JAVA_HOME=%JAVA_HOME:"=%
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
    if exist "%JAVA_EXE%" goto execute
)

echo ERROR: No 'java' command could be found in PATH or JAVA_HOME.
goto error

:execute
set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

if exist %WRAPPER_JAR% goto runWrapper

echo Downloading Maven Wrapper...
powershell -Command "(New-Object Net.WebClient).DownloadFile('%WRAPPER_URL:"=%', '%WRAPPER_JAR:"=%')"
if %ERRORLEVEL% neq 0 goto error

:runWrapper
"%JAVA_EXE%" %MAVEN_OPTS% -jar %WRAPPER_JAR% %*
if %ERRORLEVEL% neq 0 goto error
goto end

:error
set ERROR_CODE=1

:end
endlocal
exit /B %ERROR_CODE%
