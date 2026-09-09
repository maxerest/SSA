package com.example.View;

import com.example.Parametres;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.IERSConventions;

public class Sun_position {

    public static Vector3D getSun_position_initial() {
        System.out.println(Parametres.date_orekit);
        AbsoluteDate date = Parametres.date_orekit;

        CelestialBody sun =
                CelestialBodyFactory.getSun();

        Frame itrf =
                FramesFactory.getITRF(
                        IERSConventions.IERS_2010,
                        true
                );

        // Sun position expressed in Earth-fixed coordinates
        Vector3D sunPosECEF =
                sun.getPosition(date, itrf);

        return sunPosECEF.normalize();
    }
}
