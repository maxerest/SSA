package com.example.View;

import com.example.Parametres;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.time.AbsoluteDate;

public class Sun_position {

    public static Vector3D getSun_position_initial() {
        CelestialBody sun = CelestialBodyFactory.getSun();
        Vector3D sunPos = sun.getPosition(Parametres.date_orekit, Parametres.frame); // position of the sun at eme2000 at start epoch
        CelestialBody  earth = CelestialBodyFactory.getEarth();
        Vector3D earthPos = earth.getPosition(Parametres.date_orekit, Parametres.frame); // position of the sun at eme2000 at start epoch
        // Sun position relative to Earth (Earth-centered vector)
        return sunPos.subtract(earthPos).normalize();
    }
}
