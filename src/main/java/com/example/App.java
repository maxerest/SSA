package com.example;

import com.example.Analytics_Propagator.Type1.*;
import java.io.File;

import org.orekit.data.DataProvider;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.propagation.SpacecraftState;
import java.util.Date;
/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        final File orekitData = new File("C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA");
        final DataProvider dirCrawler = new DirectoryCrawler(orekitData);
        DataContext.getDefault().getDataProvidersManager().addProvider(dirCrawler);
        Frame frame = FramesFactory.getEME2000();
        AbsoluteDate date_orekit = new AbsoluteDate(new Date(), TimeScalesFactory.getUTC()).shiftedBy(2*3600);
        PositionAngleType positionAngleType = PositionAngleType.MEAN;
        Orbit orbit_kepl = new KeplerianOrbit(7000e3, 0.001, Math.toRadians(15),  Math.toRadians(30),  Math.toRadians(45), Math.toRadians(60), positionAngleType, frame, date_orekit, 3.986004418e14);
        KeplerianPropagator propagator = new KeplerianPropagator(orbit_kepl);
        AbsoluteDate fin_propagation = date_orekit.shiftedBy(1);
        SpacecraftState etat_fin_propagation= propagator.propagate(fin_propagation);
        System.err.println("Position at end of propagation: " + etat_fin_propagation.getPosition().getX() + " " + etat_fin_propagation.getPosition().getY() + " " + etat_fin_propagation.getPosition().getZ());;
        System.err.println("Velocity at end of propagation: " + (etat_fin_propagation.getPosition().getNorm()-6367e3));
    }
}
