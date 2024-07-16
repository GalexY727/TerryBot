package frc.robot.commands;

import java.util.function.BooleanSupplier;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.subsystems.Indexer;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.elevator.Claw;
import frc.robot.subsystems.elevator.Elevator;
import frc.robot.util.Constants.ShooterConstants;
import frc.robot.util.Constants.TrapConstants;

public class PieceControl {

    private Intake intake;
    private Indexer indexer;

    private Elevator elevator;
    private Claw claw;

    private ShooterCalc shooterCalc;

    public PieceControl(
            Intake intake,
            Indexer indexer,
            Elevator elevator,
            Claw claw,
            ShooterCalc shooterCalc) {
        this.intake = intake;
        this.indexer = indexer;
        this.elevator = elevator;
        this.claw = claw;
        this.shooterCalc = shooterCalc;
    }

    public Trigger readyToShoot() {
        return new Trigger(() -> shooterCalc.pivotAtDesiredAngle() && shooterCalc.shooterAtDesiredRPM());
    }

    public Command stopAllMotors() {
        return Commands.parallel(
                intake.stop(),
                indexer.stop(),
                elevator.stop(),
                claw.stop());
    }

    // TODO: Possibly split this into two commands where one sends to shooter
    // without waiting
    public Command noteToShoot() {
        // this should be ran while we are aiming with pivot and shooter already
        // start running indexer so it gets up to speed and wait until shooter is at desired 
        // rotation and speed before sending note from claw into indexer and then into 
        // shooter before stopping claw and indexer
        return Commands.sequence(
                intake.inCommand(),
                claw.intake(),
                indexer.toShooter(),
                Commands.waitSeconds(2),
                stopAllMotors());

    }

    public Command ejectNote() {
        // this should be ran while we are aiming with pivot and shooter already
        // start running indexer so it gets up to speed and wait until shooter is at desired 
        // rotation and speed before sending note from claw into indexer and then into 
        // shooter before stopping claw and indexer
        return Commands.sequence(
                intake.outCommand(),
                claw.outtake(),
                indexer.toElevator(),
                Commands.waitSeconds(2),
                stopAllMotors());

    }

    public Command noteToTarget(BooleanSupplier toAmp) {
        // maybe make setPosition a command ORR Make the Elevator Command
        return Commands.either(
                        elevator.setPositionCommand(TrapConstants.AMP_PLACE_POS),
                        elevator.setPositionCommand(TrapConstants.TRAP_PLACE_POS),
                        toAmp)
                .andThen(
                        Commands.waitUntil(elevator.isAtTargetPosition()))
                .andThen(claw.placeCommand())
                .andThen(Commands.waitSeconds(TrapConstants.OUTTAKE_SECONDS))
                .andThen(elevator.toBottomCommand());
    }

    public Command intakeToClaw() {
        return intake.inCommand()
                .alongWith(indexer.toElevator());
    }

    public Command stopIntakeAndIndexer() {
        return intake.stop()
                .alongWith(indexer.stop());
    }

}
