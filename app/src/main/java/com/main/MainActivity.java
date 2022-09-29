package com.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

import com.bluetoothlegatt.BleUartDataReceiver;
import com.bluetoothlegatt.EchartOptionUtil;
import com.bluetoothlegatt.EchartView;
import com.bluetoothlegatt.MotionClassifier;
import com.bluetoothlegatt.SampleGattAttributes;
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

import com.main.operation.OperationActivity;
import com.minio.minio_android.MinioUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


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
    private EditText minio_user, minio_password, minio_link;
    private Switch sw_auto;
    private ImageView img_loading;

    RemoteViews notificationLayout = new RemoteViews(getPackageName(), R.layout.notification_layout);
    NotificationManager alertManager;

    private MinioUtils client = new MinioUtils();
    private MotionClassifier mMotionClassifier;

    private static final int LENGTH = 50; //length of data shown
    private EchartView mLineChart;
    boolean mEnableRefresh = false;
    int mCounter = 0; //counting predicted outputs
    Integer[] x = new Integer[LENGTH];
    Float[] y = new Float[LENGTH];

    private final String data_dir = Environment.getExternalStorageDirectory().getPath() + "/Download/bleReceived";
    private String model_dir = Environment.getExternalStorageDirectory().getPath() + "/Download/models";

    private Animation operatingAnim;
    private DeviceAdapter mDeviceAdapter;
    private ProgressDialog progressDialog;

    public MainActivity() throws IOException {
    }

    // Alert notification settings
    private void emitAlert(){

//        Notification alertNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setSmallIcon(R.mipmap.ic_launcher)
//                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
//                .setCustomContentView(notificationLayout)
//                .setCustomBigContentView(notificationLayout)
//                .setAutoCancel(true)
//                .build();
        // Create an explicit intent for an Activity in your app
        NotificationCompat.Action action =
                new NotificationCompat.Action.Builder(R.drawable.ic_reply_icon,
                        getString(R.string.label), replyPendingIntent)
                        .addRemoteInput(remoteInput)
                        .build();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        Intent snoozeIntent = new Intent(this, MyBroadcastReceiver.class);
        snoozeIntent.setAction(ACTION_SNOOZE);
        snoozeIntent.putExtra(EXTRA_NOTIFICATION_ID, 0);
        PendingIntent snoozePendingIntent =
                PendingIntent.getBroadcast(this, 0, snoozeIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("My notification")
                .setContentText("Hello World!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_snooze, getString(R.string.snooze),
                        snoozePendingIntent);

        alertManager.notify(NOTIFICATION_ID, builder.build());
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
        alertManager = getSystemService(NotificationManager.class);
        alertManager.createNotificationChannel(channel);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        DocumentTool.verifyStoragePermissions(MainActivity.this);

        createNotificationChannel();

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
        DocumentTool.addFolder(data_dir);
        DocumentTool.addFolder(model_dir);

        mMotionClassifier = new MotionClassifier(this);

        Arrays.fill(x, 0);
        Arrays.fill(y,0f);

        mLineChart = findViewById(R.id.data_chart);
        mLineChart.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                //refreshLineChart();
                //最好在h5页面加载完毕后再加载数据，防止html的标签还未加载完成，不能正常显示
                mEnableRefresh = true;
            }
        });
    }

    private void refreshLineChart(Object[] x, Object[] y){
//        Object[] x = new Object[]{
//                "1", "2", "3", "4", "5", "6", "7"
//        };
//        Object[] y = new Object[]{
//                820, 932, 901, 934, 1290, 1330, 1320
//        };
        if(mEnableRefresh){
            mLineChart.refreshEchartsWithOption(EchartOptionUtil.getLineChartOptions(x, y));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        showConnectedDevice();
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
                if (btn_scan.getText().equals(getString(R.string.start_scan))) {
                    checkPermissions();
                } else if (btn_scan.getText().equals(getString(R.string.stop_scan))) {
                    BleManager.getInstance().cancelScan();
                }
                break;

            case R.id.txt_setting:
                if (layout_setting.getVisibility() == View.VISIBLE) {
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
            default://TODO Settings
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

    private void downloadModel(){
        Toast.makeText(this, "Download starting...",Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            configureClient();
            client.download("new_model.tflite",model_dir + "/new_model.tflite");
            Toast.makeText(getApplicationContext(), "Download Success!",Toast.LENGTH_SHORT).show();
        }).start();
    }

    private void uploadSavedData(){
        File dir = new File(data_dir);
        String[] files = dir.list();

        Toast.makeText(this, "Upload starting...",Toast.LENGTH_SHORT).show();

        if (files != null)
        for (String name : files) {
            new Thread(() -> {
                configureClient();
                client.upload(Paths.get(data_dir,name).toAbsolutePath().toString(), name);
                Toast.makeText(getApplicationContext(), "Upload Success!",Toast.LENGTH_SHORT).show();
            }).start();
        }
    }

    private void initView() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        btn_scan = (Button) findViewById(R.id.btn_scan);
        btn_scan.setText(getString(R.string.start_scan));
        btn_scan.setOnClickListener(this);

        et_name = (EditText) findViewById(R.id.et_name);
        et_mac = (EditText) findViewById(R.id.et_mac);
        et_uuid = (EditText) findViewById(R.id.et_uuid);
        sw_auto = (Switch) findViewById(R.id.sw_auto);

        minio_user = (EditText) findViewById(R.id.minio_user);
        minio_password = (EditText) findViewById(R.id.minio_password);
        minio_link = (EditText) findViewById(R.id.minio_link);

        layout_setting = (LinearLayout) findViewById(R.id.layout_setting);
        txt_setting = (TextView) findViewById(R.id.txt_setting);
        txt_setting.setOnClickListener(this);
        layout_setting.setVisibility(View.GONE);
        txt_setting.setText(getString(R.string.expand_search_settings));

        img_loading = (ImageView) findViewById(R.id.img_loading);
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
                DocumentTool.verifyStoragePermissions(MainActivity.this);

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
                        BleUartDataReceiver data_parser = mDeviceAdapter.getParser(bleDevice.getKey());
                        data_parser.receiveData(data);
                        data_parser.parseData();
                        data_parser.setCb(parsed_data -> new Thread(()-> {

                        //DocumentTool.addFile(bleDevice.getMac().replace(':', '_')+".txt");
                        DocumentTool.appendFileData(data_dir + "/" + bleDevice.getMac().replace(':', '_')+".txt", parsed_data.toString().getBytes(StandardCharsets.UTF_8));

                        // predict & show on echarts here
                        if(mCounter == LENGTH) {
                            x = Arrays.copyOfRange(x, 1, LENGTH);
                            x = Arrays.copyOf(x, LENGTH);
                            x[LENGTH - 1] = parsed_data.timeStamp;

                            y = Arrays.copyOfRange(y, 1, LENGTH);
                            y = Arrays.copyOf(y, LENGTH);
                            y[LENGTH - 1] = mMotionClassifier.classifyMotion(parsed_data.toFloatList())[0];//data.press_ao
                        } else {
                            x[mCounter] = parsed_data.timeStamp;
                            y[mCounter] = mMotionClassifier.classifyMotion(parsed_data.toFloatList())[0]; //TODO: buffer & classify
                            mCounter++;
                        }

                        if(mEnableRefresh && x[0] != null && y[0] != null) {
                            Integer[] finalX = x;
                            Float[] finalY = y;
                            new Thread(() -> {refreshLineChart(finalX, finalY);}).start();
                        }

                        }).start());
                    }
                });

            }


        });
        ListView listView_device = (ListView) findViewById(R.id.list_device);
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
                Manifest.permission.WRITE_EXTERNAL_STORAGE));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);    //this permission is only for API >= S (31)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else { permissions.add(Manifest.permission.ACCESS_FINE_LOCATION); }

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
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                })
                        .setPositiveButton(R.string.setting,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                        startActivityForResult(intent, REQUEST_CODE_OPEN_GPS);
                                    }
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
