// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.ejml.simple.AutomaticSimpleMatrixConvert;

import com.ctre.phoenix6.hardware.Pigeon2;
import com.pathplanner.lib.auto.AutoBuilder;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.geometry.Twist2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Robot;
import frc.robot.RobotContainer;
import frc.robot.commands.drive.ChasePose;
import frc.robot.commands.drive.Drive;
import frc.robot.commands.drive.DriveHDC;
import frc.robot.util.Constants.AutoConstants;
import frc.robot.util.Constants.DriveConstants;
import frc.robot.util.Constants.FieldConstants;
import frc.robot.util.rev.MAXSwerveModule;
import monologue.Logged;
import monologue.Annotations.Log;

public class Swerve extends SubsystemBase implements Logged {

    public static double twistScalar = 4;
    private Rotation2d gyroRotation2d = new Rotation2d();

    private double speedMultiplier = 1;
    private final MAXSwerveModule frontLeft = new MAXSwerveModule(
            DriveConstants.FRONT_LEFT_DRIVING_CAN_ID,
            DriveConstants.FRONT_LEFT_TURNING_CAN_ID,
            DriveConstants.FRONT_LEFT_CHASSIS_ANGULAR_OFFSET);

    private final MAXSwerveModule frontRight = new MAXSwerveModule(
            DriveConstants.FRONT_RIGHT_DRIVING_CAN_ID,
            DriveConstants.FRONT_RIGHT_TURNING_CAN_ID,
            DriveConstants.FRONT_RIGHT_CHASSIS_ANGULAR_OFFSET);

    private final MAXSwerveModule rearLeft = new MAXSwerveModule(
            DriveConstants.REAR_LEFT_DRIVING_CAN_ID,
            DriveConstants.REAR_LEFT_TURNING_CAN_ID,
            DriveConstants.BACK_LEFT_CHASSIS_ANGULAR_OFFSET);

    private final MAXSwerveModule rearRight = new MAXSwerveModule(
            DriveConstants.REAR_RIGHT_DRIVING_CAN_ID,
            DriveConstants.REAR_RIGHT_TURNING_CAN_ID,
            DriveConstants.BACK_RIGHT_CHASSIS_ANGULAR_OFFSET);

    // The gyro sensor
    private final Pigeon2 gyro = new Pigeon2(DriveConstants.PIGEON_CAN_ID);

    private final MAXSwerveModule[] swerveModules = new MAXSwerveModule[] {
            frontLeft,
            frontRight,
            rearLeft,
            rearRight
    };

    private SwerveDrivePoseEstimator poseEstimator = new SwerveDrivePoseEstimator(
            DriveConstants.DRIVE_KINEMATICS,
            gyro.getRotation2d(),
            getModulePositions(),
            new Pose2d(),
            // Trust the information of the vision more
            // Nat.N1()).fill(0.1, 0.1, 0.1) --> trust more
            // Nat.N1()).fill(1.25, 1.25, 1.25) --> trust less
            // Notice that the theta on the vision is very large,
            // and the state measurement is very small.
            // This is because we assume that the IMU is very accurate.
            // You can visualize these graphs working together here:
            // https://www.desmos.com/calculator/a0kszyrwfe
            VecBuilder.fill(0.1, 0.1, 0.05),
            // State measurement
            // standard deviations
            // X, Y, theta
            VecBuilder.fill(0.8, 0.8, 2.5)
    // Vision measurement
    // standard deviations
    // X, Y, theta
    );

    /**
     * Creates a new DriveSu1stem.
     */
    public Swerve() {
        AutoBuilder.configureHolonomic(
                this::getPose,
                this::resetOdometry,
                this::getRobotRelativeVelocity,
                this::drive,
                AutoConstants.HPFC,
                Robot::isRedAlliance,
                this);

        resetEncoders();
        gyro.setYaw(0);
        setBrakeMode();
    }

    @Override
    public void periodic() {
        gyroRotation2d = gyro.getRotation2d();
        poseEstimator.updateWithTime(Robot.currentTimestamp, gyroRotation2d, getModulePositions());
        // System.out.print("angle: " + gyro.getAngle()+ ", yaw: " +
        // gyro.getYaw().getValueAsDouble());
        logPositions();
    }

