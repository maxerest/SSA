package com.example.Ground_stations;
import com.example.Parametres;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.TopocentricFrame;

public class Ground_station {
    
    public static TopocentricFrame station1 = new TopocentricFrame(Parametres.earth, new GeodeticPoint(
        FastMath.toRadians(45.0),   // latitude
        FastMath.toRadians(-75.0),  // longitude
        0.0),                       // altitude (m)
        "GS1");

    public static TopocentricFrame station2 = new TopocentricFrame(Parametres.earth, new GeodeticPoint(
        FastMath.toRadians(5.0),
        FastMath.toRadians(10.0),
        0.0),
        "GS2");
}
