package com.example;

import java.util.Date;

import org.orekit.bodies.BodyShape;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

public class Parametres
{   
    public String nom_sat ="Sat_1";
    //Gestion temps
    public static AbsoluteDate date_orekit = new AbsoluteDate(new Date(), TimeScalesFactory.getUTC()).shiftedBy(2*3600);
    public static double duration = Constants.JULIAN_DAY-(3600*23);
    
    // définition de la Terre
    public static Frame frame = FramesFactory.getEME2000();
    public static double mu = Constants.EIGEN5C_EARTH_MU;
    public static BodyShape earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,Constants.WGS84_EARTH_FLATTENING,FramesFactory.getITRF(IERSConventions.IERS_2010, true));

    // Definiton parametres orbitaux
    private double semi_axis = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 700e3;
    private double eccentricity = 0.001;
    private double inclinaison = Math.toRadians(45);
    private double long_noeud_ascendant = Math.toRadians(30);
    private double arg_periastre = Math.toRadians(45);
    private double anomalie = Math.toRadians(60);
    private PositionAngleType type_anomalie = PositionAngleType.MEAN;
    private Orbit orbit_kepl;

    // Parametres satellite
    public double area = 1.0;    // m^2
    public double cd   = 2.2;
    public double srpCrossSection = 2;   // m²
    public double srpCoeff        = 1.30;
        
    //Seul de detection altitude
    public Double Detectionaltitude =Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 699e3;
       
    //Inital state of the satellite
    public SpacecraftState s_initialState ;
    public Parametres(String nom_sat,double semi_axis,double eccentricity,double inclinaison,double long_noeud_ascendant,double arg_periastre,double anomalie,PositionAngleType type_anomalie){
        this.nom_sat= nom_sat;
        this.semi_axis=semi_axis;
        this.eccentricity=eccentricity;
        this.inclinaison=inclinaison;
        this.long_noeud_ascendant=long_noeud_ascendant;
        this.arg_periastre=arg_periastre;
        this.anomalie=anomalie;
        this.type_anomalie=type_anomalie;
        orbit_kepl = new KeplerianOrbit(this.semi_axis, this.eccentricity, this.inclinaison,  this.long_noeud_ascendant,  this.arg_periastre, this.anomalie, this.type_anomalie, frame, date_orekit, mu);
        this.s_initialState= new SpacecraftState(orbit_kepl);
    }
    public Orbit get_Orbit(){
        return orbit_kepl;
    }
    public AbsoluteDate get_Date(){
        return date_orekit;
    }
    public String get_Name(){
        return nom_sat;
    }

    
}
