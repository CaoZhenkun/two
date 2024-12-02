package com.gnss.ppptesttwo.constellations;

import android.location.GnssMeasurementsEvent;

import com.gnss.ppptesttwo.Constants;
import com.gnss.ppptesttwo.Time;
import com.gnss.ppptesttwo.corrections.Correction;
import com.gnss.ppptesttwo.corrections.TopocentricCoordinates;
import com.gnss.ppptesttwo.navifromftp.Coordinates;
import com.gnss.ppptesttwo.navifromftp.RinexNavigationGalileo;
import com.gnss.ppptesttwo.navifromftp.RinexNavigationGps;
import com.gnss.ppptesttwo.navifromftp.SatellitePosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GnssConstellation extends Constellation{

    //这个改成武汉大学的了
    public final static String NASA_NAVIGATION_HOURLY = "ftp://igs.gnsswhu.cn/pub/gps/data/hourly/${yyyy}/${ddd}/hour${ddd}0.${yy}n.gz";

    //混合星历
    //public final static String NASA_NAVIGATION_HOURLY = "ftp://igs.gnsswhu.cn/pub/gps/data/hourly/${yyyy}/${ddd}/${hh4}/CORD00ARG_R_${yyyy}${ddd}${hh4}00_01H_MN.rnx.gz";



    //这个改成武汉大学的了
    public final static String NASA_NAVIGATION_HOURLY_Galileo = "ftp://igs.gnsswhu.cn/pub/gps/data/hourly/${yyyy}/${ddd}/hour${ddd}0.${yy}g.gz";

    public final static String BKG_GALILEO_RINEX = "ftp://igs.bkg.bund.de/EUREF/BRDC/${yyyy}/${ddd}/BRDC00WRD_R_${yyyy}${ddd}0000_01D_EN.rnx.gz";


    public final static String NASA_NAVIGATION_HOURLY_Galileo_ABPO = "ftp://cddis.gsfc.nasa.gov/pub/gps/data/hourly/${yyyy}/${ddd}/${hh4}/ABPO00MDG_R_${yyyy}${ddd}${hh4}00_01H_EN.rnx.gz";


    public final static String ESA_GALILEO_RINEX = "ftp://gssc.esa.int/gnss/data/daily/${yyyy}/${ddd}/ankr${ddd}0.${yy}l.Z";
    private GpsConstellation gpsConstellation = new GpsConstellation();
    private GalileoConstellation galileoConstellation = new GalileoConstellation();

    private static final String NAME = "GNSS";

    /**
     * List holding observed satellites
     */
    protected List<SatelliteParameters> observedSatellites = new ArrayList<>();
    private List<SatelliteParameters> unusedSatellites = new ArrayList<>();

    private RinexNavigationGps rinexNavigationGps=new RinexNavigationGps();
    private RinexNavigationGalileo rinexNavigationGalileo=new RinexNavigationGalileo();
    private boolean isgps;
    private boolean isgalileo;
    private boolean isglonass;
    private boolean isbeidou;



    public GnssConstellation(boolean isgps,boolean isgalileo,boolean isglonass,boolean isbeidou)
    {
        this.isgps=isgps;
        this.isglonass=isglonass;
        this.isbeidou=isbeidou;
        this.isgalileo=isgalileo;
    }
    public void init()
    {
        if(isgps) {
            try {
                rinexNavigationGps.getFromFTP(NASA_NAVIGATION_HOURLY);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(isgalileo) {
            try {
                rinexNavigationGalileo.getFromFTP(NASA_NAVIGATION_HOURLY_Galileo);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void setIsgps(boolean isgps) {
        this.isgps = isgps;
    }

    public void setIsgalileo(boolean isgalileo) {
        this.isgalileo = isgalileo;
    }

    public void setIsbeidou(boolean isbeidou) {
        this.isbeidou = isbeidou;
    }

    public void setIsglonass(boolean isglonass) {
        this.isglonass = isglonass;
    }


    @Override
    public Coordinates getRxPos() {
        synchronized (this) {
            return gpsConstellation.getRxPos();
        }
    }

    @Override
    public void setRxPos(Coordinates rxPos) {
        synchronized (this) {
             gpsConstellation.setRxPos(rxPos);
            galileoConstellation.setRxPos(rxPos);
        }
    }

    @Override
    public SatelliteParameters getSatellite(int index) {
        synchronized (this) {
            return observedSatellites.get(index);
        }
    }

    @Override
    public List<SatelliteParameters> getSatellites() {
        synchronized (this) {
            return observedSatellites;
        }
    }

    @Override
    public List<SatelliteParameters> getUnusedSatellites() {
        return unusedSatellites;
    }

    @Override
    public int getVisibleConstellationSize() {
        synchronized (this) {
            return observedSatellites.size()+unusedSatellites.size();
        }
    }

    @Override
    public int getUsedConstellationSize() {
        synchronized (this) {
            return observedSatellites.size();
        }
    }

    @Override
    public double getSatelliteSignalStrength(int index) {
        synchronized (this) {
            return observedSatellites.get(index).getSignalStrength();
        }
    }

    @Override
    public int getConstellationId() {
        return 2;
    }

    @Override
    public void addCorrections(ArrayList<Correction> corrections) {

    }

    @Override
    public Time getTime() {
        synchronized (this) {
            return gpsConstellation.getTime();
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void updateMeasurements(GnssMeasurementsEvent event) {

        gpsConstellation.updateMeasurements(event);
        //if(isgalileo) galileoConstellation.updateMeasurements(event);

    }

    public void  calculateSatPosition( Coordinates position) {

        // Make a list to hold the satellites that are to be excluded based on elevation/CN0 masking criteria创建一个列表以保存要根据 elevation/CN0 掩膜条件排除的卫星
        //根据给定的位置坐标计算 GPS 和 Galileo 卫星星座中每个卫星的位置，并将符合条件的卫星添加到观测卫星列表中
        List<SatelliteParameters> excludedSatellites = new ArrayList<>();
        synchronized (this) {
            if(isgps ) gpsConstellation.calculateSatPosition(this.rinexNavigationGps, position);//点击按钮下载导航电文后已经初始化了导航电文
            if(isgalileo) galileoConstellation.calculateSatPosition(this.rinexNavigationGalileo, position);


            observedSatellites.clear();
            unusedSatellites.clear();

            for (int i=0; i<gpsConstellation.getUsedConstellationSize(); i++){
                observedSatellites.add(gpsConstellation.getSatellite(i));
            }

            for (int i=0; i<galileoConstellation.getUsedConstellationSize(); i++){
                observedSatellites.add(galileoConstellation.getSatellite(i));
            }

            unusedSatellites.addAll(gpsConstellation.getUnusedSatellites());
            unusedSatellites.addAll(galileoConstellation.getUnusedSatellites());

        }
    }
}
