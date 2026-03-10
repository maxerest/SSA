package com.example.Orbiting_object;

import com.example.Manoeuvre.Manoeuvre;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

import java.util.*;

public class Satellite extends Orbiting_object {

    //Defintion manoeuvre
    private List<Manoeuvre> liste_manoeuvre_sat= new LinkedList<>();
    private List<SpacecraftState> liste_state_propa= new ArrayList<>();
    private  Map<String, Map<AbsoluteDate,Double>> map_pourcentage_collision= new LinkedHashMap<>();
    private String motor_name="Moteur_1";

    // Parametres satellite
    private double area=2;    // m^2
    private double cd=0.85 ;
    private final double srpCrossSection;   // m²
    private final double srpCoeff;
    private Map<String,AntennaParameters> map_parametres_antennes=new LinkedHashMap<>();
    private double puissance_amplificateur;


    private Satellite(Builder builder) {
        super(builder);  // Initialize parent with its Builder
        this.motor_name = builder.motor_name;
        this.area = builder.area;
        this.cd = builder.cd;
        this.srpCrossSection = builder.srpCrossSection;
        this.srpCoeff = builder.srpCoeff;
        this.map_parametres_antennes.put("Antenna 1",new AntennaParameters());
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
    public Map<String,AntennaParameters> getMap_parametres_antennes() {
        return map_parametres_antennes;
    }


    public double getSrpCoeff() {return srpCoeff;}
    public List<Manoeuvre> getListe_manoeuvre_sat(){return liste_manoeuvre_sat;}
    public List<SpacecraftState> get_liste_state_propa(){return liste_state_propa;}
    public Map<String, Map<AbsoluteDate,Double>> getMap_pourcentage_collision() {return map_pourcentage_collision;}
    public void add_manoeuvre (double start_date_manoeuvre, double duration_manoeuvre){
        try{
        liste_manoeuvre_sat.add( new Manoeuvre(start_date_manoeuvre,duration_manoeuvre));
        }catch (Exception e){
            System.out.println("Echec de l'ajout de la manoeuvre");
        }
    }
    public void launch_manoeuvre(NumericalPropagator propagator){
        for (Manoeuvre m : liste_manoeuvre_sat){
            System.out.println(Manoeuvre.Motor.getthrust(this.get_Motor_name()));
            System.out.println(Manoeuvre.Motor.getISP(this.get_Motor_name()));
            m.launch_manoeuvre(Manoeuvre.Motor.getISP(this.get_Motor_name()),Manoeuvre.Motor.getthrust(this.get_Motor_name()),propagator);
        }
    }
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

    public void remove_last_map_percentage_collison(){
        try{
            List<String> allKeys = new ArrayList<>(map_pourcentage_collision.keySet());
            if (allKeys.size()>2){
                String actualSecondLastKey = allKeys.get(allKeys.size() - 2);
                this.map_pourcentage_collision.remove(actualSecondLastKey);
            }

        }catch (Exception e){
            System.out.println("Problème à l'ajout d'un % de collision pour un sat sur le sat : "+this.nom_sat);
            System.out.println(e);
        }
    }
    public class AntennaParameters {
        private double gain;           // dBi
        private double noiseFigure;    // dB
        private double frequency;      // GHz
        private double bandwidth;      // MHz
        private double efficiency;     // %
        private double txPowerDbm;     // dBm

        public AntennaParameters() {
            this.gain = 15.0;           // dBi (satellite TX antenna)
            this.noiseFigure = 5.0;     // dB (satellite receiver, higher than ground station)
            this.frequency = 12.0;      // GHz (Ku-band uplink/downlink)
            this.bandwidth = 36.0;      // MHz (typical satellite transponder)
            this.efficiency = 0.60;     // 60% (space-qualified antenna)
            this.txPowerDbm = 20.0;
        }
        public AntennaParameters(double gain, double noiseFigure, double frequency,double bandwidth,double efficiency) {
            this.gain = gain;
            this.noiseFigure = noiseFigure;
            this.frequency = frequency;
            this.bandwidth=bandwidth;
            this.efficiency=efficiency;
        }

        public double getGain() {
            return gain;
        }

        public double getNoiseFigure() {
            return noiseFigure;
        }

        public double getFrequency() {
            return frequency;
        }

        public double getBandwidth() {
            return bandwidth;
        }

        public double getEfficiency() {
            return efficiency;
        }
        public double getTxPowerDbm(){return txPowerDbm;}
        public void setGain(double gain) {
            this.gain = gain;
        }

        public void setNoiseFigure(double noiseFigure) {
            this.noiseFigure = noiseFigure;
        }

        public void setFrequency(double frequency) {
            this.frequency = frequency;
        }

        public void setBandwidth(double bandwidth) {
            this.bandwidth = bandwidth;
        }

        public void setEfficiency(double efficiency) {
            this.efficiency = efficiency;
        }
    }
}
