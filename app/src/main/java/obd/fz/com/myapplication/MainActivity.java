package obd.fz.com.myapplication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Toast;

import com.miyuan.reset.BuildConfig;
import com.miyuan.reset.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private static final String READ_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private static final String WRITE_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private static final String NOTIFY_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private static final int REQUEST_CODE_OPEN_GPS = 1;
    private static final int REQUEST_CODE_PERMISSION_LOCATION = 2;
    private static final long COMMAND_TIMEOUT = 5000;
    StringBuilder sb = new StringBuilder();
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic readCharacteristic;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private BluetoothGatt mBluetoothGatt;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private TextView statusTV;
    private OkHttpClient okHttpClient;
    private List<String> scanResult = new ArrayList<>();
    private boolean isScaning = false;
    private byte[] result;
    /**
     * 上一包是否接受完成
     */
    private boolean unfinish = true;
    /**
     * 完整包
     */
    private byte[] full;
    private int currentIndex;
    private int count;
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        /**
         *
         * @param device    扫描到的设备
         * @param rssi
         * @param scanRecord
         */
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, final byte[] scanRecord) {
            if (null == device) {
                return;
            }
            final String name = device.getName();
            Log.d("扫描结果 " + name);
            if (name != null && (name.startsWith("Guardian") || name.startsWith("MYobd"))) {
                if (scanResult.contains(name)) {
                    return;
                }

                scanResult.add(name);
                // 自动连接
                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopScan();
                        connect(name, device.getAddress());
                    }
                }, 1000);
            }
        }
    };

    public short byteToShort(byte[] b) {
        short s = 0;
        short s0 = (short) (b[0] & 0xff);// 最低位
        short s1 = (short) (b[1] & 0xff);
        s1 <<= 8;
        s = (short) (s0 | s1);
        return s;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusTV = (TextView) findViewById(R.id.status);

        if (isSupportBle()) {
            bluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(this, "Device does not support Bluetooth", Toast.LENGTH_LONG);
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }

        startScan(); // 自动扫描

        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
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
                startScan();
            }
        }
    }

    private synchronized void startScan() {
        if (null == mBluetoothAdapter || isScaning) {
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
        Log.d("正在扫描...");
        statusTV.setText("正在扫描");

        isScaning = true;

        scanResult.clear();

        mBluetoothAdapter.startLeScan(leScanCallback);
    }

    private synchronized void stopScan() {
        if (null == mBluetoothAdapter || !isScaning) {
            return;
        }
        Log.d("扫描完成...");
        statusTV.setText("扫描完成");
        isScaning = false;
        mBluetoothAdapter.stopLeScan(leScanCallback);
    }

    boolean isSupportBle() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                && this.getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    synchronized void connect(final String name, String address) {

        BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(address);

        if (bluetoothDevice == null) {
            return;
        }

        Log.d("正在连接...");

        statusTV.setText("正在连接");

        mBluetoothGatt = bluetoothDevice.connectGatt(this, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                Log.d("getConnectionState " + status + "   " + newState);

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d("onConnectionStateChange  STATE_CONNECTED");
                        mBluetoothGatt.discoverServices();
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                statusTV.setText("成功连接  \n" + name);
                            }
                        });
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d("onConnectionStateChange  STATE_DISCONNECTED");
                        disconnect(true);
                    }
                } else {
                    disconnect(true);
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                Log.d("onServicesDiscovered  " + status);
                if (status == BluetoothGatt.GATT_SUCCESS) {

                    //拿到该服务 1,通过UUID拿到指定的服务  2,可以拿到该设备上所有服务的集合
                    List<BluetoothGattService> serviceList = mBluetoothGatt.getServices();

                    //可以遍历获得该设备上的服务集合，通过服务可以拿到该服务的UUID，和该服务里的所有属性Characteristic
//                    for (BluetoothGattService service : serviceList) {
//                        Log.d("service UUID  " + service.getUuid());
//                        List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
//                        for (BluetoothGattCharacteristic characteristic : characteristicList) {
//                            Log.d("characteristic  UUID " + characteristic.getUuid());
//                        }
//                    }

                    //2.通过指定的UUID拿到设备中的服务也可使用在发现服务回调中保存的服务
                    BluetoothGattService bluetoothGattService = mBluetoothGatt.getService(UUID.fromString(SERVICE_UUID));
//
                    //3.通过指定的UUID拿到设备中的服务中的characteristic，也可以使用在发现服务回调中通过遍历服务中信息保存的Characteristic
                    writeCharacteristic = bluetoothGattService.getCharacteristic(UUID.fromString(WRITE_UUID));
//
                    readCharacteristic = bluetoothGattService.getCharacteristic(UUID.fromString(NOTIFY_UUID));

                    mBluetoothGatt.setCharacteristicNotification(readCharacteristic, true);

                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            sb = new StringBuilder();
                            statusTV.setText("开始解绑");
                        }
                    });

                    getBoxID(true);
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                Log.d("OBD->APP  " + HexUtils.byte2HexStr(characteristic.getValue()));
                analyzeProtocol(characteristic.getValue());
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                Log.d("onCharacteristicRead  ");
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("onCharacteristicRead  success " + Arrays.toString(characteristic.getValue()));
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
            }
        });
    }

    private void getBoxID(boolean reset) {
        byte[] result = new byte[13];
        result[0] = 0x7e;
        result[1] = (byte) 0x80;
        result[2] = 01;
        byte[] bytes = HexUtils.longToByte(System.currentTimeMillis());
        result[3] = bytes[0];
        int cr = result[1] ^ result[2] ^ result[3];
        for (int i = 1; i < bytes.length; i++) {
            result[3 + i] = bytes[i];
            cr = cr ^ bytes[i];
        }
        result[11] = (byte) cr;
        result[12] = 0x7e;
        Log.d("APP->OBD " + HexUtils.byte2HexStr(result));
        if (mBluetoothGatt == null) {
            return;
        }
        writeCharacteristic.setValue(result);
        mBluetoothGatt.writeCharacteristic(writeCharacteristic);
    }


    private void reset() {
        byte[] result = new byte[6];
        result[0] = 0x7e;
        result[1] = (byte) 0x86;
        result[2] = 0x11;
        result[3] = 0;
        result[4] = (byte) (result[1] ^ result[2] ^ result[3]);
        result[5] = 0x7e;
        Log.d("APP->OBD " + HexUtils.byte2HexStr(result));
        if (mBluetoothGatt == null) {
            return;
        }
        writeCharacteristic.setValue(result);
        mBluetoothGatt.writeCharacteristic(writeCharacteristic);
    }

    /**
     * 接受数据
     *
     * @param data
     */
    public synchronized void analyzeProtocol(byte[] data) {
        Log.d(" analyzeProtocol");
        if (null != data && data.length > 0) {
            if (data[0] == 0x7e && data.length != 1 && unfinish && data.length >= 7) {
                // 获取包长度
                byte[] len = new byte[]{data[4], data[3]};
                count = byteToShort(len);
                if (data.length == count + 7) {  //为完整一包
                    full = new byte[count + 5];
                    System.arraycopy(data, 1, full, 0, full.length);
                    validateAndNotify(full);
                } else if (data.length < count + 7) {
                    unfinish = false;
                    full = new byte[count + 5];
                    currentIndex = data.length - 1;
                    System.arraycopy(data, 1, full, 0, data.length - 1);
                } else if (data.length > count + 7) {
                    Log.d(" analyzeProtocol error one ");
                    currentIndex = 0;
                    unfinish = true;
                    full = new byte[]{};
                    return;
                }
            } else {
                if ((currentIndex + data.length - 1) == count + 5) { // 最后一包
                    unfinish = true;
                    System.arraycopy(data, 0, full, currentIndex, data.length - 1);
                    validateAndNotify(full);
                } else if ((currentIndex + data.length - 1) < count + 5) { // 包不完整
                    // 未完成
                    System.arraycopy(data, 0, full, currentIndex, data.length);
                    currentIndex += data.length;
                } else {
                    Log.d(" analyzeProtocol error two ");
                    currentIndex = 0;
                    unfinish = true;
                    full = new byte[]{};
                    return;
                }
            }
        }
    }


    private void validateAndNotify(byte[] res) {
        byte[] result = new byte[res.length];
        System.arraycopy(res, 0, result, 0, res.length);

        int cr = result[0];
        for (int i = 1; i < result.length - 1; i++) {
            cr = cr ^ result[i];
        }
        if (cr != result[result.length - 1]) {
            result = null;
        } else {
            byte[] content = new byte[result.length - 1];
            System.arraycopy(result, 0, content, 0, content.length); // 去掉校验码
            Log.d("content  " + HexUtils.formatHexString(content));
            if (content[0] == 0x00) {
                if (content[1] == 0x02 || content[1] == 0x01) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusTV.setText("正在解绑盒子");
                        }
                    });
                    upbindBoxID(HexUtils.formatHexString(Arrays.copyOfRange(content, 12, 24)), new String(Arrays.copyOfRange(content, 24, 43)));
                } else {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusTV.setText("测试异常 请联系开发者!");
                        }
                    });
                }
            } else if (content[0] == 0x06) {
                if (content[1] == 0x11) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusTV.setText("恭喜！解绑成功，\n 继续解绑请更换OBD，程序将自动开始解绑");
                        }
                    });
                }
            }
        }
    }

    private void upbindBoxID(String boxId, String sn) {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("serialNumber", sn);
            jsonObject.put("boxId", boxId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d("upbindBoxID input " + jsonObject.toString());

        RequestBody requestBody = new FormBody.Builder()
                .add("params", GlobalUtil.encrypt(jsonObject.toString())).build();
        Request request = new Request.Builder()
                .url(BuildConfig.HOST + "service/box/clear")
//                .url("http://tpms.1668288.com/service/box/clear")
                .addHeader("content-type", "application/json;charset:utf-8")
                .post(requestBody)
                .build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("upbindBoxID failure " + e.getMessage());
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusTV.setText("网络异常！请开启网络，退出APP后重新测试");
                    }
                });

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responese = response.body().string();
                Log.d("upbindBoxID success " + responese);
                try {
                    final JSONObject result = new JSONObject(responese);
                    if ("000".equals(result.optString("status"))) {
                        reset();
                    } else {
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                statusTV.setText(result.optString("message") + "，\n 继续解绑请更换OBD，程序将自动开始解绑");
                            }
                        });
                    }
                } catch (JSONException e) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusTV.setText("数据异常！请联系开发人员!");
                        }
                    });
                }
            }
        });
    }

    /**
     * 断开链接
     */
    private synchronized void disconnect(final boolean complete) {
        if (mBluetoothGatt == null) {
            return;
        }

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d("断开连接...");

                statusTV.setText("断开连接");

                mBluetoothGatt.disconnect();
                try {
                    final Method refresh = BluetoothGatt.class.getMethod("refresh");
                    if (refresh != null && mBluetoothGatt != null) {
                        refresh.invoke(mBluetoothGatt);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mBluetoothGatt.close();
                mBluetoothGatt = null;

                if (complete) {
                    startScan();
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        System.gc();
        System.exit(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        leScanCallback = null;
        disconnect(false);
    }

}
