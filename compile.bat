@echo off
echo Starting Maven compilation...
cd /d D:\project\gaussdb-ticket-system\backend
call mvn clean package -DskipTests
echo Compilation complete!
pause