/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.bluetooth.le;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.CharConversionException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.bluetooth.le.BluetoothLeClass.OnDataAvailableListener;
import com.example.bluetooth.le.BluetoothLeClass.OnServiceDiscoverListener;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {
    private final static String TAG = DeviceScanActivity.class.getSimpleName();
    private final static String UUID_KEY_DATA = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private final static String UUID_TEST = "00002a00-0000-1000-8000-00805f9b34fb";
    private final static String SERVICE_UUID_TEST = "00001800-0000-1000-8000-00805f9b34fb";

    private final static String SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb";
    private final static String CHARACTERISTIC_UUID_POWER = "0000fff6-0000-1000-8000-00805f9b34fb";
    private final static String CHARACTERISTIC_UUID_WRITE = "0000fff1-0000-1000-8000-00805f9b34fb";

    private LeDeviceListAdapter mLeDeviceListAdapter;
    /** 搜索BLE终端 */
    private BluetoothAdapter mBluetoothAdapter;
    /** 读写BLE终端 */
    private BluetoothLeClass mBLE;
    private boolean mScanning;
    private Handler mHandler;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	Log.i(TAG, "----------------onCreate----------------------");
	Log.i(TAG, "simpleName---->" + DeviceScanActivity.class.getSimpleName());
	getActionBar().setTitle(R.string.title_devices);
	mHandler = new Handler();
	// Use this check to determine whether BLE is supported on the device.
	// Then you can
	// selectively disable BLE-related features.
	if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
	    Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
	    finish();
	}

	// Initializes a Bluetooth adapter. For API level 18 and above, get a
	// reference to
	// BluetoothAdapter through BluetoothManager.
	final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
	mBluetoothAdapter = bluetoothManager.getAdapter();

	// Checks if Bluetooth is supported on the device.
	if (mBluetoothAdapter == null) {
	    Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
	    finish();
	    return;
	}
	// 开启蓝牙
	mBluetoothAdapter.enable();

	mBLE = new BluetoothLeClass(this);
	if (!mBLE.initialize()) {
	    Log.e(TAG, "Unable to initialize Bluetooth");
	    finish();
	}
	// 发现BLE终端的Service时回调
	mBLE.setOnServiceDiscoverListener(mOnServiceDiscover);
	// 收到BLE终端数据交互的事件
	mBLE.setOnDataAvailableListener(mOnDataAvailable);
    }

    @Override
    protected void onResume() {
	super.onResume();
	// Initializes list view adapter.
	mLeDeviceListAdapter = new LeDeviceListAdapter(this);
	setListAdapter(mLeDeviceListAdapter);
	scanLeDevice(true);
    }

    @Override
    protected void onPause() {
	super.onPause();
	scanLeDevice(false);
	mLeDeviceListAdapter.clear();
	mBLE.disconnect();
    }

    @Override
    protected void onStop() {
	super.onStop();
	mBLE.close();
    }

    private int clickCount = 0;

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
	final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
	if (device == null)
	    return;
	if (mScanning) {
	    mBluetoothAdapter.stopLeScan(mLeScanCallback);
	    mScanning = false;
	}
	clickCount++;
	if (clickCount == 1)
	    mBLE.connect(device.getAddress());
	else {
	    changeValueTest();
	}
    }

    private void changeValueTest() {
	BluetoothGattService gattService = mBLE.mBluetoothGatt.getService(UUID.fromString(SERVICE_UUID));
	int type = gattService.getType();
	Log.e(TAG, "----------------------------------------------------------");
	Log.e(TAG, "-->service type:" + Utils.getServiceType(type));
	Log.e(TAG, "-->includedServices size:" + gattService.getIncludedServices().size());
	Log.e(TAG, "-->service uuid:" + gattService.getUuid());
	Log.e(TAG, "----------------------------------------------------------");

	final BluetoothGattCharacteristic characteristic1 = gattService.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID_WRITE));
	Log.e(TAG, "char uuid---->" + characteristic1.getUuid());

	int permission = characteristic1.getPermissions();
	Log.e(TAG, "char permission---->" + Utils.getCharPermission(permission));

	int property = characteristic1.getProperties();
	Log.e(TAG, "char property---->" + Utils.getCharPropertie(property));
	Log.e(TAG, "char value---->" + byteArrayToString(characteristic1.getValue()));

	mBLE.setCharacteristicNotification(characteristic1, true);
	Log.e(TAG, "setValue---->" + clickCount);
	byte[] data = { (byte) clickCount };
	characteristic1.setValue(data);
	mBLE.writeCharacteristic(characteristic1);

	// mHandler.postDelayed(new Runnable() {
	// @Override
	// public void run() {
	// Log.e(TAG, "try to read---->");
	// mBLE.readCharacteristic(characteristic1);
	// }
	// }, 1000);

	// mBLE.setCharacteristicNotification(characteristic6, true);
	// final BluetoothGattCharacteristic characteristic6 =
	// gattService.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID_POWER));
	// mBLE.mBluetoothGatt.setCharacteristicNotification(characteristic6,
	// true);
	// mBLE.readCharacteristic(characteristic6);
    }

    private void scanLeDevice(final boolean enable) {
	if (enable) {
	    // Stops scanning after a pre-defined scan period.
	    mHandler.postDelayed(new Runnable() {
		@Override
		public void run() {
		    mScanning = false;
		    mBluetoothAdapter.stopLeScan(mLeScanCallback);
		    invalidateOptionsMenu();
		}
	    }, SCAN_PERIOD);

	    mScanning = true;
	    mBluetoothAdapter.startLeScan(mLeScanCallback);
	    // UUID[] uuids={UUID.fromString(SERVICE_UUID_TEST)};
	    // mBluetoothAdapter.startLeScan(uuids, mLeScanCallback);
	} else {
	    mScanning = false;
	    mBluetoothAdapter.stopLeScan(mLeScanCallback);
	}
	invalidateOptionsMenu();
    }

    private String byteArrayToString(byte[] data) {
	if (data != null && data.length > 0) {
	    return new String(data);
	}
	return null;
    }

    /**
     * 搜索到BLE终端服务的事件
     */
    private BluetoothLeClass.OnServiceDiscoverListener mOnServiceDiscover = new OnServiceDiscoverListener() {

	@Override
	public void onServiceDiscover(BluetoothGatt gatt) {
	    Log.e(TAG, "-----------------onServiceDiscoved----------------------");
//	    BluetoothGattService gattService = mBLE.mBluetoothGatt.getService(UUID.fromString(SERVICE_UUID));
//	    final BluetoothGattCharacteristic characteristic6 = gattService.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID_POWER));
//	    mBLE.mBluetoothGatt.setCharacteristicNotification(characteristic6, true);
//	    mBLE.readCharacteristic(characteristic6);
//	    Log.e(TAG, "setCharacteristicNotification----->" + characteristic6.getUuid().toString());
	    // displayGattServices(mBLE.getSupportedGattServices());
	     displayGattServices(gatt.getServices());
	    // BluetoothGattService gattService =
	    // gatt.getService(UUID.fromString(SERVICE_UUID_TEST));
	    // int type = gattService.getType();
	    // Log.e(TAG,
	    // "----------------------------------------------------------");
	    // Log.e(TAG, "-->service type:" + Utils.getServiceType(type));
	    // Log.e(TAG, "-->includedServices size:" +
	    // gattService.getIncludedServices().size());
	    // Log.e(TAG, "-->service uuid:" + gattService.getUuid());
	    // Log.e(TAG,
	    // "----------------------------------------------------------");
	    //
	    // final BluetoothGattCharacteristic characteristic =
	    // gattService.getCharacteristic(UUID.fromString(UUID_TEST));
	    // Log.e(TAG, "char uuid---->" + characteristic.getUuid());
	    //
	    // int permission = characteristic.getPermissions();
	    // Log.e(TAG, "char permission---->" +
	    // Utils.getCharPermission(permission));
	    //
	    // int property = characteristic.getProperties();
	    // Log.e(TAG, "char property---->" +
	    // Utils.getCharPropertie(property));
	    //
	    // Log.e(TAG, "char value---->" +
	    // byteArrayToString(characteristic.getValue()));
	    //
	    // mHandler.postDelayed(new Runnable() {
	    // @Override
	    // public void run() {
	    // mBLE.readCharacteristic(characteristic);
	    // }
	    // }, 500);
	    // mBLE.setCharacteristicNotification(characteristic, true);
	    // characteristic.setValue("test data"+clickCount);
	    // mBLE.writeCharacteristic(characteristic);
	}

    };

    public int byteArrayToInt(byte[] b, int offset) {
	int value = 0;
	for (int i = 0; i < 4; i++) {
	    value |= b[i];
	    value = value << 8;
	}
	return value;
    }

    /**
     * 收到BLE终端数据交互的事件
     */
    private BluetoothLeClass.OnDataAvailableListener mOnDataAvailable = new OnDataAvailableListener() {

	/**
	 * BLE终端数据被读的事件
	 */
	@Override
	public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
	    Log.e(TAG, "-----------------onCharacteristicRead  scanactivity----------------------" + status);
	    if (status == BluetoothGatt.GATT_SUCCESS) {
		Log.e(TAG, "onCharRead " + "  device nama: " + gatt.getDevice().getName() + "  char uuid: "
			+ characteristic.getUuid().toString() + "  value: " + characteristic.getValue()[0]);
	    }
	}

	/**
	 * 收到BLE终端写入数据回调
	 */
	@Override
	public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
	    Log.e(TAG, "status--->" + status + " onCharWrite " + "  device nama: " + gatt.getDevice().getName() + "  char uuid: "
		    + characteristic.getUuid().toString() + "  value: " + characteristic.getValue()[0]);
	}

	public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
	    Log.e(TAG,
		    " onCharacteristicChanged " + "  char uuid: " + characteristic.getUuid().toString() + "  value: "
			    + characteristic.getValue()[0]);
	};
    };

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

	@Override
	public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
	    runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    mLeDeviceListAdapter.addDevice(device);
		    Log.i(TAG, "device---->" + device);
		    mLeDeviceListAdapter.notifyDataSetChanged();
		}
	    });
	}
    };

    private void displayGattServices(List<BluetoothGattService> gattServices) {
	if (gattServices == null)
	    return;

	for (BluetoothGattService gattService : gattServices) {
	    // -----Service的字段信息-----//
//	    if (!gattService.getUuid().equals(UUID.fromString(SERVICE_UUID))) {
//		return;
//	    }
	    int type = gattService.getType();
	    Log.e(TAG, "----------------------------------------------------------");
	    Log.e(TAG, "-->service type:" + Utils.getServiceType(type));
	    Log.e(TAG, "-->includedServices size:" + gattService.getIncludedServices().size());
	    Log.e(TAG, "-->service uuid:" + gattService.getUuid());
	    Log.e(TAG, "----------------------------------------------------------");

	    // -----Characteristics的字段信息-----//
	    List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
	    for (final BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
		Log.e(TAG, "char uuid---->" + gattCharacteristic.getUuid());

		int permission = gattCharacteristic.getPermissions();
		Log.e(TAG, "char permission---->" + Utils.getCharPermission(permission));

		int property = gattCharacteristic.getProperties();
		Log.e(TAG, "char property---->" + Utils.getCharPropertie(property));

		byte[] data = gattCharacteristic.getValue();
		if (data != null && data.length > 0) {
		    Log.e(TAG, "char value---->" + new String(data));
		}

		if (gattCharacteristic.getUuid().toString().equals(UUID_TEST)) {
		    Log.e(TAG, "Find the specific characteristic");
		    // 测试读取当前Characteristic数据，会触发mOnDataAvailable.onCharacteristicRead()
		    mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
			    mBLE.readCharacteristic(gattCharacteristic);
			}
		    }, 500);
		    // 接受Characteristic被写的通知,收到蓝牙模块的数据后会触发mOnDataAvailable.onCharacteristicWrite()
		    mBLE.setCharacteristicNotification(gattCharacteristic, true);
		    // 设置数据内容
		    gattCharacteristic.setValue("test data");
		    // 往蓝牙模块写入数据
		    mBLE.writeCharacteristic(gattCharacteristic);
		}

		// -----Descriptors的字段信息-----//
		// List<BluetoothGattDescriptor> gattDescriptors =
		// gattCharacteristic
		// .getDescriptors();
		// for (BluetoothGattDescriptor gattDescriptor :
		// gattDescriptors) {
		// Log.e(TAG, "desc uuid---->" + gattDescriptor.getUuid());
		// int descPermission = gattDescriptor.getPermissions();
		// Log.e(TAG,
		// "desc permission---->"
		// + Utils.getDescPermission(descPermission));
		//
		// byte[] desData = gattDescriptor.getValue();
		// if (desData != null && desData.length > 0) {
		// Log.e(TAG, "desc value---->" + new String(desData));
		// }
		// }
	    }
	}//

    }
}