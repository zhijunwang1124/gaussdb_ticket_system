@echo off
echo ========================================
echo  Clean and Rebuild Project
echo ========================================
echo.

cd /d D:\project\gaussdb-ticket-system\backend

echo [1/4] Stopping Java processes...
taskkill /F /IM java.exe 2>nul
timeout /t 3

echo [2/4] Cleaning old compiled files...
call mvn clean
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Clean failed!
    pause
    exit /b 1
)

echo [3/4] Compiling project...
call mvn compile
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compile failed!
    pause
    exit /b 1
)

echo [4/4] Packaging project...
call mvn package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Package failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Build completed successfully!
echo ========================================
echo.
echo You can now start the application with:
echo   mvn spring-boot:run
echo.

pause