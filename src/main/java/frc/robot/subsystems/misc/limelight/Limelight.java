package frc.robot.subsystems.misc.limelight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Supplier;

import edu.wpi.first.apriltag.AprilTag;
import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.apriltag.AprilTagFields;
import edu.wpi.first.math.estimator.PoseEstimator;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Robot;
import frc.robot.subsystems.Swerve;
import frc.robot.util.calc.LimelightHelpers;
import frc.robot.util.calc.LimelightHelpers.LimelightTarget_Fiducial;
import frc.robot.util.calc.LimelightHelpers.Results;
import frc.robot.util.constants.Constants.CameraConstants;
import frc.robot.util.constants.Constants.FieldConstants;
import monologue.Logged;
import monologue.Annotations.Log;

// https://github.com/NAHSRobotics-Team5667/2020-FRC/blob/master/src/main/java/frc/robot/utils/LimeLight.java
public class Limelight extends SubsystemBase implements Logged{

    String limelightName = "limelight";
    private final Supplier<Pose2d> robotPoseSupplier;

    @Log
    Pose3d[] visableTags;

    private static NetworkTableEntry timingTestEntry;
    private static boolean timingTestEntryValue = false;

    private SwerveDrivePoseEstimator poseEstimator;

    @Log
    public boolean isConnected = false;

    @Log
    public long timeDifference = 999_999; // Micro Seconds = 0.999999 Seconds | So the limelight is not connected if the time difference is greater than LimelightConstants.LIMELIGHT_MAX_UPDATE_TIME

    public Limelight(SwerveDrivePoseEstimator poseEstimator, Supplier<Pose2d> robotPoseSupplier) {
        // Uses network tables to check status of limelight
        timingTestEntry = LimelightHelpers.getLimelightNTTableEntry(limelightName,"TIMING_TEST_ENTRY");
        this.robotPoseSupplier = robotPoseSupplier;
        this.poseEstimator = poseEstimator;
        loadAprilTagFieldLayout();
    }

    @Override
    public void periodic() {
        if (FieldConstants.IS_SIMULATION) {
            updateCameras(robotPoseSupplier.get());
        } else {
            getTags();
            runLimelightCode();
        }
    }

    private void runLimelightCode() {
        // Create an "Optional" object that contains the estimated pose of the robot
        // This can be present (sees tag) or not present (does not see tag)
        LimelightHelpers.Results result = getResults();
        // The skew of the tag represents how confident the camera is
        // If the result of the estimatedRobotPose exists,
        // and the skew of the tag is less than 3 degrees,
        // then we can confirm that the estimated position is realistic
        if ( // check validity
            ((!(result.botpose[0] == 0 && result.botpose[1] == 0) )
            // check if good tag
            && (LimelightHelpers.getTA("limelight") >= 0.3 
                || result.targets_Fiducials.length > 1 && LimelightHelpers.getTA("limelight") > 0.4))
            && getRobotPoseTargetSpace().getTranslation().getNorm() < 3.25
        ) {
            Pose2d estimatedRobotPose = result.getBotPose2d_wpiBlue();
            if (Double.isNaN(estimatedRobotPose.getX()) 
                || Double.isNaN(estimatedRobotPose.getY()) 
                || Double.isNaN(estimatedRobotPose.getRotation().getRadians())) {
                return;
            }
            poseEstimator.addVisionMeasurement( 
                estimatedRobotPose,
                Robot.currentTimestamp - getLatencyDiffSeconds());
        }
    }

    public Results getResults() {
        return LimelightHelpers.getLatestResults(limelightName).targetingResults;
    }

    private LimelightTarget_Fiducial[] getTags() {
        LimelightTarget_Fiducial[] fiducials = LimelightHelpers.getLatestResults(limelightName).targetingResults.targets_Fiducials;
        
        setFiducialPoses(fiducials);

        return fiducials;
    }

    private void setFiducialPoses(LimelightTarget_Fiducial[] fiducials) {
        ArrayList<Pose3d> knownFiducials = new ArrayList<>();

        for (LimelightTarget_Fiducial target : fiducials) {
            int tagID = (int) target.fiducialID;
            if (tagID < aprilTagFieldLayout.getTags().size()) {
                knownFiducials.add(aprilTagFieldLayout.getTagPose(tagID).get());
            }
        }

        visableTags = knownFiducials.toArray(new Pose3d[0]);
    }

