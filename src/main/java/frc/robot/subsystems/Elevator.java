package frc.robot.subsystems;

import com.revrobotics.CANSparkBase.IdleMode;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.ParallelRaceGroup;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.util.Neo;
import frc.robot.util.Constants.ClimbConstants;
import monologue.Logged;

public class Elevator extends SubsystemBase implements Logged {

    private final Neo elevatorLeft;
    private final Neo elevatorRight;
    
    public Elevator() {
        elevatorLeft = new Neo(14);
        elevatorRight = new Neo(15);

        elevatorLeft.setIdleMode(IdleMode.kBrake);
        elevatorRight.setIdleMode(IdleMode.kBrake);

        elevatorLeft.setInverted(true);
        elevatorRight.setInverted(false);
    }    

    public void setPosition(double pos1, double pos2) {
        elevatorLeft.setPosition(pos1);
        elevatorRight.setPosition(pos2);
    }

    

    public Command toTop(boolean right) {
        
       if (right) {
            return runOnce(() -> this.setPosition(ClimbConstants.ALMOST_HIGH_LIMIT, ClimbConstants.HIGH_LIMIT));
       } 
       else {
            return runOnce(() -> this.setPosition(ClimbConstants.HIGH_LIMIT, ClimbConstants.ALMOST_HIGH_LIMIT));
       }
       
    }
    
    public Command toBottom() {
        

        ParallelRaceGroup group = new ParallelRaceGroup();
        group.addCommands(Commands.runOnce(() -> elevatorRight.setPosition(ClimbConstants.ROCK_BOTTOM)));
        group.addCommands(Commands.runOnce(() -> elevatorLeft.setPosition(ClimbConstants.ROCK_BOTTOM)));
        
        return Commands.runOnce(() -> group.execute(), this);
    }
    
}