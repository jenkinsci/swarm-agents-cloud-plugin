@echo off
set JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-17-Full
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d %~dp0
call .mvn\apache-maven-3.9.9\bin\mvn.cmd %*
