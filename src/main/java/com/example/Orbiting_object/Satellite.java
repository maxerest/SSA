package com.example.Orbiting_object;

import com.example.Manoeuvre.Manoeuvre;
import com.example.Parametres;
import org.hipparchus.geometry.Space;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;

public class Satellite extends Orbiting_object {

    //Defintion manoeuvre
    private int type_moteur=1;
    private double start_manoeuvre=1000;
    private double duration_manoeuvre=1000;
    public Manoeuvre manoeuvre = new Manoeuvre.Builder()
            .firingDate(Parametres.date_orekit.shiftedBy(start_manoeuvre))
            .duration(duration_manoeuvre)
            .build();
    // Parametres satellite
    private double area=2;    // m^2
    private double cd=0.85 ;
    private final double srpCrossSection;   // m²
    private final double srpCoeff;

    private Satellite(Builder builder) {
        super(builder);  // Initialize parent with its Builder
        this.type_moteur = builder.type_moteur;
        this.start_manoeuvre = builder.start_manoeuvre;
        this.duration_manoeuvre = builder.duration_manoeuvre;
        this.area = builder.area;
        this.cd = builder.cd;
        this.srpCrossSection = builder.srpCrossSection;
        this.srpCoeff = builder.srpCoeff;
    }
    public static class Builder extends  Orbiting_object.Builder{

        private int type_moteur = 1;
        private double start_manoeuvre = 300;
        private double duration_manoeuvre = 360;
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
        public Builder type_moteur(int t) { this.type_moteur = t; return this; }
        public Builder start_manoeuvre(double s) { this.start_manoeuvre = s; return this; }
        public Builder duration_manoeuvre(double d) { this.duration_manoeuvre = d; return this; }
        public Builder area(double a) { this.area = a; return this; }
        public Builder cd(double c) { this.cd = c; return this; }
        public Builder srpCrossSection(double s) { this.srpCrossSection = s; return this; }
        public Builder srpCoeff(double s) { this.srpCoeff = s; return this; }
        public Satellite build() { return new Satellite(this); }
    }

    public double getCd() {
        return cd;
    }

    public int getType_moteur() {
        return type_moteur;
    }

    public double getStart_manoeuvre() {
        return start_manoeuvre;
    }

    public double getDuration_manoeuvre() {
        return duration_manoeuvre;
    }

    public Manoeuvre getManoeuvre() {
        return manoeuvre;
    }

    public double getArea() {
        return area;
    }

    public double getSrpCrossSection() {
        return srpCrossSection;
    }

    public double getSrpCoeff() {return srpCoeff;}

}
