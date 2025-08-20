package com.example.Analytics_Propagator.Type1;
import java.io.File;
import org.orekit.data.DataProvider;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;

public class propagator_1
{
    public String main(  )
    {
        final File orekitData = new File("C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA");
        final DataProvider dirCrawler = new DirectoryCrawler(orekitData);
        DataContext.getDefault().getDataProvidersManager().addProvider(dirCrawler);

        System.out.println( "Currently in propagator 1" );
        return "Currently in propagator 1";
    }
}