    public void logPositions() {

        Pose2d currentPose = getPose();

        RobotContainer.swerveMeasuredStates = new SwerveModuleState[] {
                frontLeft.getState(), frontRight.getState(), rearLeft.getState(), rearRight.getState()
        };

        ChassisSpeeds speeds = DriveConstants.DRIVE_KINEMATICS.toChassisSpeeds(RobotContainer.swerveMeasuredStates);

        if (FieldConstants.IS_SIMULATION) {
            resetOdometry(
                    currentPose.exp(
                            new Twist2d(
                                    0, 0,
                                    speeds.omegaRadiansPerSecond * .02)));
        }

        RobotContainer.field2d.setRobotPose(currentPose);

        if ((Double.isNaN(currentPose.getX())
            || Double.isNaN(currentPose.getY())
            || Double.isNaN(currentPose.getRotation().getDegrees())))
        {
            // Something in our pose was NaN...
            resetOdometry(RobotContainer.robotPose2d);
            resetEncoders();
            resetHDC();
        } else {
            RobotContainer.robotPose2d = currentPose;
        }

        RobotContainer.robotPose3d = new Pose3d(
                new Translation3d(
                        currentPose.getX(),
                        currentPose.getY(),
                        Math.hypot(
                                Rotation2d.fromDegrees(gyro.getRoll().refresh().getValue()).getSin()
                                        * DriveConstants.ROBOT_LENGTH_METERS / 2.0,
                                Rotation2d.fromDegrees(gyro.getPitch().refresh().getValue()).getSin() *
                                        DriveConstants.ROBOT_LENGTH_METERS / 2.0)),
                new Rotation3d(0, 0, currentPose.getRotation().getRadians()));

    }

    /**
     * Returns the currently-estimated pose of the robot.
     *
     * @return The pose.
     */
    public Pose2d getPose() {
        return poseEstimator.getEstimatedPosition();
    }

    public SwerveDrivePoseEstimator getPoseEstimator() {
        return poseEstimator;
    }

    public void drive(ChassisSpeeds robotRelativeSpeeds) {
        setModuleStates(DriveConstants.DRIVE_KINEMATICS.toSwerveModuleStates(
            ChassisSpeeds.discretize(robotRelativeSpeeds, (Timer.getFPGATimestamp() - Robot.previousTimestamp)))
        );
    }

    public void drive(double xSpeed, double ySpeed, double rotSpeed, boolean fieldRelative) {
        double timeDifference = Timer.getFPGATimestamp() - Robot.previousTimestamp;
        ChassisSpeeds robotRelativeSpeeds;

        if (fieldRelative) {
            robotRelativeSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(xSpeed, ySpeed, rotSpeed, getPose().getRotation());
        } else {
            robotRelativeSpeeds = new ChassisSpeeds(xSpeed, ySpeed, rotSpeed);
        }

        ChassisSpeeds discretizedSpeeds = ChassisSpeeds.discretize(robotRelativeSpeeds, timeDifference);
        SwerveModuleState[] swerveModuleStates = DriveConstants.DRIVE_KINEMATICS.toSwerveModuleStates(discretizedSpeeds);

        setModuleStates(swerveModuleStates);
    }

    public void stopDriving() {
        drive(0, 0, 0, false);
    }
    
    @Log
    Pose2d desiredHDCPose = new Pose2d();
    public void setDesiredPose(Pose2d pose) {
        desiredHDCPose = pose;
    }

    public ChassisSpeeds getRobotRelativeVelocity() {
        return DriveConstants.DRIVE_KINEMATICS.toChassisSpeeds(getModuleStates());
    }

    /**
     * Sets the wheels into an X formation to prevent movement.
     */
    public void setWheelsX() {
        SwerveModuleState[] desiredStates = new SwerveModuleState[] {
            new SwerveModuleState(0, Rotation2d.fromDegrees(45)),
            new SwerveModuleState(0, Rotation2d.fromDegrees(-45)),
            new SwerveModuleState(0, Rotation2d.fromDegrees(-45)),
            new SwerveModuleState(0, Rotation2d.fromDegrees(45))
        };

        setModuleStates(desiredStates);
    }

    public Command getSetWheelsX() {
        return run(this::setWheelsX);
    }

    /**
     * Sets the swerve ModuleStates.
     *
     * @param desiredStates The desired SwerveModule states.
     */
    public void setModuleStates(SwerveModuleState[] desiredStates) {
        SwerveDriveKinematics.desaturateWheelSpeeds(
            desiredStates, 
            NetworkTableInstance.getDefault().getTable("Robot").getEntry("MAX")
                .getDouble(DriveConstants.MAX_SPEED_METERS_PER_SECOND)
        );
        frontLeft.setDesiredState(desiredStates[0]);
        frontRight.setDesiredState(desiredStates[1]);
        rearLeft.setDesiredState(desiredStates[2]);
        rearRight.setDesiredState(desiredStates[3]);

        RobotContainer.swerveDesiredStates = desiredStates;
    }

