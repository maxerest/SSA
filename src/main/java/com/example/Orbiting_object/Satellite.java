package com.example.Orbiting_object;

import com.example.Manoeuvre.Manoeuvre;
import com.example.Parametres;
import org.hipparchus.geometry.Space;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.forces.maneuvers.Maneuver;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.forces.maneuvers.propulsion.PropulsionModel;
import org.orekit.forces.maneuvers.trigger.ManeuverTriggers;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

import java.util.LinkedList;
import java.util.List;

public class Satellite extends Orbiting_object {

    //Defintion manoeuvre
    private List<Manoeuvre> liste_manoeuvre_sat= new LinkedList<>();
    private String motor_name="Moteur_1";

    // Parametres satellite
    private double area=2;    // m^2
    private double cd=0.85 ;
    private final double srpCrossSection;   // m²
    private final double srpCoeff;

    private Satellite(Builder builder) {
        super(builder);  // Initialize parent with its Builder
        this.motor_name = builder.motor_name;
        this.area = builder.area;
        this.cd = builder.cd;
        this.srpCrossSection = builder.srpCrossSection;
        this.srpCoeff = builder.srpCoeff;
    }

    public boolean is_firing(SpacecraftState currentState) {
        for (Manoeuvre m:liste_manoeuvre_sat){
            if (m.getTriggers().isFiring(currentState.getDate(), null)){
                return true;
            }
        }
        return false;
    }

    public boolean is_firing(AbsoluteDate date) {
        for (Manoeuvre m:liste_manoeuvre_sat){
            if (m.getTriggers().isFiring(date, null)){
                return true;
            }
        }
        return false;
    }

    public static class Builder extends  Orbiting_object.Builder{
        private String motor_name = "Motor_1";
        private double area = 1.0;
        private double cd = 2.2;
        private double srpCrossSection = 2;
        private double srpCoeff = 1.30;

        @Override
        public Builder nom_sat(String name) { super.nom_sat(name); return this; }
        @Override
        public Builder mass(double mass) { super.mass(mass); return this; }
        @Override
        public Builder semi_axis(double sa) { super.semi_axis(sa); return this; }
        @Override
        public Builder eccentricity(double e) { super.eccentricity(e); return this; }
        @Override
        public Builder inclinaison(double i) { super.inclinaison(i); return this; }
        @Override
        public Builder long_noeud_ascendant(double lna) { super.long_noeud_ascendant(lna); return this; }
        @Override
        public Builder arg_periastre(double arg) { super.arg_periastre(arg); return this; }
        @Override
        public Builder anomalie(double a) { super.anomalie(a); return this; }
        @Override
        public Builder type_anomalie(PositionAngleType t) { super.type_anomalie(t); return this; }
        @Override
        public Builder Detectionaltitude(Double d) { super.Detectionaltitude(d); return this; }
        @Override
        public  Builder s_initialState(SpacecraftState s) {super.s_initialState(s);return this;}
        // Delegate to parent Builder for orbital parameters
        public Builder motor_name(String s) { this.motor_name = s; return this; }
        public Builder area(double a) { this.area = a; return this; }
        public Builder cd(double c) { this.cd = c; return this; }
        public Builder srpCrossSection(double s) { this.srpCrossSection = s; return this; }
        public Builder srpCoeff(double s) { this.srpCoeff = s; return this; }
        public Satellite build() {super.build(); return new Satellite(this); }
    }

    public double getCd() {
        return cd;
    }

    public String get_Motor_name() {
        return motor_name;
    }

    public double getArea() {
        return area;
    }

    public double getSrpCrossSection() {
        return srpCrossSection;
    }

    public double getSrpCoeff() {return srpCoeff;}
    public List<Manoeuvre> getListe_manoeuvre_sat(){return liste_manoeuvre_sat;}

    public void add_manoeuvre (double start_date_manoeuvre, double duration_manoeuvre){
        try{
        liste_manoeuvre_sat.add( new Manoeuvre(start_date_manoeuvre,duration_manoeuvre));
        }catch (Exception e){
            System.out.println("Echec de l'ajout de la manoeuvre");
        }
    }
    public void launch_manoeuvre(NumericalPropagator propagator){
        System.out.println(liste_manoeuvre_sat);
        for (Manoeuvre m : liste_manoeuvre_sat){
            System.out.println(Manoeuvre.Motor.getthrust(this.get_Motor_name()));
            System.out.println(Manoeuvre.Motor.getISP(this.get_Motor_name()));
            m.launch_manoeuvre(Manoeuvre.Motor.getISP(this.get_Motor_name()),Manoeuvre.Motor.getthrust(this.get_Motor_name()),propagator);
        }
    }
}
