@echo off
echo ========================================
echo  Start GaussDB Ticket System
echo ========================================
echo.

echo [1/3] Checking Java...
java -version
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java not found!
    pause
    exit /b 1
)

echo [2/3] Starting application...
cd /d D:\project\gaussdb-ticket-system\backend

echo.
echo ========================================
echo  Application Starting...
echo  Please wait for startup message
echo ========================================
echo.

call mvn spring-boot:run

pause