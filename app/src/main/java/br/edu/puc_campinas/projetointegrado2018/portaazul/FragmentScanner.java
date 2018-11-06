package br.edu.puc_campinas.projetointegrado2018.portaazul;


import android.app.Activity;
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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;


/**
 * A simple {@link Fragment} subclass.
 */
public class FragmentScanner extends Fragment {

    public FragmentScanner() {
        // Required empty public constructor
    }

    private Button mOpenButton;
    private Spinner mDevicesSpinner;
    private Switch mDevicesScan;

    private static final int REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static final int REQUEST_ACCESS_FINE_LOCATION = 2;

    private BluetoothAdapter mBluetoothAdapter= null;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner = null;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private HashSet<BluetoothDevice> devicesFound = new HashSet<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_scanner, container, false);

        checkBluetoothPermissions();

        mDevicesSpinner = (Spinner)view.findViewById(R.id.spinner_devices_found);
        mDevicesScan = (Switch)view.findViewById(R.id.switch_device_scan);
        mOpenButton = (Button) view.findViewById(R.id.button_open);

        mDevicesScan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                scanLeDevice(isChecked);
            }
        });

        mOpenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (devicesFound.size() == 0)
                    return;

                int idx = 0;
                for (BluetoothDevice dev : devicesFound) {
                    if (idx == mDevicesSpinner.getSelectedItemPosition()) {
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
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(getActivity(), "BLE Not Supported", Toast.LENGTH_SHORT).show();
            return view;
            //finish();
        }
        /* Get a Bluetooth Adapter Object */
        final BluetoothManager bluetoothManager =
                (BluetoothManager)getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(getActivity(), "BLE Not Supported", Toast.LENGTH_SHORT).show();
            return view;
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Toast.makeText(getActivity(), "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
                return view;
            }
        }

        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mGatt = null;

        ScanSettings.Builder builderScanSettings = new ScanSettings.Builder();
        builderScanSettings.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
        builderScanSettings.setReportDelay(0);

        settings = builderScanSettings.build();

        setRetainInstance(true);

        return view;
    }

    private void attemptLogin() {
        SharedPreferences settings = getActivity().getSharedPreferences(FragmentLogin.PREFS_NAME, 0);
        String user = settings.getString("user", "");
        String token = settings.getString("token", "");

        if (mGatt != null) {
            BluetoothGattService service = mGatt.getService(UUID.fromString("ab0828b1-198e-4351-b779-901fa0e0371e"));
            if (service != null) {
                BluetoothGattCharacteristic charac = service.getCharacteristic(UUID.fromString("03d5b556-7940-4692-bc80-d5027539b024"));
                if (charac != null) {
                    charac.setValue("name=" + user + "&token=" + token);
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
        Activity activity = getActivity();

        if (activity.checkSelfPermission(ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                activity.checkSelfPermission(ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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

        ArrayAdapter<String> adp = new ArrayAdapter<>(getActivity().getApplicationContext(), android.R.layout.simple_spinner_item, devicesName);
        adp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDevicesSpinner.setAdapter(adp);
    }

    private void scanLeDevice(final boolean enable) {
        if (mLEScanner != null && enable) {
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
            if (mLEScanner != null) {
                mLEScanner.stopScan(mScanCallback);
                updateDeviceListView();
            }
            mDevicesScan.setChecked(false);
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

                mGatt = device.connectGatt(getActivity().getApplicationContext(), false, mainGattCallback);
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

            attemptLogin();
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
