@echo off
set JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-17-Full
set PATH=%JAVA_HOME%\bin;%PATH%
call mvnw.cmd test
