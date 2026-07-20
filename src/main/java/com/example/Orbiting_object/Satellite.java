package com.example.Orbiting_object;

import com.example.Manoeuvre.Manoeuvre;
import com.example.Orbiting_object.Satellite_sub_systems.*;
import com.example.Parametres;
import com.example.RevisitFrequency.EO_observations;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.NadirPointing;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

import java.util.*;

public class Satellite extends Orbiting_object {



    private NumericalPropagator propagator;
    //Defintion manoeuvre
    private AttitudeProvider attitude_sat = new NadirPointing(Parametres.frame,Parametres.earth);
    private List<Manoeuvre> liste_manoeuvre_sat= new LinkedList<>();
    private List<SpacecraftState> liste_state_propa= new ArrayList<>();
    private  Map<String, Map<AbsoluteDate,Double>> map_pourcentage_collision= new LinkedHashMap<>();
    private Motors motor;
    //Parametre EO
    private String currently_observing=null;
    private final Vector3D boresight = new Vector3D(0,0,1);
    private final double agility = Math.toRadians(60); // Capability of the satellite to look of Nadir to point
    private EO_sensors.Sensor sensor;
    private final List<EO_observations> list_observations_on_board= new LinkedList<>();
    // Parametres satellite
    private double area=2;    // m^2
    private double cd=0.85 ;
    private final double srpCrossSection;   // m²
    private final double srpCoeff;
    private double memory_on_board=2000000; //MB
    private final double max_memory_on_board=2048000;
    // Parametres satcom
    private Map<String,Antenna> map_parametres_antennes=new LinkedHashMap<>();
    private MODCOD.modcod MODCOD;


    private Satellite(Builder builder) {
        super(builder);  // Initialize parent with its Builder
        this.area = builder.area;
        this.cd = builder.cd;
        this.srpCrossSection = builder.srpCrossSection;
        this.srpCoeff = builder.srpCoeff;
        this.sensor = builder.sensor;
        this.motor=builder.motor;
    this.map_parametres_antennes.put(builder.antenna.getName(),builder.antenna);
    }

    public boolean is_firing(SpacecraftState currentState) {
        for (Manoeuvre m:liste_manoeuvre_sat){
            //if (m.getTriggers().isFiring(currentState.getDate(), null)){
                return true;
            //}
        }
        return false;
    }

    public boolean is_firing(AbsoluteDate date) {
        for (Manoeuvre m:liste_manoeuvre_sat){
           // if (m.getTriggers().isFiring(date, null)){
                return true;
            //}
        }
        return false;
    }
    public void add_observation(EO_observations observations) {
        list_observations_on_board.add(observations);
        memory_on_board-=observations.getTotal_data();
    }
    public String isCurrently_observing() {
        return currently_observing;
    }

    public void setCurrently_observing(String currently_observing) {
        this.currently_observing = currently_observing;
    }

    public AttitudeProvider getAttitude_sat() {
        return attitude_sat;
    }

    public void setAttitude_sat(AttitudeProvider attitude_sat) {
        this.attitude_sat = attitude_sat;
    }

    public static class Builder extends  Orbiting_object.Builder{
        private Motors motor;
        private double area = 1.0;
        private double cd = 2.2;
        private double srpCrossSection = 2;
        private double srpCoeff = 1.30;
        private EO_sensors.Sensor sensor;
        private Antenna antenna;

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
        @Override
        public Builder date_initialState(AbsoluteDate d){super.date_initialState(d);
            return this;}
        public Builder motor(Motors m) { this.motor = m; return this; }
        public Builder area(double a) { this.area = a; return this; }
        public Builder cd(double c) { this.cd = c; return this; }
        public Builder srpCrossSection(double s) { this.srpCrossSection = s; return this; }
        public Builder srpCoeff(double s) { this.srpCoeff = s; return this; }
        public Builder eo_sensor(EO_sensors.Sensor eo_s) { this.sensor = eo_s;return this;
        }
        public Builder antenna(Antenna a) {if(a==null){this.antenna=new Antenna();return this;} this.antenna = a;return this; }
        public Satellite build() {super.build();

            return new Satellite(this); }
    }

    public double getCd() {
        return cd;
    }
    public Vector3D getBoresight() {return  boresight;}

    public double getArea() {
        return area;
    }
    public List<EO_observations> getList_observations_on_board() {
        return list_observations_on_board;
    }

    public double getSrpCrossSection() {
        return srpCrossSection;
    }
    public Map<String, Antenna> getMap_parametres_antennes() {
        return map_parametres_antennes;
    }

    public AbsoluteDate getPropagation_date() {
        return super.getDate_start_propagation();
    }

    public EO_sensors.Sensor get_sensor(){return sensor;}
    private final Map<String, EO_sensors.Sensor> activeSensors = new LinkedHashMap<>();

    public double getSrpCoeff() {return srpCoeff;}

    public Motors getMotor() {return motor;}

    public List<SpacecraftState> get_liste_state_propa(){return liste_state_propa;}
    public Map<String, Map<AbsoluteDate,Double>> getMap_pourcentage_collision() {return map_pourcentage_collision;}
    public double getAgility() {return agility;}

    public void add_state (SpacecraftState s){
        this.liste_state_propa.add(s);

    }

    public void add_map_percentage_collison(String nom_sat,AbsoluteDate date, double percentage_collision ){
        try{
            this.map_pourcentage_collision.computeIfAbsent(nom_sat, k -> new LinkedHashMap<>());
            this.map_pourcentage_collision.get(nom_sat).put(date,percentage_collision);
        }catch (Exception e){
            System.out.println("Problème à l'ajout d'un % de collision pour un sat sur le sat : "+this.nom_sat);
            System.out.println(e);
        }
    }

    public void setMemory_on_board(double added_memory) {
        this.memory_on_board = Math.min(max_memory_on_board,memory_on_board+added_memory);
    }
    public NumericalPropagator getPropagator() {
        return propagator;
    }

    public void setPropagator(NumericalPropagator propagator) {
        this.propagator = propagator;
    }
}
