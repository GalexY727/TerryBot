// Primarily referenced from https://github.com/lasarobotics/PurpleLib/blob/master/src/main/java/org/lasarobotics/hardware/revrobotics/Spark.java
package frc.robot.util;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.revrobotics.CANSparkBase;
import com.revrobotics.CANSparkMax;
import com.revrobotics.MotorFeedbackSensor;
import com.revrobotics.REVLibError;
import com.revrobotics.SparkPIDController;
import com.revrobotics.SparkPIDController.ArbFFUnits;
import com.revrobotics.jni.CANSparkMaxJNI;

import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.motorcontrol.Spark;
import frc.robot.util.Constants.FieldConstants;
import frc.robot.util.Constants.NeoMotorConstants;

public class SafeSpark extends CANSparkMax {

    protected final int canID;
    protected final boolean useAbsoluteEncoder;
    SparkPIDController pidController = getPIDController();

    private final int MAX_ATTEMPTS = 20;
    private final int MEASUREMENT_PERIOD = 16;
    private final int AVERAGE_DEPTH = 2;
    private final double BURN_FLASH_WAIT_TIME = 0.5;
    private final double APPLY_PARAMETER_WAIT_TIME = 0.1;

    public SafeSpark(int canID, boolean useAbsoluteEncoder, CANSparkBase.MotorType motorType) {
        super(canID, motorType);

        if (motorType == CANSparkBase.MotorType.kBrushless) {
            fixMeasurementPeriod();
            fixAverageDepth();
        }

        this.canID = canID;
        this.useAbsoluteEncoder = useAbsoluteEncoder;
        if (useAbsoluteEncoder) {
            setFeedbackDevice(getAbsoluteEncoder());
        }
    }

    /**
     * Attempt to apply parameter and check if specified parameter is set correctly
     * 
     * @param parameterSetter        Method to set desired parameter
     * @param parameterCheckSupplier Method to check for parameter in question
     * @return {@link REVLibError#kOk} if successful
     */
    public REVLibError applyParameter(
        Supplier<REVLibError> parameterSetter, 
        BooleanSupplier parameterCheckSupplier, 
        String errorMessage)
    {
        if (FieldConstants.IS_SIMULATION)
            return parameterSetter.get();
        if (parameterCheckSupplier.getAsBoolean())
            return REVLibError.kOk;

        REVLibError status = REVLibError.kError;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            status = parameterSetter.get();
            if (parameterCheckSupplier.getAsBoolean() && status == 1)
                break;
            Timer.delay(APPLY_PARAMETER_WAIT_TIME);
        }

