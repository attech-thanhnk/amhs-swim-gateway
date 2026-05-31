@echo off
setlocal
echo ======================================================
echo    ASG SWIM GATEWAY - AUTOMATED BUILD AND RELEASE
echo ======================================================

:: 1. Build Docker Image (Compiles JAR inside)
echo [*] Step 1: Building Docker image and compiling source code...
docker-compose build gateway-swim

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed! Please check your source code for errors.
    pause
    exit /b %ERRORLEVEL%
)

:: 2. Extract Artifact
echo [*] Step 2: Extracting pre-compiled JAR artifact...

if not exist "asg-swim-jar-deployment" (
    echo [!] Creating missing deployment directory...
    mkdir "asg-swim-jar-deployment"
)

docker create --name tmp-extract amhs-swim-gateway-gateway-swim:latest
docker cp tmp-extract:/app/app.jar asg-swim-jar-deployment/app.jar
docker rm tmp-extract

echo [*] Copying database setup scripts...
if exist "database" (
    xcopy /S /I /Y "database" "asg-swim-jar-deployment\database"
)

:: 3. Verification
echo [*] Step 3: Verifying deployment bundle...
if exist "asg-swim-jar-deployment\app.jar" (
    echo ======================================================
    echo    SUCCESS! Deployment bundle is ready in:
    echo    asg-swim-jar-deployment
    echo ======================================================
) else (
    echo [ERROR] JAR extraction failed!
)

pause
endlocal