    public void resetOdometry(Pose2d pose) {

        if (Double.isNaN(pose.getX()) || Double.isNaN(pose.getY()) || Double.isNaN(pose.getRotation().getRadians())) {
            return;
        }

        poseEstimator.resetPosition(
                gyroRotation2d,
                getModulePositions(),
                pose);
    }

    public Command resetOdometryCommand(Supplier<Pose2d> pose) {
        return runOnce(() -> resetOdometry(pose.get()));
    }

    public SwerveModuleState[] getModuleStates() {

        SwerveModuleState[] states = new SwerveModuleState[4];

        for (int modNum = 0; modNum < swerveModules.length; modNum++) {
            states[modNum] = swerveModules[modNum].getState();
        }
        return states;

    }

    /**
     * Returns an array of SwerveModulePosition objects representing the positions of all swerve modules.
     * This is the position of the driving encoder and the turning encoder
     *
     * @return an array of SwerveModulePosition objects representing the positions of all swerve modules
     */
    public SwerveModulePosition[] getModulePositions() {

        SwerveModulePosition[] positions = new SwerveModulePosition[4];

        for (int modNum = 0; modNum < swerveModules.length; modNum++) {
            positions[modNum] = swerveModules[modNum].getPosition();
        }
        return positions;

    }
    
    public void resetEncoders() {
        for (MAXSwerveModule mSwerveMod : swerveModules) {
            mSwerveMod.resetEncoders();
        }
    }

    public Command toggleSpeed() {
        return runOnce(() -> this.speedMultiplier = (this.speedMultiplier == 1) ? 0.35 : 1);
    }

    public void setSpeedMultiplier(double speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    public Command setAlignmentSpeed() {
        return runOnce(() -> {
            DriveConstants.MAX_SPEED_METERS_PER_SECOND = FieldConstants.ALIGNMENT_SPEED;
        });
    }

    public double getSpeedMultiplier() {
        return this.speedMultiplier;
    }

    /**
     * Sets the brake mode for the drive motors.
     * This is useful for when the robot is enabled
     * So we can stop the robot quickly
     * (This is the default mode)
     */
    public void setBrakeMode() {
        for (MAXSwerveModule mSwerveMod : swerveModules) {
            mSwerveMod.setBrakeMode();
        }
    }

    /**
     * Sets the coast mode for the drive motors.
     * This is useful for when the robot is disabled
     * So we can freely move the robot around
     */
    public void setCoastMode() {
        for (MAXSwerveModule mSwerveMod : swerveModules) {
            mSwerveMod.setCoastMode();
        }
    }

    public Command getDriveCommand(Supplier<ChassisSpeeds> speeds, BooleanSupplier fieldRelative) {
        return new Drive(this, speeds, fieldRelative, () -> false);
    }
    
    public DriveHDC getDriveHDCCommand(Supplier<ChassisSpeeds> speeds, BooleanSupplier fieldRelative) {
        return new DriveHDC(this, speeds, fieldRelative, () -> false);
    }

    public Command updateChasePose(Supplier<Pose2d> poseSupplier) {
        return Commands.runOnce(() -> ChasePose.updateDesiredPose(poseSupplier.get()));
    }

    public Command getChaseCommand() {
        return new ChasePose(this);
    }

    public void resetHDC() {
        AutoConstants.HDC.getThetaController().reset(getPose().getRotation().getRadians());
    }

    public Command resetHDCCommand() {
        return Commands.runOnce(() -> resetHDC());
    }

    public void reconfigureAutoBuilder() {
        AutoBuilder.configureHolonomic(
                this::getPose,
                this::resetOdometry,
                this::getRobotRelativeVelocity,
                this::drive,
                AutoConstants.HPFC,
                Robot::isRedAlliance,
                this);
    }

    // Used for note pickup in auto
    // Because of the fact that we do not have to be perfectly 
    // on top of a note to intake it, the tolerance is fairly lenient
    public boolean atPose(Pose2d position) {
        // More lenient on x axis, less lenient on y axis and rotation
        Pose2d currentPose = getPose();
        double angleDiff = currentPose.getRotation().minus(position.getRotation()).getRadians();
		double distance = currentPose.relativeTo(position).getTranslation().getNorm();
        return 
            MathUtil.isNear(0, distance, AutoConstants.AUTO_POSITION_TOLERANCE_METERS)
            && MathUtil.isNear(0, angleDiff, AutoConstants.AUTO_POSITION_TOLERANCE_RADIANS);
    }

    public boolean atHDCPose() {
        return atPose(desiredHDCPose);
    }

}