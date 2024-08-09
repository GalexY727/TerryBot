package frc.robot.commands.subsytemHelpers;

import java.util.function.Supplier;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.robot.subsystems.Indexer;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.elevator.Trapper;
import frc.robot.subsystems.elevator.Elevator;
import frc.robot.util.constants.SpeedAngleTriplet;
import frc.robot.util.constants.Constants.TrapConstants;

public class PieceControl {

    private Intake intake;
    private Indexer indexer;

    private Elevator elevator;
    private Trapper trapper;

    private ShooterCmds shooterCmds;

    private boolean shooterMode = true;

    public PieceControl(
            Intake intake,
            Indexer indexer,
            Elevator elevator,
            Trapper trapper,
            ShooterCmds shooterCmds) {
        this.intake = intake;
        this.indexer = indexer;
        this.elevator = elevator;
        this.trapper = trapper;
        this.shooterCmds = shooterCmds;
    }

    public Command stopAllMotors() {
        return Commands.parallel(
                stopIntakeAndIndexer(),
                shooterCmds.stopShooter()).ignoringDisable(true);
    }

    // TODO: only run angle reset when we are not using prepareSWDCommand
    public Command shootWhenReady(Supplier<Pose2d> poseSupplier, Supplier<ChassisSpeeds> speedSupplier) {
        return Commands.waitUntil(shooterCmds.shooterCalc.readyToShootSupplier())
                .andThen(noteToShoot(poseSupplier, speedSupplier));
    }

    // TODO: Possibly split this into two commands where one sends to shooter
    // without waiting
    public Command noteToShoot(Supplier<Pose2d> poseSupplier, Supplier<ChassisSpeeds> speedSupplier) {
        // this should be ran while we are aiming with pivot and shooter already
        // start running indexer so it gets up to speed and wait until shooter is at desired 
        // rotation and speed before sending note from trapper into indexer and then into 
        // shooter before stopping trapper and indexer
        return Commands.sequence(
                intake.inCommand(),
                trapper.intake(),
                indexer.toShooter(),
                Commands.waitSeconds(NT.getSupplier("noteToShoot1").getAsDouble()), // 0.7
                shooterCmds.getNoteTrajectoryCommand(poseSupplier, speedSupplier),
                Commands.waitSeconds(NT.getSupplier("noteToShoot2").getAsDouble()), // 0.4
                stopIntakeAndIndexer());
    }

    public Command intakeToTrap() {
        // this should be ran while we are aiming with pivot and shooter already
        // start running indexer so it gets up to speed and wait until shooter is at desired 
        // rotation and speed before sending note from trapper into indexer and then into 
        // shooter before stopping trapper and indexer
        return Commands.sequence(
            intake.inCommand(),
            trapper.intake(),
            indexer.toShooterSlow(),
            Commands.waitUntil(intake::getPossession),
            Commands.waitSeconds(NT.getSupplier("intakeToTrap1").getAsDouble()), // 0.5
            noteToTrap(),
            noteToIndexer()
        );
    }

    public Command noteToIndexer() {
        return Commands.sequence(
            trapper.intake(),
            indexer.toShooterSlow(),
            Commands.waitSeconds(NT.getSupplier("noteToIndexer1").getAsDouble()), // 0.6
            indexer.stopCommand(),
            indexer.toElevatorSlow(),
            Commands.waitSeconds(NT.getSupplier("noteToIndexer2").getAsDouble()), // 0.07
            stopIntakeAndIndexer()
        );
    }

    public Command noteToTrap() {
        return Commands.sequence(
            trapper.outtake(),
            indexer.toElevator(),
            Commands.waitSeconds(NT.getSupplier("noteToTrap1").getAsDouble()), // 0.2
            stopIntakeAndIndexer(),
            trapper.outtakeSlow(),
            Commands.waitSeconds(NT.getSupplier("noteToTrap2").getAsDouble()), // 0.5
            stopIntakeAndIndexer()
        );
    }

    public Command ejectNote() {
        // this should be ran while we are aiming with pivot and shooter already
        // start running indexer so it gets up to speed and wait until shooter is at desired 
        // rotation and speed before sending note from trapper into indexer and then into 
        // shooter before stopping trapper and indexer
        return Commands.sequence(
            intake.outCommand(),
            indexer.toElevator(),
            dropPieceCommand(),
            stopAllMotors()
        );
    }

    public Command stopEjecting() {
        return Commands.parallel(
            elevator.toBottomCommand(),
            stopAllMotors()
        );
    }

    public Command dropPieceCommand() {
        return Commands.sequence(
            elevator.toDropCommand(),
            trapper.outtake(),
            Commands.waitSeconds(NT.getSupplier("dropPieceCommand1").getAsDouble()), // 0.5
            elevator.toBottomCommand()
        );
    }

    public Command indexCommand() {
        return elevator.toIndexCommand()
                .alongWith(intake.stopCommand(), trapper.stopCommand());
    }

    public Command intakeAuto() {
        return Commands.sequence(
                intake.inCommand(),
                trapper.intake(),
                indexer.stopCommand());
    }

    public Command noteToTarget(Supplier<Pose2d> poseSupplier, Supplier<ChassisSpeeds> speedSupplier) {
        // maybe make setPosition a command ORR Make the Elevator Command
        return Commands.either(
            shootWhenReady(poseSupplier, speedSupplier),
            placeTrap(),
            this::getShooterMode
        );
    }

    public Command placeTrap() {
        return shooterCmds.setTripletCommand(SpeedAngleTriplet.of(0.0,0.0, 60.0)).alongWith(
            Commands.sequence(
                trapper.intake(),
                Commands.waitSeconds(0.3),
                trapper.stopCommand(),
                Commands.waitSeconds(1),
                trapper.outtake(),
                Commands.waitSeconds(TrapConstants.OUTTAKE_SECONDS),
                trapper.stopCommand()
            )
        ).andThen(
            shooterCmds.setTripletCommand(SpeedAngleTriplet.of(0.0,0.0, 0.0))
        );
    }

    public Command sourceShooterIntake() {
        return Commands.sequence(
            shooterCmds.setTripletCommand(new SpeedAngleTriplet(-300.0, -300.0, 45.0)),
            indexer.toElevator(),
            trapper.outtake(),
            Commands.waitSeconds(3),
            indexCommand()
        ); 
    }

    public Command intakeToTrapper() { 
        return intake.inCommand()
                .alongWith(indexer.toElevator());
    }

    public Command stopIntakeAndIndexer() {
        return intake.stopCommand()
                .alongWith(indexer.stopCommand())
                .alongWith(trapper.stopCommand());
    }

    public boolean getShooterMode() {
        return shooterMode;
    }

    public void setShooterMode(boolean shooterMode) {
        this.shooterMode = shooterMode;
    }

    public Command setShooterModeCommand(boolean shooterMode) {
        return Commands.runOnce(() -> setShooterMode(shooterMode));
    }
}
