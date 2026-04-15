@echo off
echo ========================================
echo  GaussDB Ticket System - Build & Restart
echo ========================================
echo.

echo [Step 1] Stopping existing Java processes...
taskkill /F /IM java.exe 2>nul
timeout /t 2

echo [Step 2] Compiling project...
cd /d D:\project\gaussdb-ticket-system\backend
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)

echo [Step 3] Starting application...
echo.
echo ========================================
echo  Application is starting...
echo  Please wait for the startup message
echo ========================================
echo.

call mvn spring-boot:run

pause