    public Pose2d getNotePose2d() {
        return noteInVision() 
            ? LimelightHelpers.getTargetPose3d_RobotSpace(limelightName).toPose2d()
            : robotPoseSupplier.get();
    }

    public boolean noteInVision() {
        LimelightHelpers.setPipelineIndex(limelightName, 1);
        return getResults().valid;
    }

    public Pose2d getPose2d() {
        return LimelightHelpers.getBotPose2d_wpiBlue(limelightName);
    }

    public double getTagID() {
        return LimelightHelpers.getFiducialID(limelightName);
    }

    public double getLatencyDiffSeconds() {
        return (LimelightHelpers.getLatency_Pipeline(limelightName)/1000d) - (LimelightHelpers.getLatency_Capture(limelightName)/1000d); 
    }


    // The below code is for simulation only
    // This has nothing to do with the actual limelight

    private AprilTagFieldLayout aprilTagFieldLayout;

    /**
     * Loads the AprilTag field layout from a resource file.
     * If the file is not found, an IOException is caught and a message is printed.
     */
    public void loadAprilTagFieldLayout() {
        try {
            aprilTagFieldLayout = AprilTagFieldLayout.loadFromResource(AprilTagFields.k2024Crescendo.m_resourceFile);
        } catch (IOException e) {
            System.out.println("AprilTag field layout not found! What year is it again?");
        }
    }
    
    /**
     * Updates the cameras by checking if any poses are in the field of view of the cameras.
     * 
     * @param robotPose the pose of the robot
     */
    public void updateCameras(Pose2d robotPose) {
        // Loop through the tag field layout to see if any poses are in the field of view of our cameras
        // Also check to see if the distance is 
        ArrayList<Pose3d> poses = new ArrayList<Pose3d>();  
        for (int i = 0; i < aprilTagFieldLayout.getTags().size(); i++) {
            AprilTag tag = aprilTagFieldLayout.getTags().get(i);
            for (int j = 0; j < CameraConstants.cameras.length; j++) {
                if (isInFieldOfView(robotPose, CameraConstants.cameras[j], tag.pose)) {
                    poses.add(tag.pose); 
                }
            }
        }
        visableTags = poses.toArray(new Pose3d[0]);
    }

    /**
     * Checks if a given tag pose is within the field of view of the camera, based
     * on the robot's pose and camera's pose.
     * 
     * @param robotPose  The pose of the robot.
     * @param cameraPose The pose of the camera.
     * @param tagPose    The pose of the tag.
     * @return True if the tag is within the field of view, false otherwise.
     */
    private boolean isInFieldOfView(Pose2d robotPose, Pose3d cameraPose, Pose3d tagPose) {
        // Put the camera on the robot
        cameraPose = new Pose3d(robotPose).plus(new Transform3d(cameraPose.getTranslation(), cameraPose.getRotation()));
        
        Pose3d cameraToTag = tagPose.relativeTo(cameraPose);
        // Get the atan of the camera's x and y
        // to see if it is in frame
        Rotation2d yawAngle = new Rotation2d(cameraToTag.getTranslation().getX(), cameraToTag.getTranslation().getY());
        // Check if the tag is facing the camera (roughly)
        boolean angleCheck = Math.abs(yawAngle.getDegrees()) < 45;
        boolean distanceCheck = cameraToTag.getTranslation().getX() < 3;
        boolean isFacing = Math.signum(tagPose.getRotation().toRotation2d().minus(cameraPose.getRotation().toRotation2d()).getDegrees()) == -1;
        
        return angleCheck && distanceCheck && isFacing;
    }

    // https://github.com/StuyPulse/Alfred/blob/c7ebcdf0e586a32e6e28b5b808fb6aee6deee325/src/main/java/frc/util/Limelight.java#L28
    public boolean isConnected() {
        // Force an update and get current time
        timingTestEntryValue = !timingTestEntryValue; // flip test value
        timingTestEntry.setBoolean(timingTestEntryValue);
        long currentTime = timingTestEntry.getLastChange();

        // Get most recent update from limelight
        long lastUpdate = LimelightHelpers.getLimelightNTTableEntry(limelightName, "tl").getLastChange();

        // Calculate limelights last update
        timeDifference = currentTime - lastUpdate;
        isConnected = timeDifference < CameraConstants.LIMELIGHT_MAX_UPDATE_TIME;

        return isConnected;
    }

    public Pose2d getRobotPoseTargetSpace() {
        return LimelightHelpers.getBotPose3d_TargetSpace(limelightName).toPose2d();
    }
}