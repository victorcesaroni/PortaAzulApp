package br.edu.puc_campinas.projetointegrado2018.portaazul;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.app.LoaderManager.LoaderCallbacks;

import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.content.Intent;

import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.widget.Toast;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import android.content.Context;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class LoginActivity extends AppCompatActivity {

    private LoginActivity context = null;

    private static final int REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static final int REQUEST_ACCESS_FINE_LOCATION = 2;

    // UI references.
    private AutoCompleteTextView mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;
    private Spinner mDevicesView;
    private Button mEmailSignInButton;
    private Button mScanButton;
    private Button mConnectButton;

    private boolean scan = false;
    // http://www.argenox.com/blog/android-5-0-lollipop-brings-ble-improvements/
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private HashSet<BluetoothDevice> devicesFound = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;

        checkBluetoothPermissions();

        setContentView(R.layout.activity_login);

        mEmailView = (AutoCompleteTextView)findViewById(R.id.user);
        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
        mDevicesView = (Spinner)findViewById(R.id.devices_spinner);
        mPasswordView = (EditText)findViewById(R.id.password);
        mEmailSignInButton = (Button)findViewById(R.id.email_sign_in_button);
        mScanButton = (Button)findViewById(R.id.scan_button);
        mConnectButton = (Button)findViewById(R.id.connect_button);

        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mScanButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                scan = !scan;
                scanLeDevice(scan);
                mScanButton.setText( scan ? "PARAR" : "ESCANEAR");
            }
        });

        mConnectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (devicesFound.size() == 0)
                    return;

                int idx = 0;
                for (BluetoothDevice dev : devicesFound) {
                    if (idx == mDevicesView.getSelectedItemPosition()) {
                        connectToDevice(dev);
                        break;
                    }
                    idx++;
                }
            }
        });

        // BLE
        mHandler = new Handler();

        /* Check to make sure BLE is supported */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        /* Get a Bluetooth Adapter Object */
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mGatt = null;

        ScanSettings.Builder builderScanSettings = new ScanSettings.Builder();
        builderScanSettings.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
        builderScanSettings.setReportDelay(0);

        settings = builderScanSettings.build();
    }

    private void attemptLogin() {
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        if (mGatt != null) {
            BluetoothGattService service = mGatt.getService(UUID.fromString("ab0828b1-198e-4351-b779-901fa0e0371e"));
            if (service != null) {
                BluetoothGattCharacteristic charac = service.getCharacteristic(UUID.fromString("03d5b556-7940-4692-bc80-d5027539b024"));
                if (charac != null) {
                    charac.setValue("name=" + email + "&token=" + password);
                    if (mGatt.writeCharacteristic(charac)) {
                        Log.i("attemptLogin", "OK");
                    } else {
                        Log.e("attemptLogin", "FAIL");
                    }
                } else {
                    Log.e("attemptLogin", "charac = null");
                }
            } else {
                Log.e("attemptLogin", "service = null");
            }
        } else {
            Log.e("attemptLogin", "mGatt = null");
        }
    }

    private void checkBluetoothPermissions() {
        if (checkSelfPermission(ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        requestPermissions(new String[]{ACCESS_COARSE_LOCATION}, REQUEST_ACCESS_COARSE_LOCATION);
        requestPermissions(new String[]{ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkBluetoothPermissions();
            }
        }
        if (requestCode == REQUEST_ACCESS_COARSE_LOCATION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkBluetoothPermissions();
            }
        }
    }

    private void updateDeviceListView() {
        ArrayList<String> devicesName = new ArrayList<>();
        for (BluetoothDevice dev : devicesFound) {
            devicesName.add(dev.getName());
        }

        ArrayAdapter<String> adp = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, devicesName);
        adp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDevicesView.setAdapter(adp);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            /*// para de escanear depois de SCAN_PERIOD
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mLEScanner.stopScan(mScanCallback);
                }
            }, SCAN_PERIOD);*/

            devicesFound.clear();
            mLEScanner.startScan(filters, settings, mScanCallback);
        } else {
            mLEScanner.stopScan(mScanCallback);
            updateDeviceListView();
        }
    }

    public void connectToDevice(final BluetoothDevice device) {
        //if (mGatt != null)
         //   mGatt.disconnect();

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (mGatt != null)
                    mGatt.disconnect();

                mGatt = device.connectGatt(getApplicationContext(), false, mainGattCallback);
                scanLeDevice(false);
            }
        });
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        void onDeviceFound(ScanResult scanResult) {
            BluetoothDevice device = scanResult.getDevice();
            Log.i("onDeviceFound", "Device found: " + device.getName() + "  " + device.getAddress() + " rssi:" + scanResult.getRssi());
            devicesFound.add(device);
            updateDeviceListView();
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            /* Connect to device found */
            Log.i("onScanResult", "callbackType: " + String.valueOf(callbackType));
            Log.i("Scan Item: ", result.toString());
            onDeviceFound(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            /* Process a batch scan results */
            for (ScanResult sr : results) {
                Log.i("Scan Item: ", sr.toString());
                onDeviceFound(sr);
            }
        }
    };

    private BluetoothGattCallback mainGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange","status=>"+status+" newState=>"+newState);

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("mainGattCallback", "CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("mainGattCallback", "DISCONNECTED");
                    break;
                default:
                    Log.e("mainGattCallback", "STATE_OTHER");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            Log.i("onServicesDiscovered", services.toString());

            for (BluetoothGattService s : services) {
                Log.i("onServicesDiscovered", "UUID - " + s.getUuid());
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    Log.i("onServicesDiscovered", "CHARACTERISTIC_UUID - " + c.getUuid());
                }
            }
            //gatt.readCharacteristic(services.get(1).getCharacteristics().get(0));
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // data available
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.i("onCharacteristicChanged", characteristic.toString());
        }
    };
}
