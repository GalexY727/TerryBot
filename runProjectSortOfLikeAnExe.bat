@echo off
setlocal

rem Set the directory of the script
set SCRIPT_DIR=%~dp0

rem Navigate to the project directory
cd /d "%SCRIPT_DIR%"

rem Function to check if WPILib is installed
:check_wpilib_installed
if exist "%USERPROFILE%\wpilib\2024" (
    goto :wpilib_installed
) else (
    goto :install_wpilib
)

:wpilib_installed
echo WPILib is already installed.
goto :check_gradle

:install_wpilib
echo WPILib is not installed. Installing WPILib...
powershell -Command "Invoke-WebRequest -Uri https://packages.wpilib.workers.dev/installer/v2024.3.2/Win64/WPILib_Windows-2024.3.2.iso -OutFile WPILib_Windows-2024.3.2.iso"
powershell -Command "Mount-DiskImage -ImagePath %SCRIPT_DIR%\WPILib_Windows-2024.3.2.iso"
powershell -Command "Start-Process -FilePath 'D:\Install-WPILib.ps1' -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File D:\Install-WPILib.ps1' -Wait"
powershell -Command "Dismount-DiskImage -ImagePath %SCRIPT_DIR%\WPILib_Windows-2024.3.2.iso"
del WPILib_Windows-2024.3.2.iso

:check_gradle
rem Check if Gradle is installed
gradle -v >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Gradle is not installed. Installing Gradle...
    rem Download and install Gradle
    powershell -Command "Invoke-WebRequest -Uri https://services.gradle.org/distributions/gradle-7.2-bin.zip -OutFile gradle.zip"
    powershell -Command "Expand-Archive -Path gradle.zip -DestinationPath ."
    set PATH=%SCRIPT_DIR%\gradle-7.2\bin;%PATH%
)

rem Compile the project using Gradle
call gradlew.bat build

rem Check if the build was successful
if %ERRORLEVEL% neq 0 (
    echo Build failed.
    exit /b %ERRORLEVEL%
)

rem Run the project in a new terminal window
start cmd /k "call gradlew.bat run"

endlocal