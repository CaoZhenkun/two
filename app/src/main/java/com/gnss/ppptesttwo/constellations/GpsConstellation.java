package com.gnss.ppptesttwo.constellations;

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.util.Log;
import com.gnss.ppptesttwo.GNSSData;
import com.gnss.ppptesttwo.Constants;
import com.gnss.ppptesttwo.PositioningData;
import com.gnss.ppptesttwo.Time;
import com.gnss.ppptesttwo.corrections.Correction;
import com.gnss.ppptesttwo.corrections.IonoCorrection;
import com.gnss.ppptesttwo.corrections.ShapiroCorrection;
import com.gnss.ppptesttwo.corrections.TopocentricCoordinates;
import com.gnss.ppptesttwo.corrections.TropoCorrection;
import com.gnss.ppptesttwo.navifromftp.Coordinates;
import com.gnss.ppptesttwo.navifromftp.RinexNavigationGps;
import com.gnss.ppptesttwo.navifromftp.SatellitePosition;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mateusz Krainski on 17/02/2018.
 * This class is for...
 * <p>
 * GPS Pseudorange computation algorithm by: Mareike Burba
 * - variable name changes and comments were added
 * to fit the description in the GSA white paper
 * by: Sebastian Ciuban
 */

public class GpsConstellation extends Constellation{

    private final static char satType = 'G';
    private static final String NAME = "GPS L1";
    private static final String TAG = "GpsConstellation";
    private static double L1_FREQUENCY = 1.57542e9;
    private static double FREQUENCY_MATCH_RANGE = 0.1e9;

    private boolean fullBiasNanosInitialized = false;
    private long FullBiasNanos;

    private Coordinates rxPos;
    protected double tRxGPS;
    protected double weekNumberNanos;
    private List<SatelliteParameters> unusedSatellites = new ArrayList<>();

    public double getWeekNumber() {
        return weekNumberNanos;
    }

    public double gettRxGPS() {
        return tRxGPS;
    }

    private static final int constellationId = GnssStatus.CONSTELLATION_GPS;
    private static double MASK_ELEVATION = 20; // degrees
    private static double MASK_CN0 = 10; // dB-Hz
    PositioningData positioningData;

    /**
     * Corrections which are to be applied to received pseudoranges
     */
    private ArrayList<Correction> corrections = new ArrayList<>();

    /**
     * Time of the measurement
     */
    private Time timeRefMsec;

    protected int visibleButNotUsed = 0;

    // Condition for the pseudoranges that takes into account a maximum uncertainty for the TOW
    // (as done in gps-measurement-tools MATLAB code)
    private static final int MAXTOWUNCNS = 50;                                     // [nanoseconds]

    private RinexNavigationGps rinexNavGps = null;

    /**
     * List holding observed satellites
     */
    protected List<SatelliteParameters> observedSatellites = new ArrayList<>();


    public GpsConstellation() {

        addCorrections(new IonoCorrection(), new TropoCorrection(),new ShapiroCorrection());
    }


    public void addCorrections(IonoCorrection ionoCorrection, TropoCorrection tropoCorrection, ShapiroCorrection shapiroCorrection) {
        synchronized (this) {
            corrections.add(ionoCorrection);
            corrections.add(tropoCorrection);
            corrections.add(shapiroCorrection);
        }
    }


    public static boolean approximateEqual(double a, double b, double eps) {
        return Math.abs(a - b) < eps;
    }

