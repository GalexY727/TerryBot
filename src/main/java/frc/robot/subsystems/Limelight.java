package frc.robot.subsystems;

import java.util.Optional;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.util.Constants;
import monologue.Logged;
import monologue.Annotations.Log;

// https://github.com/NAHSRobotics-Team5667/2020-FRC/blob/master/src/main/java/frc/robot/utils/LimeLight.java
public class Limelight extends SubsystemBase implements Logged{

    private NetworkTable table;
    private NetworkTableEntry botPose;

    private static NetworkTableEntry timingTestEntry;
    private static boolean timingTestEntryValue = false;

    @Log
    public boolean isConnected = false;

    @Log
    public long timeDifference = 999_999; // Micro Seconds = 0.999999 Seconds | So the limelight is not connected if the time difference is greater than LimelightConstants.LIMELIGHT_MAX_UPDATE_TIME

    public Limelight() {
        table = NetworkTableInstance.getDefault().getTable("limelight");

        // Uses network tables to check status of limelight
        timingTestEntry = table.getEntry("TIMING_TEST_ENTRY");
        
        botPose = table.getEntry("botpose");
    }

    /**
     * @return ID of the primary in-view AprilTag
     */
    public double[] getTagID() {
        return table.getEntry("tid").getDoubleArray(new double[6]);
    }

    public Pose3d arrayToPose3d(double[] entry) {
        return new Pose3d(
                entry[0],
                entry[1],
                entry[2],
                new Rotation3d(
                        Units.degreesToRadians(entry[3]),
                        Units.degreesToRadians(entry[4]),
                        Units.degreesToRadians(entry[5])));
    }

    /**
     * Robot transform in field-space.
     * Translation (X,Y,Z)
     * Rotation(Roll,Pitch,Yaw)
     * 
     * @return the robot pose
     */
    public Optional<Pose3d> getPose3d() {
        double[] botPoseArray = getRawPose();
        if (hasTarget(botPoseArray)) {
            return Optional.empty();
        } else {
            return Optional.of(arrayToPose3d(botPoseArray));
        }
    }

    public double[] getRawPose() {
        return botPose.getDoubleArray(new double[8]);
    }

    /**
     * Robot transform in field-space (blue driverstation WPILIB origin).
     * Translation (X,Y,Z)
     * Rotation(Roll,Pitch,Yaw),
     * total latency (cl+tl)
     * 
     * @return the robot pose with the blue driverstation WPILIB origin
     */
    public Optional<Pose2d> getPose2d() {
        double[] botPoseArray = botPose.getDoubleArray(new double[8]);
        if (hasTarget(botPoseArray)) {
            return Optional.empty();
        } else {
            return Optional.of(
                    new Pose2d(new Translation2d(botPoseArray[0], botPoseArray[1]), new Rotation2d(botPoseArray[5])));
        }
    }

    /**
     * Limelight returns an array of {0,0,0,0,0,0,0,0} if it doesn't see a target
     * so we can check if we have a target in sight using this method
     * 
     * @param botPoseArray the output of the camera
     * @return if there is a target or not
     */
    boolean hasTarget(double[] botPoseArray) {
        boolean allZeros = true;
        for (double val : botPoseArray) {
            if (val != 0) {
                allZeros = false;
                break;
            }
        }
        return allZeros;
    }

    /**
     * 3D transform of the camera in the coordinate
     * system of the primary in-view AprilTag (array (6))
     * 
     * or
     * 
     * the cooraaaaaaaadinate system of the robot (array (6))
     * 
     * @param targetSpace is weather or not the camera pose is returned as a
     *                    targetSpace
     * @return the camera pose
     */
    public double[] getCameraPose(boolean targetSpace) {
        return table.getEntry((targetSpace) ? "camerapose_targetspace" : "camerapose_robotspace")
                .getDoubleArray(new double[6]);
    }

    /**
     * 3D transform of the primary-
     *  in-view AprilTag
     * in the coordinate system of the Camera (array (6))
     * 
     * or
     * 
     * the coordinate system of the Robot (array (6))
     * 
     * @param cameraSpace is weather or not the target pose is returned as a
     *                    cameraSpace
     * @return the target pose
     */
    public double[] getTargetPose(boolean cameraSpace) {
        return table.getEntry((cameraSpace) ? "targetpose_cameraspace" : "targetpose_robotspace")
                .getDoubleArray(new double[6]);
    }

    public boolean containsTagID(boolean cameraSpace, int tagID) {
        double[] targetPose = getTargetPose(cameraSpace);
        for (double pose : targetPose) {
            if (pose == tagID) {
                return true;
            }
        }
        return false;
    }

    /**
     * Are we currently tracking any potential targets
     * 
     * @return Whether the limelight has any valid targets (0 or 1)
     */
    public boolean hasValidTarget() {
        return (table.getEntry("tv").getDouble(0) == 0) ? false : true;
    }

    private NetworkTableEntry getPipelineLatencyRaw() {
        return table.getEntry("tl");
    }

    /**
     * Latency in ms of the pipeline
     * 
     * @return The pipeline’s latency contribution (s) Add at least 11ms for image
     *         capture latency.
     */
    public double getPipelineLatency() {
        return getPipelineLatencyRaw().getDouble(0) / 1000.0;
    }

    /**
     * Time between the end of the exposure of the middle row
     * of the sensor to the beginning of the tracking pipeline.
     * 
     * @return Capture pipeline latency (s).
     */
    public double getCaptureLatency() {
        return table.getEntry("cl").getDouble(0) / 1000.0;
    }

    /**
     * Total latency (s) of the entire pipeline (s)
     * 
     * @return Total latency (s)
     */
    public double getCombinedLatencySeconds() {
        return (getPipelineLatency() - getCaptureLatency());
    }

    /**
     * Sets the Lime Light LED's
     * 
     * @param mode - LightMode (On, Off, Blinking, or determined by the pipeline)
     */
    public void turnLightOff() {
        table.getEntry("ledMode").setNumber(1);
    }

    /**
     * Sets the limelights current pipeline
     * 
     * @param pipeline The pipeline index (0-9)
     */
    public void setPipeline(int pipeline) {
        table.getEntry("pipeline").setNumber(pipeline);;
    }

    // https://github.com/StuyPulse/Alfred/blob/c7ebcdf0e586a32e6e28b5b808fb6aee6deee325/src/main/java/frc/util/Limelight.java#L28
    public boolean isConnected() {// Force an update and get current time
        timingTestEntryValue = !timingTestEntryValue; // flip test value
        timingTestEntry.setBoolean(timingTestEntryValue);
        long currentTime = timingTestEntry.getLastChange();

        // Get most recent update from limelight
        long lastUpdate = getPipelineLatencyRaw().getLastChange();

        // Calculate limelights last update
        timeDifference = currentTime - lastUpdate;
        boolean connected = timeDifference < Constants.LIMELIGHT_MAX_UPDATE_TIME;

        return connected;
    }
}