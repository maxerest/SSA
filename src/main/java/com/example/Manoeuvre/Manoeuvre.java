package com.example.Manoeuvre;
import com.example.Parametres;
import com.example.View.Visulations;
import org.json.JSONObject;
import com.example.Analytics_Propagator.Type1.Handlers;
import com.example.App;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.maneuvers.Maneuver;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.forces.maneuvers.propulsion.PropulsionModel;
import org.orekit.forces.maneuvers.trigger.DateBasedManeuverTriggers;
import org.orekit.forces.maneuvers.trigger.ManeuverTriggers;
import org.orekit.frames.LOFType;
import org.orekit.propagation.events.*;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.propagation.numerical.NumericalPropagator;

import com.example.Orbiting_object.*;
import org.orekit.time.AbsoluteDate;

import java.util.ArrayList;
import java.util.List;


public class Manoeuvre {
    private double start_time_from_epoch;
    private double duration;
    private Satellite satellite;
    private Vector3D direction_of_burn;

    public Manoeuvre(String message){
        parse(message);
        new_manoeuvre_creation();
        System.out.println("Maneuver created for satellite "+satellite.get_Name()+" at "+start_time_from_epoch+"s after epoch for a duration of "+duration+"s");

        Visulations.csvWriters.get("real_sats").close(); // close the writer so i can safe delete lines and the reopen to write
        try{
            Visulations.deleteSatelliteRows(satellite.get_Name());
        }catch(Exception e){
            System.out.println("CSV files from Maneuver could not be updated in sat : "+satellite.get_Name());
            e.printStackTrace();
        }
        Visulations.reopen_CSV_writer_append();
        NumericalPropagator propagator= satellite.getPropagator();
        propagator.resetInitialState(satellite.get_s_initialState());
        propagator.propagate(Parametres.date_orekit,Parametres.date_orekit.shiftedBy(Parametres.duration));
        System.out.println("Propagation completed for " + satellite.get_Name());
    }


    /**
     * Launch the manoeuvre within the propagator
     */

    // From motor, direction , and the event detector, it creates the maneuvre and returns it for the propagator
    public void new_manoeuvre_creation() {
        NumericalPropagator propagator = satellite.getPropagator();
        AbsoluteDate burnStart = Parametres.date_orekit.shiftedBy(this.start_time_from_epoch);
        final ManeuverTriggers triggers = new DateBasedManeuverTriggers(burnStart, duration);
        AttitudeProvider maneuverAttitude = new LofOffset(Parametres.frame, LOFType.TNW);
        PropulsionModel propulsionModel =
                new BasicConstantThrustPropulsionModel(satellite.getMotor().getThrust(),satellite.getMotor().getISP(),
                        this.direction_of_burn,
                        "Maneuver");
        propagator.addForceModel(new Maneuver(maneuverAttitude, triggers, propulsionModel));
    }

    public  void parse(String rawMessage) {
        // rawMessage is expected as "MANEUVER_CREATE:{...json...}"
        String prefix = "MANEUVER_CREATE:";
        if (!rawMessage.startsWith(prefix)) {
            throw new IllegalArgumentException("Not a MANEUVER_CREATE message: " + rawMessage);
        }
        String json = rawMessage.substring(prefix.length());
        JSONObject obj = new JSONObject(json);

        this.satellite=App.liste_par_sats_real_orbit.stream().filter(satellite -> satellite.get_Name().equals(obj.getString("satName"))).findFirst().orElseThrow();
        this.start_time_from_epoch = obj.getDouble("triggerValue");
        this.duration = obj.getDouble("durationSec");
        this.direction_of_burn=directionToVector(obj.getString("direction"));
    }
    public static Vector3D directionToVector(String direction) {
        return switch (direction) {
            case "tangential+" -> Vector3D.PLUS_I;   // T axis, e.g. LOFType.TNW
            case "tangential-" -> Vector3D.MINUS_I;
            case "radial+" -> Vector3D.PLUS_J;    // depends on chosen LOFType — verify axis order
            case "radial-" -> Vector3D.MINUS_J;
            case "normal+" -> Vector3D.PLUS_K;    // W axis (orbital normal)
            case "normal-" -> Vector3D.MINUS_K;
            default -> throw new IllegalArgumentException("Unknown direction: " + direction);
        };
    }
    private List<String> new_csv_lines(){
        List<String> lines = new ArrayList<>();


        return lines;
    }
}
