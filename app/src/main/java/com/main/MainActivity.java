package com.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.bluetoothlegatt.EchartOptionUtil;
import com.bluetoothlegatt.EchartView;
import com.bluetoothlegatt.MotionClassifier;
import com.clj.blesample.DocumentTool;
import com.clj.blesample.GattAttributes;
import com.clj.blesample.adapter.DeviceAdapter;
import com.clj.blesample.comm.ObserverManager;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleMtuChangedCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleRssiCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;
import com.github.abel533.echarts.json.GsonOption;
import com.main.operation.OperationActivity;
import com.minio.minio_android.MinioUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_OPEN_GPS = 1;
    private static final int REQUEST_CODE_PERMISSIONS = 2;
    private static final String CHANNEL_ID = "com.main.alert.channel";
    private static final int NOTIFICATION_ID = 1; //"com.main.alert.notification";

    private LinearLayout layout_setting;
    private TextView txt_setting;
    private Button btn_scan;
    private EditText et_name, et_mac, et_uuid;
    private EditText minio_user, minio_password, minio_link, setting_emergency_num, setting_data_dir, setting_model_dir, setting_model_name;
    private Switch sw_auto;
    private ImageView img_loading;

    NotificationManager alertManager;

    private MinioUtils client = new MinioUtils();
    private MotionClassifier mMotionClassifier;

    private EchartView mLineChartLeft;
    private EchartView mLineChartRight;
    boolean mEnableRefreshLeft = false;
    boolean mEnableRefreshRight = false;

    private static String emergency_num = "10086";
    private static String data_dir = "/Download/bleReceived";
    private static String model_dir = "/Download/models";
    private static String model_name = "new-model.tflite";
    private static String prefix = "";

    ExecutorService dataPipeline;
    ExecutorService downloadPool;
    ExecutorService uploadPool;
    ExecutorService refreshEchartsPool;
    RunnableFactory threadFactory = new RunnableFactory();
    Handler handler = new Handler(Looper.getMainLooper());

    private Animation operatingAnim;
    private DeviceAdapter mDeviceAdapter;
    private ProgressDialog progressDialog;

    public MainActivity() throws IOException {
    }

    // Alert notification settings
    private void emitAlert(){

        RemoteViews alertView = new RemoteViews(getPackageName(), R.layout.notification_layout);//远程视图

        Intent mainIntent = new Intent(this, MainActivity.class);
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + emergency_num));

        PendingIntent pending_intent_no;
        PendingIntent pending_intent_yes;

        pending_intent_no = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_MUTABLE);
        pending_intent_yes = PendingIntent.getActivity(this, 1, callIntent, PendingIntent.FLAG_MUTABLE);


        alertView.setOnClickPendingIntent(R.id.alert_btn_no, pending_intent_no);
        alertView.setOnClickPendingIntent(R.id.alert_btn_yes, pending_intent_yes);

        NotificationCompat.Builder alertBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(alertView);
                //.setContentIntent(pending_intent_no);
