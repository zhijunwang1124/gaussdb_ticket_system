@echo off
echo Restarting GaussDB Ticket System Application...
echo Stopping existing processes...
taskkill /F /IM java.exe 2>nul
timeout /t 2

echo Starting application...
cd /d D:\project\gaussdb-ticket-system\backend
call mvn spring-boot:run

pause