    public void updateMeasurements1(GnssMeasurementsEvent event) {

        synchronized (this) {
            //初始化变量
            positioningData.gnssDataArrayListtest.clear();
            //获取 GNSS 时钟信息
            GnssClock gnssClock = event.getClock();//获取 GNSS 时钟信息Gets the GNSS receiver clock information associated with the measurements for the current event.
            long TimeNanos = gnssClock.getTimeNanos();//获取当前时间的纳秒数
            //timeRefMsec = new Time(System.currentTimeMillis());//获取当前时间的毫秒数
            double BiasNanos = gnssClock.getBiasNanos();//获取时钟偏差的纳秒数
            double gpsTime, pseudorange;

            // Use only the first instance of the FullBiasNanos (as done in gps-measurement-tools)
            //仅使用 FullBiasNanos 的第一个实例（就像在 gps-measurement-tools 中所做的那样）
            if (!fullBiasNanosInitialized) {
                if(!gnssClock.hasFullBiasNanos())
                {
                    return;
                }
                FullBiasNanos = gnssClock.getFullBiasNanos();
                fullBiasNanosInitialized = true;
            }


            for (GnssMeasurement measurement : event.getMeasurements()) {

                if (measurement.getConstellationType() != GnssStatus.CONSTELLATION_GPS)
                    continue;

                if (measurement.hasCarrierFrequencyHz())
                    if (!approximateEqual(measurement.getCarrierFrequencyHz(), L1_FREQUENCY, FREQUENCY_MATCH_RANGE))
                        continue;

                long ReceivedSvTimeNanos = measurement.getReceivedSvTimeNanos();
                double TimeOffsetNanos = measurement.getTimeOffsetNanos();

                gpsTime =
                        TimeNanos - (FullBiasNanos + BiasNanos); // TODO intersystem bias?


                tRxGPS =
                        gpsTime + TimeOffsetNanos;

                weekNumberNanos =
                        Math.floor((-1. * FullBiasNanos) / Constants.NUMBER_NANO_SECONDS_PER_WEEK)
                                * Constants.NUMBER_NANO_SECONDS_PER_WEEK;

                //计算伪距
                pseudorange =
                        (tRxGPS - weekNumberNanos - ReceivedSvTimeNanos) / 1.0E9
                                * Constants.SPEED_OF_LIGHT;

                // TODO Check that the measurement have a valid state such that valid pseudoranges are used in the PVT algorithm

                /*

                According to https://developer.android.com/ the GnssMeasurements States required
                for GPS valid pseudoranges are:

                int STATE_CODE_LOCK         = 1      (1 << 0)
                int int STATE_TOW_DECODED   = 8      (1 << 3)

                */
                //检查测量状态，确保测量状态满足条件（如 STATE_CODE_LOCK 和 STATE_TOW_DECODED）
                int measState = measurement.getState();

                // Bitwise AND to identify the states
                boolean codeLock = (measState & GnssMeasurement.STATE_CODE_LOCK) != 0;//检查是否已经码锁定（STATE_CODE_LOCK）
                boolean towDecoded = (measState & GnssMeasurement.STATE_TOW_DECODED) != 0;// 检查是否已经解码时间（STATE_TOW_DECODED）
                boolean towKnown = false;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // 检查当前设备的 API 级别是否大于或等于 Android O（API 级别 26）
                    towKnown = (measState & GnssMeasurement.STATE_TOW_KNOWN) != 0;
                    // 如果设备的 API 级别大于或等于 Android O，检查是否已知时间（STATE_TOW_KNOWN）
                    //STATE_TOW_KNOWN:这个GNSS测量的跟踪状态已经知道了周时间（Time-of-Week），可能没有通过空中解码，但已经通过其他来源确定。
                }
                //如果测量状态满足条件，则创建 SatelliteParameters 对象并添加到 observedSatellites 列表中。
                //否则，创建 SatelliteParameters 对象并添加到 unusedSatellites 列表中，并增加 visibleButNotUsed 计数。
                if (codeLock && (towDecoded || towKnown) && pseudorange < 1e9) { // && towUncertainty
                    //存储卫星参数的对象
                    GNSSData gnssData=new GNSSData();

                    gnssData.setSATstate(1);
                    gnssData.setGnssType('G');
                    gnssData.setpseudorange(pseudorange);//存储伪距
                    gnssData.setSATID(measurement.getSvid());//存储卫星ID




                    //measurement.getCn0DbHz()获取载噪比（C/N0），单位为dB-Hz。
                    gnssData.setSnr(measurement.getCn0DbHz());


                    if (measurement.hasCarrierFrequencyHz())
                        //如果有载波，获取载波频率
                        gnssData.setFrequency(measurement.getCarrierFrequencyHz());

                    positioningData.gnssDataArrayListtest.add(gnssData);


                }
            }
            System.out.println("here");
        }
    }
    //更新 GPS 卫星星座的测量数据。具体来说，它处理从 GnssMeasurementsEvent 对象中获取的 GNSS 测量数据，
    // 计算伪距（Pseudorange），并根据测量状态决定是否将卫星参数添加到观测卫星列表中。
    public void updateMeasurements(GnssMeasurementsEvent event) {

        synchronized (this) {
            //******************************************下面中文注释是AI生成，不可都信***************************************//
            //初始化变量
            visibleButNotUsed = 0;//初始化未使用的可见卫星数量
            observedSatellites.clear();//清空观测卫星列表
            unusedSatellites.clear();//清空未使用的卫星列表
            //获取 GNSS 时钟信息
            GnssClock gnssClock = event.getClock();//获取 GNSS 时钟信息Gets the GNSS receiver clock information associated with the measurements for the current event.
            long TimeNanos = gnssClock.getTimeNanos();//获取当前时间的纳秒数
            timeRefMsec = new Time(System.currentTimeMillis());//获取当前时间的毫秒数
            double BiasNanos = gnssClock.getBiasNanos();//获取时钟偏差的纳秒数
            double gpsTime, pseudorange;

            // Use only the first instance of the FullBiasNanos (as done in gps-measurement-tools)
            //仅使用 FullBiasNanos 的第一个实例（就像在 gps-measurement-tools 中所做的那样）
            if (!fullBiasNanosInitialized) {
                FullBiasNanos = gnssClock.getFullBiasNanos();
                fullBiasNanosInitialized = true;
            }



            for (GnssMeasurement measurement : event.getMeasurements()) {

                if (measurement.getConstellationType() != constellationId)
                    continue;

                if (measurement.hasCarrierFrequencyHz())
                    if (!approximateEqual(measurement.getCarrierFrequencyHz(), L1_FREQUENCY, FREQUENCY_MATCH_RANGE))
                        continue;

                long ReceivedSvTimeNanos = measurement.getReceivedSvTimeNanos();
                double TimeOffsetNanos = measurement.getTimeOffsetNanos();

                gpsTime =
                        TimeNanos - (FullBiasNanos + BiasNanos); // TODO intersystem bias?


                tRxGPS =
                        gpsTime + TimeOffsetNanos;

                weekNumberNanos =
                        Math.floor((-1. * FullBiasNanos) / Constants.NUMBER_NANO_SECONDS_PER_WEEK)
                                * Constants.NUMBER_NANO_SECONDS_PER_WEEK;

                //计算伪距
                pseudorange =
                        (tRxGPS - weekNumberNanos - ReceivedSvTimeNanos) / 1.0E9
                                * Constants.SPEED_OF_LIGHT;

                // TODO Check that the measurement have a valid state such that valid pseudoranges are used in the PVT algorithm

                /*

                According to https://developer.android.com/ the GnssMeasurements States required
                for GPS valid pseudoranges are:

                int STATE_CODE_LOCK         = 1      (1 << 0)
                int int STATE_TOW_DECODED   = 8      (1 << 3)

                */
                //检查测量状态，确保测量状态满足条件（如 STATE_CODE_LOCK 和 STATE_TOW_DECODED）
                int measState = measurement.getState();

                // Bitwise AND to identify the states
                boolean codeLock = (measState & GnssMeasurement.STATE_CODE_LOCK) != 0;//检查是否已经码锁定（STATE_CODE_LOCK）
                boolean towDecoded = (measState & GnssMeasurement.STATE_TOW_DECODED) != 0;// 检查是否已经解码时间（STATE_TOW_DECODED）
                boolean towKnown = false;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // 检查当前设备的 API 级别是否大于或等于 Android O（API 级别 26）
                    towKnown = (measState & GnssMeasurement.STATE_TOW_KNOWN) != 0;
                    // 如果设备的 API 级别大于或等于 Android O，检查是否已知时间（STATE_TOW_KNOWN）
                    //STATE_TOW_KNOWN:这个GNSS测量的跟踪状态已经知道了周时间（Time-of-Week），可能没有通过空中解码，但已经通过其他来源确定。
                }
                //如果测量状态满足条件，则创建 SatelliteParameters 对象并添加到 observedSatellites 列表中。
                //否则，创建 SatelliteParameters 对象并添加到 unusedSatellites 列表中，并增加 visibleButNotUsed 计数。
                if (codeLock && (towDecoded || towKnown) && pseudorange < 1e9) { // && towUncertainty
                    //存储卫星参数的对象
                    SatelliteParameters satelliteParameters = new SatelliteParameters(
                            measurement.getSvid(),
                            new Pseudorange(pseudorange, 0.0));

                    satelliteParameters.setUniqueSatId("G" + satelliteParameters.getSatId() + "_L1");//唯一的ID可能用于标识特定的卫星及其信号频段


                    //measurement.getCn0DbHz()获取载噪比（C/N0），单位为dB-Hz。
                    satelliteParameters.setSignalStrength(measurement.getCn0DbHz());

                    satelliteParameters.setConstellationType(measurement.getConstellationType());//星座类型，返回值为定义在GnssStatus中的常量

                    if (measurement.hasCarrierFrequencyHz())
                        //如果有载波，获取载波频率
                        satelliteParameters.setCarrierFrequency(measurement.getCarrierFrequencyHz());

                    observedSatellites.add(satelliteParameters);//添加到卫星观测列表

                    Log.d(TAG, "updateConstellations(" + measurement.getSvid() + "): " + pseudorange);
                    Log.d(TAG, "updateConstellations: Passed with measurement state: " + measState);
                } else {
                    //不满足条件，添加到不用的卫星列表
                    SatelliteParameters satelliteParameters = new SatelliteParameters(
                            measurement.getSvid(),
                            null);

                    satelliteParameters.setUniqueSatId("G" + satelliteParameters.getSatId() + "_L1");

                    satelliteParameters.setSignalStrength(measurement.getCn0DbHz());

                    satelliteParameters.setConstellationType(measurement.getConstellationType());

                    if (measurement.hasCarrierFrequencyHz())
                        satelliteParameters.setCarrierFrequency(measurement.getCarrierFrequencyHz());

                    unusedSatellites.add(satelliteParameters);
                    visibleButNotUsed++;//可见卫星数++
                }
            }
        }
    }


    public double getSatelliteSignalStrength(int index) {
        synchronized (this) {
            return observedSatellites.get(index).getSignalStrength();
        }
    }


    public int getConstellationId() {
        synchronized (this) {
            return constellationId;
        }
    }

    @Override
    public void addCorrections(ArrayList<Correction> corrections) {

            this.corrections=corrections;

    }

    @Override
    public Time getTime() {
        return timeRefMsec;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * @param rinexNavGps 获取的导航电文
     * @param position    接收机的近似位置
     */


    public void calculateSatPosition(RinexNavigationGps rinexNavGps, Coordinates position) {

        // Make a list to hold the satellites that are to be excluded based on elevation/CN0 masking criteria
        //根据给定的 RINEX 导航文件 和接收机位置  计算每个观测卫星的位置，并根据仰角掩码标准排除不符合条件的卫星。
        List<SatelliteParameters> excludedSatellites = new ArrayList<>();

        synchronized (this) {
            System.out.println("此历元卫星数：" + observedSatellites.size());

            //接收机的位置，这里用接收机的位置主要是为了计算对流层延迟
            rxPos = Coordinates.globalXYZInstance(position.getX(), position.getY(), position.getZ());

            System.out.println("接收机近似位置：" + position.getX() + "," + position.getY() + "," + position.getZ());

            for (SatelliteParameters observedSatellite : observedSatellites) {
                // Computation of the GPS satellite coordinates in ECEF frame
                //计算GPS卫星在ECEF坐标系中的坐标 地心地固系

                //观测数据的时间
                // Determine the current GPS week number
                int gpsWeek = (int) (weekNumberNanos / Constants.NUMBER_NANO_SECONDS_PER_WEEK);

                // Time of signal reception in GPS Seconds of the Week (SoW)
                double gpsSow = (tRxGPS - weekNumberNanos) * 1e-9;
                Time tGPS = new Time(gpsWeek, gpsSow);

                // Convert the time of reception from GPS SoW to UNIX time (milliseconds)
                long timeRx = tGPS.getMsec();//UNIX time (milliseconds)

                SatellitePosition rnp = rinexNavGps.getSatPositionAndVelocities(
                        timeRx,//观测数据的时间
                        observedSatellite.getPseudorange(),
                        observedSatellite.getSatId(),
                        satType,
                        0.0
                );

                if (rnp == null) {
                    excludedSatellites.add(observedSatellite);
                    //GnssCoreService.notifyUser("Failed getting ephemeris data!", Snackbar.LENGTH_SHORT, RNP_NULL_MESSAGE);
                    continue;
                }

                observedSatellite.setSatellitePosition(rnp);

                //设置卫星相对于用户的方位角和仰角，并根据这些角度来设置伪距测量方差
                observedSatellite.setRxTopo(
                        new TopocentricCoordinates(
                                rxPos,
                                observedSatellite.getSatellitePosition()));

                //Add to the exclusion list the satellites that do not pass the masking criteria
                if (observedSatellite.getRxTopo().getElevation() < MASK_ELEVATION) {
                    excludedSatellites.add(observedSatellite);
                }
                double accumulatedCorrection = 0;
                //计算累计的误差，包括对流层延迟和电离层延迟
                //遍历计算三种误差并累加
                for (Correction correction : corrections) {

                    correction.calculateCorrection(
                            new Time(timeRx),
                            rxPos,
                            observedSatellite.getSatellitePosition(),
                            rinexNavGps);

                    accumulatedCorrection += correction.getCorrection();

                }
                System.out.println("此卫星误差为：" + observedSatellite.getSatId() + "," + accumulatedCorrection);


                observedSatellite.setAccumulatedCorrection(accumulatedCorrection);
            }

            // Remove from the list all the satellites that did not pass the masking criteria
            // 移除不符合条件的卫星
            visibleButNotUsed += excludedSatellites.size();
            observedSatellites.removeAll(excludedSatellites);
            unusedSatellites.addAll(excludedSatellites);
        }
    }


    public Coordinates getRxPos() {
        synchronized (this) {
            return rxPos;
        }
    }
    public static void registerClass() {
        register(
                NAME,
                GpsConstellation.class);
    }


    public void setRxPos(Coordinates rxPos) {
        synchronized (this) {
            this.rxPos = rxPos;
        }
    }


    public SatelliteParameters getSatellite(int index) {
        synchronized (this) {
            return observedSatellites.get(index);
        }
    }


    public List<SatelliteParameters> getSatellites() {
        synchronized (this) {
            return observedSatellites;
        }
    }


    public List<SatelliteParameters> getUnusedSatellites() {
        return unusedSatellites;
    }


    public int getVisibleConstellationSize() {
        synchronized (this) {
            return getUsedConstellationSize() + visibleButNotUsed;
        }
    }


    public int getUsedConstellationSize() {
        synchronized (this) {
            return observedSatellites.size();
        }
    }

}