//                .setAutoCancel(true)
        alertManager.notify(NOTIFICATION_ID, alertBuilder.build());
        //onPause();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        alertManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        alertManager.createNotificationChannel(channel);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        setHandler(); //Handling all messages!

        DocumentTool.verifyStoragePermissions(MainActivity.this);

        BleManager.getInstance().init(getApplication());
        BleManager.getInstance()
                .enableLog(true)
                .setReConnectCount(1, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000);

        //default client settings
        client = client.resetAccount("minioadmin")
                .resetSecretKey("minioadmin123")
                .resetEndPoint("http://10.68.142.34:9000")
                .resetBucketName("test");

        // initialize folders
        prefix = Environment.getExternalStorageDirectory().getPath();
        DocumentTool.addFolder(data_dir);
        DocumentTool.addFolder(model_dir);


        mMotionClassifier = new MotionClassifier(this);

        mLineChartLeft = findViewById(R.id.data_chart1);
        mLineChartRight = findViewById(R.id.data_chart2);

        mLineChartLeft.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);;
                //最好在h5页面加载完毕后再加载数据，防止html的标签还未加载完成，不能正常显示
                mEnableRefreshLeft = true;
            }
        });
        mLineChartRight.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                //最好在h5页面加载完毕后再加载数据，防止html的标签还未加载完成，不能正常显示
                mEnableRefreshRight = true;
            }
        });

        //数据处理管线，串行执行所有的tasks
        dataPipeline = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        uploadPool = new ThreadPoolExecutor(5, 10,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        downloadPool = new ThreadPoolExecutor(5, 10,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());

        refreshEchartsPool = new ThreadPoolExecutor(2, 5,
                50L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());

        createNotificationChannel();
        emitAlert();//发送告警通知
    }

    private void refreshLineChartLeft(Object[] x, Object[] roll, Object[] pitch, Object[] yaw){
        if(mEnableRefreshLeft) {
            refreshEchartsPool.execute(() -> {
                GsonOption op = EchartOptionUtil.getLineChartOptions(x, roll, pitch, yaw, "Left");
                runOnUiThread(() -> mLineChartLeft.refreshEchartsWithOption(op));});
        }
    }


    private void refreshLineChartRight(Object[] x, Object[] roll, Object[] pitch, Object[] yaw){
        if(mEnableRefreshRight){
            refreshEchartsPool.execute(() -> {
                GsonOption op = EchartOptionUtil.getLineChartOptions(x, roll, pitch, yaw, "Right");
                runOnUiThread(() -> mLineChartRight.refreshEchartsWithOption(op));});
        }
    }

    private void setHandler(){
        handler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(@NonNull Message msg) {
                ArrayList<Object> xyAxis;
                super.handleMessage(msg);
                switch (msg.what) {
                    case 0: // 0-left-data-ok
                        ArrayList<Object[]> receivedDataL = (ArrayList<Object[]>) msg.obj;
                        refreshLineChartLeft(receivedDataL.get(0), receivedDataL.get(1), receivedDataL.get(2), receivedDataL.get(3));
                        Log.d(TAG, "0-left-data-ok");
                        break;
                    case 1: // 1-right-data-ok
                        ArrayList<Object[]> receivedDataR = (ArrayList<Object[]>) msg.obj;
                        refreshLineChartRight(receivedDataR.get(0), receivedDataR.get(1), receivedDataR.get(2), receivedDataR.get(3));
                        Log.d(TAG, "1-right-data-ok");
                        break;
                    case 2: // 2-upload-ok
                        Toast.makeText(getApplicationContext(), R.string.upload_success,Toast.LENGTH_SHORT).show();
                        break;
                    case 3: // 3-download-ok
                        Toast.makeText(getApplicationContext(), R.string.download_success,Toast.LENGTH_SHORT).show();
                        break;
                    case 4: // 4-model-predicted
                        xyAxis = (ArrayList<Object>) msg.obj;
                        Log.d(TAG, "predict Result: " + xyAxis.get(1).toString());
                        if(((Float[]) xyAxis.get(1))[0] > MotionClassifier.PROB_THRESHOLD) {
                            emitAlert();
                        }
                        break;
                    case 5: // 5-change-model-success
                        Toast.makeText(getApplicationContext(), R.string.change_model_success,Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        showConnectedDevice();
        setHandler();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BleManager.getInstance().disconnectAllDevice();
        BleManager.getInstance().destroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_scan:
                //DocumentTool.verifyStoragePermissions(MainActivity.this);
                if (btn_scan.getText().equals(getString(R.string.start_scan))) {
                    checkPermissions();
//                    setScanRule();
//                    startScan();
                } else if (btn_scan.getText().equals(getString(R.string.stop_scan))) {
                    BleManager.getInstance().cancelScan();
                }
                break;

            case R.id.txt_setting:
                if (layout_setting.getVisibility() == View.VISIBLE) {
                    //TODO: 在此处保存设置
                    configureClient();
                    configureEmergency();
                    configurePaths();
                    layout_setting.setVisibility(View.GONE);
                    txt_setting.setText(getString(R.string.expand_search_settings));
                } else {
                    layout_setting.setVisibility(View.VISIBLE);
                    txt_setting.setText(getString(R.string.retrieve_search_settings));
                }
                break;

            case R.id.alert_btn_no:

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.minio_settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.minio_upload:
                uploadSavedData();
                return true;
            case R.id.minio_download:
                downloadModel();
                return true;
            case R.id.change_model:
                File model = new File(prefix + model_dir + '/' + model_name);
                if(model.exists() && model.length() > 0)
                    mMotionClassifier.setModelByPath(prefix + model_dir + '/' + model_name, handler);
                else
                    Toast.makeText(this, R.string.change_model_fail, Toast.LENGTH_SHORT).show();

            default://TODO Menu Settings
               return super.onOptionsItemSelected(item);
        }
    }

    private void configureClient(){
        String user = minio_user.getText().toString();
        if(!TextUtils.isEmpty(user)) client = client.resetAccount(user);
        String password = minio_password.getText().toString();
        if(!TextUtils.isEmpty(password)) client = client.resetSecretKey(password);
        String link = minio_link.getText().toString();
        if(!TextUtils.isEmpty(link)) client = client.resetEndPoint(link);
    }

    private void configureEmergency(){
        String _emergency_num = setting_emergency_num.getText().toString();
        if(!TextUtils.isEmpty(_emergency_num)) emergency_num = _emergency_num;
    }

    private void configurePaths(){
        String _data_dir = setting_data_dir.getText().toString();
        if(!TextUtils.isEmpty(_data_dir)) data_dir = _data_dir;
        String _model_dir = setting_model_dir.getText().toString();
        if(!TextUtils.isEmpty(_model_dir)) model_dir = _model_dir;
        String _model_name = setting_model_name.getText().toString();
        if(!TextUtils.isEmpty(_model_name)) model_name = _model_name;
    }

    private void downloadModel(){
        configureClient();
        Toast.makeText(this, R.string.download_start,Toast.LENGTH_SHORT).show();
        Log.d(TAG,
                "Downloading " + model_name + " to "
                        + prefix + model_dir + "." + client.toString());
        downloadPool.execute(threadFactory.getDownloadRunnable(model_name, prefix + model_dir, client, handler));
        //new Thread(threadFactory.getDownloadRunnable(model_name, prefix + model_dir, client, handler)).start();
    }

    private void uploadSavedData(){
        configureClient();
        Toast.makeText(this, R.string.upload_start,Toast.LENGTH_SHORT).show();
        Log.d(TAG,
                "Uploading " + prefix + data_dir
                        + " ." + client.toString());
        File dir = new File(prefix + data_dir);
        String[] files = dir.list();

        if (files != null) {
            for (String name : files) {

                Log.d(TAG,
                        "Uploading " + prefix + data_dir + '/' + name);
            }
        }
        uploadPool.execute(threadFactory.getUploadRunnable(prefix + data_dir, client, handler));
        //new Thread(threadFactory.getUploadRunnable(prefix + data_dir, client, handler)).start();
    }

    private void initView() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        btn_scan = findViewById(R.id.btn_scan);
        btn_scan.setText(getString(R.string.start_scan));
        btn_scan.setOnClickListener(this);

        et_name = findViewById(R.id.et_name);
        et_mac = findViewById(R.id.et_mac);
        et_uuid = findViewById(R.id.et_uuid);
        sw_auto = findViewById(R.id.sw_auto);

        minio_user = findViewById(R.id.minio_user);
        minio_password = findViewById(R.id.minio_password);
        minio_link = findViewById(R.id.minio_link);

        setting_emergency_num = findViewById(R.id.emergency_num);
        setting_data_dir = findViewById(R.id.data_dir);
        setting_model_dir = findViewById(R.id.model_dir);
        setting_model_name = findViewById(R.id.model_name);

        layout_setting = findViewById(R.id.layout_setting);
        txt_setting = findViewById(R.id.txt_setting);
        txt_setting.setOnClickListener(this);
        layout_setting.setVisibility(View.GONE);
        txt_setting.setText(getString(R.string.expand_search_settings));

        img_loading = findViewById(R.id.img_loading);
        operatingAnim = AnimationUtils.loadAnimation(this, R.anim.rotate);
        operatingAnim.setInterpolator(new LinearInterpolator());
        progressDialog = new ProgressDialog(this);

        mDeviceAdapter = new DeviceAdapter(this);
        mDeviceAdapter.setOnDeviceClickListener(new DeviceAdapter.OnDeviceClickListener() {
            @Override
            public void onConnect(BleDevice bleDevice) {
                if (!BleManager.getInstance().isConnected(bleDevice)) {
                    BleManager.getInstance().cancelScan();
                    connect(bleDevice);
                }
            }

            @Override
            public void onDisConnect(final BleDevice bleDevice) {
                if (BleManager.getInstance().isConnected(bleDevice)) {
                    BleManager.getInstance().disconnect(bleDevice);
                }
            }

            @Override
            public void onDetail(BleDevice bleDevice) {
                if (BleManager.getInstance().isConnected(bleDevice)) {
                    Intent intent = new Intent(MainActivity.this, OperationActivity.class);
                    intent.putExtra(OperationActivity.KEY_DATA, bleDevice);
                    startActivity(intent);
                }
            }

            @Override
            public void onReceiveClicked(final BleDevice bleDevice) {
                BleManager.getInstance().notify(bleDevice, GattAttributes.BLE_UART_SERVICE, GattAttributes.BLE_UART_TX, new BleNotifyCallback() {
                    @Override
                    public void onNotifySuccess() {
                        Toast.makeText(MainActivity.this, bleDevice.getMac() + "Character Notify设置成功", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNotifyFailure(BleException exception) {
                        Toast.makeText(MainActivity.this, bleDevice.getMac() + "Character Notify设置失败", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCharacteristicChanged(byte[] data) {
                        // Parsing data here
                        dataPipeline.execute(threadFactory.getDataPipelineRunnable(mDeviceAdapter,
                                bleDevice,
                                data,
                                data_dir,
                                mMotionClassifier,
                                handler));

                    }
                });
            }
        });
        ListView listView_device = findViewById(R.id.list_device);
        listView_device.setAdapter(mDeviceAdapter);
    }



    private void showConnectedDevice() {
        List<BleDevice> deviceList = BleManager.getInstance().getAllConnectedDevice();
        mDeviceAdapter.clearConnectedDevice();
        for (BleDevice bleDevice : deviceList) {
            mDeviceAdapter.addDevice(bleDevice);
        }
        mDeviceAdapter.notifyDataSetChanged();
    }

    private void setScanRule() {
        String[] uuids;
        String str_uuid = et_uuid.getText().toString();
        if (TextUtils.isEmpty(str_uuid)) {
            uuids = null;
        } else {
            uuids = str_uuid.split(",");
        }

        // APP function specific
        UUID[] serviceUuids = null;

        if (uuids != null && uuids.length > 0) {
            serviceUuids = new UUID[uuids.length];
            for (int i = 0; i < uuids.length; i++) {
                String name = uuids[i];
                String[] components = name.split("-");
                if (components.length != 5) {
                    serviceUuids[i] = null;
                } else {
                    serviceUuids[i] = UUID.fromString(uuids[i]);
                }
            }
        }

        String[] names;
        String str_name = et_name.getText().toString();
        if (TextUtils.isEmpty(str_name)) {
            names = new String[] {"ATK-BLE01"};
        } else {
            names = str_name.split(",");
        }

        String mac = et_mac.getText().toString();

        boolean isAutoConnect = sw_auto.isChecked();

        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
                .setServiceUuids(serviceUuids)      // 只扫描指定的服务的设备，可选
                .setDeviceName(true, names)   // 只扫描指定广播名的设备，可选
                .setDeviceMac(mac)                  // 只扫描指定mac的设备，可选
                .setAutoConnect(isAutoConnect)      // 连接时的autoConnect参数，可选，默认false
                .setScanTimeOut(10000)              // 扫描超时时间，可选，默认10秒
                .build();
        BleManager.getInstance().initScanRule(scanRuleConfig);
    }

    private void startScan() {
        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanStarted(boolean success) {
                mDeviceAdapter.clearScanDevice();
                mDeviceAdapter.notifyDataSetChanged();
                img_loading.startAnimation(operatingAnim);
                img_loading.setVisibility(View.VISIBLE);
                btn_scan.setText(getString(R.string.stop_scan));
            }

            @Override
            public void onLeScan(BleDevice bleDevice) {
                super.onLeScan(bleDevice);
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                mDeviceAdapter.addDevice(bleDevice);
                mDeviceAdapter.notifyDataSetChanged();
            }

            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                img_loading.clearAnimation();
                img_loading.setVisibility(View.INVISIBLE);
                btn_scan.setText(getString(R.string.start_scan));
            }
        });
    }

    private void connect(final BleDevice bleDevice) {
        BleManager.getInstance().connect(bleDevice, new BleGattCallback() {
            @Override
            public void onStartConnect() {
                progressDialog.show();
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                img_loading.clearAnimation();
                img_loading.setVisibility(View.INVISIBLE);
                btn_scan.setText(getString(R.string.start_scan));
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, getString(R.string.connect_fail), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                progressDialog.dismiss();
                mDeviceAdapter.addDevice(bleDevice);
                mDeviceAdapter.notifyDataSetChanged();
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
                progressDialog.dismiss();

                mDeviceAdapter.removeDevice(bleDevice);
                mDeviceAdapter.notifyDataSetChanged();

                if (isActiveDisConnected) {
                    Toast.makeText(MainActivity.this, getString(R.string.active_disconnected), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.disconnected), Toast.LENGTH_LONG).show();
                    ObserverManager.getInstance().notifyObserver(bleDevice);
                }

            }
        });
    }

    private void readRssi(BleDevice bleDevice) {
        BleManager.getInstance().readRssi(bleDevice, new BleRssiCallback() {
            @Override
            public void onRssiFailure(BleException exception) {
                Log.i(TAG, "onRssiFailure" + exception.toString());
            }

            @Override
            public void onRssiSuccess(int rssi) {
                Log.i(TAG, "onRssiSuccess: " + rssi);
            }
        });
    }

    private void setMtu(BleDevice bleDevice, int mtu) {
        BleManager.getInstance().setMtu(bleDevice, mtu, new BleMtuChangedCallback() {
            @Override
            public void onSetMTUFailure(BleException exception) {
                Log.i(TAG, "onsetMTUFailure" + exception.toString());
            }

            @Override
            public void onMtuChanged(int mtu) {
                Log.i(TAG, "onMtuChanged: " + mtu);
            }
        });
    }

    @Override
    public final void onRequestPermissionsResult(int requestCode,
                                                 @NonNull String[] permissions,
                                                 @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE_PERMISSIONS:
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            onPermissionGranted(permissions[i]);
                        }
                    }
                }
                break;
        }
    }

    private void checkPermissions() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, getString(R.string.please_open_blue), Toast.LENGTH_LONG).show();
            return;
        }

        List<String> permissions = new ArrayList<>(Arrays.asList(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CALL_PHONE));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);    //these permissions are only for API >= S (31)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
        }

        List<String> permissionDeniedList = new ArrayList<>();
        for (String permission : permissions) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, permission);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted(permission);
            } else {
                permissionDeniedList.add(permission);
            }
        }
        if (!permissionDeniedList.isEmpty()) {
            String[] deniedPermissions = permissionDeniedList.toArray(new String[permissionDeniedList.size()]);
            ActivityCompat.requestPermissions(this, deniedPermissions, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void onPermissionGranted(String permission) {
        if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission)) {
            if (!checkGPSIsOpen()) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.notifyTitle)
                        .setMessage(R.string.gpsNotifyMsg)
                        .setNegativeButton(R.string.cancel,
                                (dialog, which) -> finish())
                        .setPositiveButton(R.string.setting,
                                (dialog, which) -> {
                                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                    startActivityForResult(intent, REQUEST_CODE_OPEN_GPS);
                                })

                        .setCancelable(false)
                        .show();
            } else {
                setScanRule();
                startScan();
            }
        }
    }

    private boolean checkGPSIsOpen() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
            return false;
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_GPS) {
            if (checkGPSIsOpen()) {
                setScanRule();
                startScan();
            }
        }
    }

}
