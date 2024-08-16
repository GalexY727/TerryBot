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

## Virtual Camera

With some cool math, we can see where AprilTags would be on the field even if we are in sim!

This allows us to fine tune where the optimal camera position is for the bot so that we can see the most tags possible (for a kalman filter with main bot pose)

![gif](https://github.com/user-attachments/assets/7294c0ba-c5e7-4006-956d-8f168cdf4720)

## Object Detection

With decision-making, we are able to follow dynamic autos that can be based on if our camera can see an object or not! This is pretty stellar
*The robot only goes to the note if the camera can see it, which can be simulated with a controller in simulation by holding 'x'. 
If it cannot see an object, it will skip over the location.


![AdvantageScope_(WPILib)_avCZtabs8m](https://github.com/user-attachments/assets/c6fa4586-3975-4e27-bee3-0202e30c4e76)


Keep in mind this project requires NetworkTables to run, but if you would like to run it in simulation you can do so by launching the newly installed WPIlib VSCode (from running the .bat file) and pressing CTRL+SHIFT+P and typing "simulate robot code"