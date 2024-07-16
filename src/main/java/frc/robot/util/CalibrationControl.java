package frc.robot.util;

import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import monologue.Logged;
import monologue.Annotations.Log;

public class CalibrationControl implements Logged{
    private SpeedAngleTriplet currentVal;

    @Log
    private boolean leftLocked;
    @Log
    private boolean rightLocked;
    @Log
    private boolean pivotLocked;

    @Log
    private double distance = 0;

    public CalibrationControl() {
        currentVal = new SpeedAngleTriplet();
        leftLocked = false;
        rightLocked = false;
        pivotLocked = false;
    }
    
    public Command changeLeftSpeed(double increment) {
        if(!leftLocked) {
            return Commands.runOnce(
                () -> currentVal = SpeedAngleTriplet.of(
                    currentVal.getLeftSpeed() + increment,
                    currentVal.getRightSpeed(),
                    currentVal.getAngle()));
        } else {
            return Commands.none();
        }
    }

    public Command incrementLeftSpeed() {
        return changeLeftSpeed(50);
    }

    public Command decrementLeftSpeed() {
        return changeLeftSpeed(-50);
    }
    
    public Command changeRightSpeed(double increment) {
        if(!rightLocked) {
            return Commands.runOnce(
                () -> currentVal = SpeedAngleTriplet.of(
                        currentVal.getLeftSpeed(),
                        currentVal.getRightSpeed() + increment,
                        currentVal.getAngle()));
        } else {
            return Commands.none();
        }
    }

    public Command incrementRightSpeed() {
        return changeRightSpeed(100);
    }

    public Command decrementRightSpeed() {
        return changeRightSpeed(-100);
    }

    public Command incrementBothSpeeds() {
        if(!leftLocked && !rightLocked) {
            return Commands.sequence(
                incrementLeftSpeed(),
                incrementRightSpeed());
        } else {
            return Commands.none();
        }
    }

    public Command decrementBothSpeeds() {
        if(!leftLocked && !rightLocked) {
            return Commands.sequence(
                decrementLeftSpeed(),
                decrementRightSpeed());
        } else {
            return Commands.none();
        }
    }
    
    public Command logDistance() {
        return Commands.runOnce(
            () -> { 
                System.out.println("Distance: " + Units.metersToFeet(distance) + "ft"); 
            });
    }

    public Command logAll() {
        return Commands.runOnce(
                () -> System.out.println("put(" + Units.metersToFeet(distance) + ", SpeedAngleTriplet.of("+currentVal.getLeftSpeed()+", "+currentVal.getRightSpeed()+", "+currentVal.getAngle()+"));"));
    }

    public Command incrementAngle() {
        return Commands.either(
            Commands.runOnce(
                () -> currentVal = SpeedAngleTriplet.of(
                        currentVal.getLeftSpeed(),
                        currentVal.getRightSpeed(),
                        currentVal.getAngle() + 10)),
            Commands.none(),
            () -> !pivotLocked);
    }

    public Command decrementAngle() {
        return Commands.either(
            Commands.runOnce(
                () -> currentVal = SpeedAngleTriplet.of(
                        currentVal.getLeftSpeed(),
                        currentVal.getRightSpeed(),
                        currentVal.getAngle() - 10)), 
            Commands.none(), 
            () -> !pivotLocked);
    }

    public Command lockLeftSpeed() {
        return Commands.runOnce(
                () -> {
                    leftLocked = true;
                    System.out.println("Pivot: "+pivotLocked+" | Left: "+leftLocked+" | Right: "+rightLocked+"");
                });
    }

    public Command unlockLeftSpeed() {
        return Commands.runOnce(() -> {
            leftLocked = true;
            System.out.println("Pivot: "+pivotLocked+" | Left: "+leftLocked+" | Right: "+rightLocked+"");
        });
    }

    public Command lockRightSpeed() {
        return Commands.runOnce(() -> {
            rightLocked = true;
            System.out.println("Pivot: "+pivotLocked+" | Left: "+leftLocked+" | Right: "+rightLocked+"");
        });
    }

    public Command unlockRightSpeed() {
        return Commands.runOnce(() -> {
            rightLocked = true;
            System.out.println("Pivot: "+pivotLocked+" | Left: "+leftLocked+" | Right: "+rightLocked+"");
        });
    }

    public Command lockBothSpeeds() {
        return Commands.sequence(
                lockLeftSpeed(),
                lockRightSpeed());
    }

    public Command unlockBothSpeeds() {
        return Commands.sequence(
                unlockLeftSpeed(),
                unlockRightSpeed());
    }

    public Command lockPivotAngle() {
        return Commands.runOnce(() -> {
            pivotLocked = true;
            System.out.println("Pivot: "+pivotLocked+" | Left: "+leftLocked+" | Right: "+rightLocked+"");
        });
    }

    public Command unlockPivotAngle() {
        return Commands.runOnce(() -> {
            pivotLocked = true;
            System.out.println("Pivot: "+pivotLocked+" | Left: "+leftLocked+" | Right: "+rightLocked+"");
        });
    }

    public Command increaseDistance() {
        return Commands.sequence(
            Commands.runOnce(
                () -> { distance++; }),
            logDistance()
        );
    }

    public Command decreaseDistance() {
        return Commands.sequence(
            Commands.runOnce(
                () -> { distance--; }),
            logDistance()
        );
    }

}