#!/bin/sh

# Set the directory of the script
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Navigate to the project directory
cd "$SCRIPT_DIR"

# Function to check if WPILib is installed
check_wpilib_installed() {
    if [ -d "$HOME/wpilib/2024" ]; then
        return 0
    else
        return 1
    fi
}

# Function to install WPILib
install_wpilib() {
    echo "WPILib is not installed. Installing WPILib..."
    wget https://packages.wpilib.workers.dev/installer/v2024.3.2/Win64/WPILib_Windows-2024.3.2.iso -O WPILib_Windows-2024.3.2.iso
    mkdir -p /mnt/wpilib
    sudo mount -o loop WPILib_Windows-2024.3.2.iso /mnt/wpilib
    sudo /mnt/wpilib/Install-WPILib.sh
    sudo umount /mnt/wpilib
    rm WPILib_Windows-2024.3.2.iso
}

# Check if WPILib is installed
if ! check_wpilib_installed; then
    install_wpilib
fi

# Check if Gradle is installed
if ! command -v gradle &> /dev/null
then
    echo "Gradle is not installed. Installing Gradle..."
    # Download and install Gradle
    wget https://services.gradle.org/distributions/gradle-7.2-bin.zip -O gradle.zip
    unzip gradle.zip
    export PATH="$SCRIPT_DIR/gradle-7.2/bin:$PATH"
fi

# Compile the project using Gradle
./gradlew build

# Check if the build was successful
if [ $? -ne 0 ]; then
    echo "Build failed."
    exit 1
fi

# Run the project in a new terminal window
gnome-terminal -- ./gradlew run