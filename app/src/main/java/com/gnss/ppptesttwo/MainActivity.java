package com.gnss.ppptesttwo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.GnssMeasurementsEvent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.gnss.ppptesttwo.adjust.WeightedLeastSquares;
import com.gnss.ppptesttwo.constellations.Constellation;
import com.gnss.ppptesttwo.constellations.GalileoConstellation;
import com.gnss.ppptesttwo.constellations.GnssConstellation;
import com.gnss.ppptesttwo.constellations.GpsConstellation;
import com.gnss.ppptesttwo.navifromftp.Coordinates;
import com.gnss.ppptesttwo.navifromftp.RinexNavigationGalileo;
import com.gnss.ppptesttwo.navifromftp.RinexNavigationGps;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class MainActivity extends AppCompatActivity {


    private Button bt_ftp, bt_start, bt_stop;
    private TextView textview_log;
    private CheckBox cb_gps, cb_glonass, cb_galileo, cb_beidou;
    private TextView textView7,textView6,textView8,textView9;
    /**
     * 是否开始进行gnss数据记录
     */
    private boolean isRecordStart = false;
    private boolean isRecordStop = true;

    /**
     * 记录哪些卫星系统参与运算
     */
    private boolean isGps = false;
    private boolean isGlonass = false;
    private boolean isGalileo = false;
    private boolean isBeidou = false;



    private static final String TAG = MainActivity.class.getSimpleName();

    private Location mLocation;

    private LocationManager mLocationManager;

    public final static String NASA_NAVIGATION_HOURLY = "ftp://cddis.gsfc.nasa.gov/pub/gps/data/hourly/${yyyy}/${ddd}/hour${ddd}0.${yy}n.Z";

    public final static String NASA_NAVIGATION_HOURLY_Galileo = "ftp://cddis.gsfc.nasa.gov/pub/gps/data/hourly/${yyyy}/${ddd}/${hh4}/AMC400USA_R_${yyyy}${ddd}${hh4}00_01H_EN.rnx.gz";

    public final static String BKG_GALILEO_RINEX = "ftp://igs.bkg.bund.de/EUREF/BRDC/${yyyy}/${ddd}/BRDC00WRD_R_${yyyy}${ddd}0000_01D_EN.rnx.gz";



    private RinexNavigationGps mRinexNavigationGps;
    private RinexNavigationGalileo mRinexNavigationGalileo;
    private GpsConstellation mGpsConstellation;
    private WeightedLeastSquares mWeightedLeastSquares;

    private GnssConstellation mGnssConstellation;

    /**
     * Calculated pose of the receiver
     */
    private Coordinates pose;
    /**
     * 接收机位置的初始化
     */
    private boolean poseinitialized = false;


    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);

        //这个是解决主线程没有网络连接问题的代码
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        //验证GNSS权限
        verifyGnssPermissions(this);
        //验证文件权限
        verifyStoragePermissions(this);
        registerLocation();
        registerGnssMeasurements();
        handler=new Handler();


        //参与运算的系统
        mGpsConstellation = new GpsConstellation();
        mRinexNavigationGps = new RinexNavigationGps();
        mRinexNavigationGalileo=new RinexNavigationGalileo();
        mWeightedLeastSquares = new WeightedLeastSquares();





        bt_start = findViewById(R.id.bt_start);
        bt_stop = findViewById(R.id.bt_stop);
        bt_ftp = findViewById(R.id.bt_ftp);
        textview_log = findViewById(R.id.text_log);
        cb_gps = findViewById(R.id.cb_gps);
        cb_glonass = findViewById(R.id.cb_glonass);
        cb_galileo = findViewById(R.id.cb_galileo);
        cb_beidou = findViewById(R.id.cb_beidou);

        // 获取TextView对象
        textView7 = findViewById(R.id.textView7);
        textView6=findViewById(R.id.textView6);
        textView8=findViewById(R.id.textView8);
        textView9=findViewById(R.id.textView9);

        //点击事件
        bt_start.setOnClickListener(new onclick());
        bt_stop.setOnClickListener(new onclick());
        bt_ftp.setOnClickListener(new onclick());

        cb_gps.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    isGps = true;
                updateGnssConstellation();
                if (!isChecked)
                    isGps = false;
                updateGnssConstellation();
                Toast.makeText(MainActivity.this, isChecked ? "GPS选中" : "GPS未选中", Toast.LENGTH_SHORT).show();
            }
        });
        cb_galileo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    isGalileo = true;
                updateGnssConstellation();
                if (!isChecked)
                    isGalileo = false;
                updateGnssConstellation();
                Log.d("MyTag", "gal");
                Toast.makeText(MainActivity.this, isChecked ? "GALILEO选中" : "GALILEO未选中", Toast.LENGTH_SHORT).show();

            }
        });
        cb_glonass.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    isGlonass = true;
                updateGnssConstellation();
                if (!isChecked)
                    isGlonass = false;
                updateGnssConstellation();
                Toast.makeText(MainActivity.this, isChecked ? "GLONASS选中" : "GLONASS未选中", Toast.LENGTH_SHORT).show();

            }
        });
        cb_beidou.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    isBeidou = true;
                updateGnssConstellation();
                if (!isChecked)
                    isBeidou = false;
                updateGnssConstellation();
                Toast.makeText(MainActivity.this, isChecked ? "BEIDOU选中" : "BEIDOU未选中", Toast.LENGTH_SHORT).show();

            }
        });
        mGnssConstellation=new GnssConstellation(isGps, isGalileo, isGlonass, isBeidou);

    }
    private void updateGnssConstellation()
    {
        mGnssConstellation=new GnssConstellation(isGps, isGalileo, isGlonass, isBeidou);
    }

    //设备的位置发生变化时被调用
    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (isRecordStart && !isRecordStop) {
                if (location != null && !poseinitialized) {
                    mLocation = location;
                    //用于创建一个表示全球 XYZ 坐标系中的Coordinates位置坐标对象
                    pose = Coordinates.globalGeodInstance(mLocation.getLatitude(), mLocation.getLongitude(), mLocation.getAltitude());
                    //**************************************************************//
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    double altitude = location.getAltitude();
                    textView7.setText(String.format("Latitude: %.6f\nLongitude: %.6f\nAltitude: %.2f", latitude, longitude, altitude));
                    //**************************************************************//
                    Log.d("MyTag", "Latitude: " + mLocation.getLatitude() + ", Longitude: " + mLocation.getLongitude() + ", Altitude: " + mLocation.getAltitude());
                    writeToFile(pose.getX()+","+pose.getY()+","+pose.getZ());
                    writeToFile("\n");
                    poseinitialized = true;
                }
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
        @Override
        public void onProviderEnabled(String provider) {
        }
        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    //这段代码的主要功能是在接收到 GNSS 测量事件时，更新 GNSS 卫星星座数据，计算卫星位置，并使用加权最小二乘法计算设备的位置。
    // 如果计算出的卫星数量足够多（至少 5 颗），则将当前时间、计算出的位置坐标（X, Y, Z）写入文件。
    // 只有在 GNSS 测量事件记录开始且未停止的情况下，才会执行这些操作。
    private GnssMeasurementsEvent.Callback gnssMeasurementsEvent = new GnssMeasurementsEvent.Callback() {
        @Override
        public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
            super.onGnssMeasurementsReceived(eventArgs);
            if (isRecordStart && !isRecordStop) {
                //10.12计算符合观测条件的卫星
                mGnssConstellation.updateMeasurements(eventArgs);//计算伪距,并根据测量状态决定是否将卫星参数添加到观测卫星列表中。
                mGnssConstellation.calculateSatPosition(pose);//计算卫星位置

                double  currenttime=mGnssConstellation.getTime().getGpsTime();//获取GPS周内秒
                //**************************************************************//
                textView6.setText(String.valueOf(mGnssConstellation.getUsedConstellationSize()));
                //**************************************************************//
                Log.d("MyTag", "Used Constellation Size: " + mGnssConstellation.getUsedConstellationSize());
                //当前使用的卫星数量是否大于等于 5，如果是，则使用加权最小二乘法计算设备的位置
                if(mGnssConstellation.getUsedConstellationSize()>=5)
                {

                    pose=mWeightedLeastSquares.calculatePose(mGnssConstellation,pose);
                    //**************************************************************//
                    textView8.setText(String.format("X: %.6f  Y: %.6f  Z: %.2f", pose.getX(), pose.getY(), pose.getZ()));
                    //**************************************************************//
                    try {
                        out.write(currenttime+","+pose.getX()+","+pose.getY()+","+pose.getZ());
                        out.write("\n");
                        out.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.d("theend", "Current Time: " + currenttime + ", X: " + pose.getX() + ", Y: " + pose.getY() + ", Z: " + pose.getZ());
                    //writeToFile(currenttime+","+pose.getX()+","+pose.getY()+","+pose.getZ());
                    //writeToFile("\n");
                }




                //System.out.println(mGpsConstellation.getSatellite(0).getAccumulatedCorrection());

//                for(Constellation constellation :mConstellations)
//                {
//                    constellation.updateMeasurements(eventArgs);
//                    System.out.println(constellation.getUsedConstellationSize()+"   "+constellation.getConstellationId());
//                }

            }
        }
    };


    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * 在对sd卡进行读写操作之前调用这个方法 * Checks if the app has permission to write to device storage * If the app does not has permission then the user will be prompted to grant permissions
     */
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static void verifyStoragePermissions(Activity activity) {    // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {        // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }
    }

    private static final int REQUEST_ACCESS_FINE_LOCATION = 2;

    private void verifyGnssPermissions(Activity activity) {
        //看GNSS权限是否开启
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);

        }
    }


    private void registerLocation() {
        //Check Permission
        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_ACCESS_FINE_LOCATION);
        }
        // 为获取地理位置信息时设置查询条件
        String bestProvider = mLocationManager.getBestProvider(getCriteria(), true);

        // 获取位置信息

        // 如果不设置查询要求，getLastKnownLocation方法传人的参数为LocationManager.GPS_PROVIDER

        assert bestProvider != null;
        mLocation = mLocationManager.getLastKnownLocation(bestProvider);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mLocationListener);
    }


    private static Criteria getCriteria() {

        Criteria criteria = new Criteria();

        // 设置定位精确度 Criteria.ACCURACY_COARSE比较粗略，Criteria.ACCURACY_FINE则比较精细

        criteria.setAccuracy(Criteria.ACCURACY_FINE);

        // 设置是否要求速度

        criteria.setSpeedRequired(true);

        // 设置是否允许运营商收费

        criteria.setCostAllowed(false);

        // 设置是否需要方位信息

        criteria.setBearingRequired(true);

        // 设置是否需要海拔信息

        criteria.setAltitudeRequired(true);

        // 设置对电源的需求

        criteria.setPowerRequirement(Criteria.POWER_HIGH);

        return criteria;

    }


    //检查并请求位置权限，然后注册一个 GNSS 测量事件的回调。如果应用没有所需的权限，则会请求权限；如果权限已经授予，
    // 则会注册回调并输出一条日志信息。
    private void registerGnssMeasurements() {
        //Check Permission
        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_ACCESS_FINE_LOCATION);
        }
        mLocationManager.registerGnssMeasurementsCallback(gnssMeasurementsEvent);
        Log.i(TAG, "Register callback -> measurementsEvent");
    }

    private class onclick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.bt_ftp:
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                //mRinexNavigationGps.getFromFTP(NASA_NAVIGATION_HOURLY);
                                //mRinexNavigationGalileo.getFromFTP(NASA_NAVIGATION_HOURLY_Galileo);
                                mGnssConstellation.init();//初始化导航电文
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    break;
                case R.id.bt_start:
                    isRecordStart = true;
                    isRecordStop = false;
                    //textView9.setText("哈哈哈");
                    Log.d("MyTag", "startle");

                    createFile();

                    break;
                case R.id.bt_stop:
                    isRecordStop = true;
                    isRecordStart = false;
                    closeFile();
                    break;
            }
        }
    }

    private FileWriter out = null;
    private void createFile() {
        Date date = new Date();

        String dateString = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(date);
        String type = "pos"; //Observable file
        int year = Integer.parseInt(new SimpleDateFormat("yy", Locale.US).format(date));
        String yearString;
        if (year - 10 < 0)
            yearString = "0" + year;
        else
            yearString = "" + year;
        String fileName = "bt" + dateString+ "." + yearString + type;

        try {
            File rootFile = new File(this.getFilesDir().getAbsolutePath(), this.getString(R.string.app_name) + "_pos");
            if (!rootFile.exists()) rootFile.mkdirs();

            File file = new File(rootFile, fileName);
            out = new FileWriter(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "CreateFile, File name = " + fileName);
    }

    private void closeFile()
    {
        Log.i(TAG, "CloseFile");
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeToFile(String str)
    {
        try {
            out.write(str);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("威胁如");
        }
    }

}
