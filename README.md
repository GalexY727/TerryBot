# TerryBot!

This repository contains the code for a basic robot that is capable of performing a variety of tasks. The robot is designed using Java and the WPILib library, and it is built using Gradle.

## Features

- **Shooting**: The robot is equipped with a shooting mechanism, implemented in the [`Shooter`](src/main/java/frc/robot/subsystems/shooter/Shooter.java) class. The shooting calculations are handled by the [`ShooterCalc`](src/main/java/frc/robot/commands/ShooterCalc.java) class.

- **Climbing**: The robot has a climbing feature, implemented in the [`Climb`](src/main/java/frc/robot/subsystems/Climb.java) class.

- **Elevator**: The robot uses an elevator mechanism powered by Neos. The details of this feature are not currently available in the workspace.

- **Intake**: The robot has an Intake subsystem, implemented in the [`Intake`](src/main/java/frc/robot/subsystems/Intake.java) class.

- **Indexer**: The robot has an Indexer subsystem, implemented in the [`Indexer`](src/main/java/frc/robot/subsystems/Indexer.java) class.

- **Limelight**: The robot uses a Limelight for vision processing. The details of this feature are implemented in the [`Limelight`](src/main/java/frc/robot/subsystems/Limelight.java) class.

## Autonomous Path Splicing
 
Auto paths are broken into components to be followed bit by bit, which means that they can later be created dynamically as a modular system 

![Autonomous Path GIF](https://github.com/GalexY727/TerryBot/assets/65139378/30872008-d3be-437e-8d91-b80819e0e7c0)

## Shooting while driving
 
Just because we are on the move doesn't mean we have to miss!

![SWD GIF](https://github.com/user-attachments/assets/ca2406d5-9c1a-4eb1-b54f-1b7f455f1281)

## Auto Alignment

Using the position of the bot, we can auto-align to the various chains on the field to get keep the bot level when we lift ourselves!

![Chain 1 gif](https://github.com/user-attachments/assets/6f61cfd5-f032-4379-8727-e0f9a5e9ac1f) ![Chain 2 gif](https://github.com/user-attachments/assets/052382e0-1697-45a2-8c82-6084b5204ba0)