        checkStatus(status, errorMessage);
        return status;
    }

    /**
     * Writes all settings to flash
     * 
     * @return {@link REVLibError#kOk} if successful
     */
    @Override
    public REVLibError burnFlash() {
        if (RobotBase.isSimulation())
            return REVLibError.kOk;

        Timer.delay(BURN_FLASH_WAIT_TIME);
        REVLibError status = super.burnFlash();
        Timer.delay(BURN_FLASH_WAIT_TIME);

        return status;
    }

    /**
     * Restore motor controller parameters to factory defaults until the next
     * controller reboot
     * 
     * @return {@link REVLibError#kOk} if successful
     */
    @Override
    public REVLibError restoreFactoryDefaults() {
        REVLibError status = applyParameter(
            () -> super.restoreFactoryDefaults(),
            () -> false,
            "Restore factory defaults failure!");

        return status;
    }

    
    public REVLibError setSoftLimit(double min, double max) {
        REVLibError status;
        Supplier<REVLibError> parameterSetter = () -> {
            REVLibError forwardLimitStatus = super.setSoftLimit(SoftLimitDirection.kForward, (float)min);
            REVLibError reverseLimitStatus = super.setSoftLimit(SoftLimitDirection.kReverse, (float)max);
            REVLibError softLimitStatus = setSoftLimit(true);
        
            if (forwardLimitStatus != REVLibError.kOk 
                || reverseLimitStatus != REVLibError.kOk 
                || softLimitStatus != REVLibError.kOk) 
            {
                return REVLibError.kInvalid;
            }
        
            return REVLibError.kOk;
        };
        BooleanSupplier parameterCheckSupplier = () -> super.getSoftLimit(SoftLimitDirection.kForward) == min &&
                super.getSoftLimit(SoftLimitDirection.kReverse) == max;

        status = applyParameter(parameterSetter, parameterCheckSupplier, "Set soft limits failure!");
        return status;
    }

    public REVLibError setSoftLimit(boolean enable) {
        REVLibError statusForward = applyParameter(
                () -> super.enableSoftLimit(SoftLimitDirection.kForward, enable),
                () -> super.isSoftLimitEnabled(SoftLimitDirection.kForward) == enable,
                enable ? "Enable" : "Disable" + " soft limit forward failure!");

        REVLibError statusReverse = applyParameter(
                () -> super.enableSoftLimit(SoftLimitDirection.kReverse, enable),
                () -> super.isSoftLimitEnabled(SoftLimitDirection.kReverse) == enable,
                enable ? "Enable" : "Disable" + " soft limit reverse failure!");

        return statusForward == REVLibError.kOk && statusReverse == REVLibError.kOk ? REVLibError.kOk : REVLibError.kError;
    }

    public double getVelocityConversionFactor() {
        if (useAbsoluteEncoder) {
            return getAbsoluteEncoder().getVelocityConversionFactor();
        } else {
            return super.getEncoder().getVelocityConversionFactor();
        }
    }

    /**
     * Get the position of the encoder
     * This will go through the positionConversionFactor if there is one
     * @return The position of the encoder
     */
    public double getPosition() {
        if (useAbsoluteEncoder & FieldConstants.IS_SIMULATION) {
            return getAbsoluteEncoder().getPosition();
        } else {
            return super.getEncoder().getPosition();
        }
    }

   
    /**
     * Set the motor fedeback device to the PIDController
     * 
     * @return {@link REVLibError#kOk} if successful
     */
    public REVLibError setFeedbackDevice(MotorFeedbackSensor device) {
        return applyParameter(
            () -> pidController.setFeedbackDevice(device), 
            () -> true,
            "Feedback device failure!");
    }

    /**
     * Set the PID wrapping to be enabled or disabled
     * 
     * @param enabled Whether to enable or disable the PID wrapping
     * @return {@link REVLibError#kOk} if successful
     */
    public REVLibError setPositionPIDWrappingEnabled(boolean enabled) {
        return applyParameter(
            () -> pidController.setPositionPIDWrappingEnabled(enabled),
            () -> pidController.getPositionPIDWrappingEnabled() = enabled,
            "Set PID wrapping enabled failure!");
    }

    public REVLibError setPositionPIDWrappingBounds(double min, double max) {
        return applyParameter(
            () -> {
                REVLibError minStatus = pidController.setPositionPIDWrappingMinInput(min);
                REVLibError maxStatus = pidController.setPositionPIDWrappingMaxInput(max);
                return minStatus == REVLibError.kOk && maxStatus == REVLibError.kOk ? REVLibError.kOk : REVLibError.kInvalid;
            },
            () -> pidController.getPositionPIDWrappingMinInput() == min && pidController.getPositionPIDWrappingMaxInput() == max,
            "Set PID wrapping bounds failure!");
    }

    /**
     * Set the PIDF controller reference for the Spark
     * 
     * @param value        The value to set the reference to
     * @param controlType  The control type to use
     * @param slot         The slot to set
     * @param arbFF        Arbitrary feed forward value
     * @param arbFFUnits   Units for the arbitrary feed forward value
     */
    public void setPIDReference(double value, ControlType controlType, int slot, double arbitraryFeedForward, ArbFFUnits arbFFUnits) {
        pidController.setReference(value, controlType, slot, arbitraryFeedForward, arbFFUnits);
    }

    /**
     * Sets the idle mode setting for the Spark
     * 
     * @param mode Idle mode (coast or brake).
     * @return {@link REVLibError#kOk} if successful
     */
    public REVLibError setIdleMode(IdleMode mode) {
        REVLibError status = applyParameter(
            () -> super.setIdleMode(mode),
            () -> super.getIdleMode() == mode,
            "Set idle mode failure!");
        return status;
    }

    /**
     * Sets the brake mode for the Spark MAX motor controller.
     * 
     * @return The REVLibError indicating the success or failure of the operation.
     */
    public REVLibError setBrakeMode() {
        return this.setIdleMode(IdleMode.kBrake);
    }

    /**
     * Sets the motor controller to coast mode.
     * 
     * @return The REVLibError indicating the success or failure of the operation.
     */
    public REVLibError setCoastMode() {
        return this.setIdleMode(IdleMode.kCoast);
    }

    /**
     * Sets the current limit in Amps.
     *
     * <p>
     * The motor controller will reduce the controller voltage output to avoid
     * surpassing this
     * limit. This limit is enabled by default and used for brushless only. This
     * limit is highly
     * recommended when using the NEO brushless motor.
     *
     * <p>
     * The NEO Brushless Motor has a low internal resistance, which can mean large
     * current spikes
     * that could be enough to cause damage to the motor and controller. This
     * current limit provides a
     * smarter strategy to deal with high current draws and keep the motor and
     * controller operating in
     * a safe region.
     *
     * @param limit The current limit in Amps.
     */
    public REVLibError setSmartCurrentLimit(int limit) {
        return applyParameter(
            () -> super.setSmartCurrentLimit(limit),
            () -> true,
            "Set current limit failure!");
    }

    /**
     * Represents an error that can occur while using the REVLib library.
     * Rev docs:
     * https://docs.revrobotics.com/sparkmax/operating-modes/control-interfaces#periodic-status-frames
     * 
     * @param frame  The status frame to reset
     * @param period The update period for the status frame.
     * @return error
     */
    public REVLibError changeStatusFrame(StatusFrame frame, int period) {
        REVLibError error = setPeriodicFramePeriod(frame.getFrame(), period);
        // Add a delay to alleviate bus traffic
        Timer.delay(0.1);
        return error;
    }

    /**
     * Resets the status frame of the NEO motor controller to its default period.
     * Rev docs:
     * https://docs.revrobotics.com/sparkmax/operating-modes/control-interfaces#periodic-status-frames
     * 
     * @param frame the status frame to reset
     * @return the REVLibError indicating the result of the operation
     */
    public REVLibError resetStatusFrame(StatusFrame frame) {
        return changeStatusFrame(frame, frame.getDefaultPeriodms());
    }

    /**
     * Represents the status frames for the Neo class.
     * Rev docs:
     * https://docs.revrobotics.com/sparkmax/operating-modes/control-interfaces#periodic-status-frames
     */
    public enum StatusFrame {
        APPLIED_FAULTS_FOLLOWER(PeriodicFrame.kStatus0, 10),
        VELO_TEMP_VOLTAGE_CURRENT(PeriodicFrame.kStatus1, 20),
        ENCODER_POSITION(PeriodicFrame.kStatus2, 20),
        ALL_ANALOG_ENCODER(PeriodicFrame.kStatus3, 50),
        ALL_ALTERNATE_ENCODER(PeriodicFrame.kStatus4, 20),
        ABSOLUTE_ENCODER_POS(PeriodicFrame.kStatus5, 200),
        ABSOLUTE_ENCODER_VELO(PeriodicFrame.kStatus6, 200);

        private final PeriodicFrame frame;
        private final int defaultPeriodms;

        /**
         * Constructs a StatusFrame with the specified frame and default period.
         * 
         * @param frame         The periodic frame.
         * @param defaultPeriod The default period in milliseconds.
         */
        StatusFrame(PeriodicFrame frame, int defaultPeriod) {
            this.frame = frame;
            this.defaultPeriodms = defaultPeriod;
        }

        /**
         * Gets the periodic frame associated with this StatusFrame.
         * 
         * @return The periodic frame.
         */
        public PeriodicFrame getFrame() {
            return frame;
        }

        /**
         * Gets the default period in milliseconds for this StatusFrame.
         * 
         * @return The default period in milliseconds.
         */
        public int getDefaultPeriodms() {
            return defaultPeriodms;
        }
    }

    public enum TelemetryPreference {
        DEFAULT,
        ONLY_ABSOLUTE_ENCODER,
        ONLY_RELATIVE_ENCODER,
        NO_TELEMETRY,
        NO_ENCODER
    }

    /**
     * Set the telemetry preference of the Neo
     * This will disable the telemtry status frames
     * which is found at
     * https://docs.revrobotics.com/sparkmax/operating-modes/control-interfaces#periodic-status-frames
     * 
     * @param type the enum to represent the telemetry preference
     *             this will tell the motor to only send
     *             that type of telemtry
     */
    public void setTelemetryPreference(TelemetryPreference type) {
        int minDelay = NeoMotorConstants.FAST_PERIODIC_STATUS_TIME_MS;
        int maxDelay = NeoMotorConstants.MAX_PERIODIC_STATUS_TIME_MS;

        // No matter what preference, we don't use analog or external encoders.
        changeStatusFrame(StatusFrame.ALL_ALTERNATE_ENCODER, maxDelay);
        changeStatusFrame(StatusFrame.ALL_ANALOG_ENCODER, maxDelay);

        switch (type) {
            // Disable all telemetry that is unrelated to the encoder
            case NO_ENCODER:
                changeStatusFrame(StatusFrame.ENCODER_POSITION, maxDelay);
                changeStatusFrame(StatusFrame.ABSOLUTE_ENCODER_VELO, maxDelay);
                changeStatusFrame(StatusFrame.ABSOLUTE_ENCODER_POS, maxDelay);
                break;
            // Disable all telemetry that is unrelated to absolute encoders
            case ONLY_ABSOLUTE_ENCODER:
                changeStatusFrame(StatusFrame.ENCODER_POSITION, maxDelay);
                changeStatusFrame(StatusFrame.ABSOLUTE_ENCODER_POS, minDelay);
                changeStatusFrame(StatusFrame.ABSOLUTE_ENCODER_VELO, minDelay);
                break;
            // Disable all telemetry that is unrelated to the relative encoder
            case ONLY_RELATIVE_ENCODER:
                changeStatusFrame(StatusFrame.ABSOLUTE_ENCODER_VELO, maxDelay);
                changeStatusFrame(StatusFrame.ABSOLUTE_ENCODER_POS, maxDelay);
                break;
            // Disable everything
            case NO_TELEMETRY:
                changeStatusFrame(StatusFrame.VELO_TEMP_VOLTAGE_CURRENT, maxDelay);
                changeStatusFrame(StatusFrame.ENCODER_POSITION, maxDelay);
                changeStatusFrame(StatusFrame.ALL_ANALOG_ENCODER, maxDelay);
                changeStatusFrame(StatusFrame.ABSOLUTE_ENCODER_VELO, maxDelay);
                break;

            case DEFAULT:
            default:
                break;
        }
    }

}
