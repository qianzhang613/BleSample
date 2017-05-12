package com.android.bleserver;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGatt;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import android.net.Uri;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Manages BLE Advertising independent of the main app.
 * If the app goes off screen (or gets killed completely) advertising can continue because this
 * Service is maintaining the necessary Callback in memory.
 */
public class AdvertiserService extends Service {

    private static final String TAG = AdvertiserService.class.getSimpleName();

    /**
     * A global variable to let AdvertiserFragment check if the Service is running without needing
     * to start or bind to it.
     * This is the best practice method as defined here:
     * https://groups.google.com/forum/#!topic/android-developers/jEvXMWgbgzE
     */
    public static boolean running = false;

    public static final String ADVERTISING_FAILED =
            "com.example.android.bluetoothadvertisements.advertising_failed";

    public static final String ADVERTISING_FAILED_EXTRA_CODE = "failureCode";

    public static final int ADVERTISING_TIMED_OUT = 6;

    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    private BluetoothGattServer mGattServer; 

    private AdvertiseCallback mAdvertiseCallback;

    private Handler mHandler;

    private Runnable timeoutRunnable;

    public String callNumber;
    public String msgCallNumber;
    public String msgText;

    public final static UUID UUID_VOICE_CALL_SERVICE = 
		UUID.fromString(SampleGattAttributes.VOICE_CALL_SERVICE);
    public final static UUID UUID_CALL_NUMBER = 
		UUID.fromString(SampleGattAttributes.CALL_NUMBER); 
    public final static UUID UUID_MESSAGE_SERVICE = 
		UUID.fromString(SampleGattAttributes.MESSAGE_SERVICE);
    public final static UUID UUID_MESSAEG_NUMBER = 
		UUID.fromString(SampleGattAttributes.MESSAGE_NUMBER);
    public final static UUID UUID_MESSAGE_TEXT = 
		UUID.fromString(SampleGattAttributes.MESSAGE_TEXT);
	
    /**
     * Length of time to allow advertising before automatically shutting off. (10 minutes)
     */
    private long TIMEOUT = TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES);

	private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
		@Override  
		public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {  
			Log.d(TAG, "onConnectionStateChange: gatt server connection state changed, new state " + Integer.toString(newState));  
			super.onConnectionStateChange(device,status,newState);  
		}  

		@Override  
		public void onServiceAdded(int status, BluetoothGattService service) {  
			Log.d(TAG, "onServiceAdded: " + Integer.toString(status));  
			super.onServiceAdded(status, service);  
		}  

		@Override  
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {  
			Log.d(TAG, "onCharacteristicReadRequest: " + "requestId: " + Integer.toString(requestId) + ", offset " + Integer.toString(offset));  
			super.onCharacteristicReadRequest(device, requestId, offset, characteristic);  
			mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());  
		}  

		@Override  
		public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {  
			String valueStr = new String(value);
			Log.d(TAG, "onCharacteristicWriteRequest: " + "data = "+ valueStr);  
			super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);  
			mGattServer.sendResponse(device, requestId,BluetoothGatt.GATT_SUCCESS,offset,value);  
			/*store data here*/
			if (characteristic.getService().getUuid() == UUID_VOICE_CALL_SERVICE 
					&& characteristic.getUuid() == UUID_CALL_NUMBER) {
				callNumber = valueStr;
				handleRequest(UUID_VOICE_CALL_SERVICE);
			}
			else if (characteristic.getService().getUuid() == UUID_MESSAGE_SERVICE) {
				if (characteristic.getUuid() == UUID_MESSAEG_NUMBER) {
					msgCallNumber  = valueStr;
				}
				else if (characteristic.getUuid() == UUID_MESSAGE_TEXT) {
					msgText = valueStr;
				}
				if(msgCallNumber != null && msgText != null)
					handleRequest(UUID_MESSAGE_SERVICE);
			}
		}  

		@Override  
		public void onNotificationSent(BluetoothDevice device, int status)  
		{  
			Log.d(TAG, "onNotificationSent: status = " + Integer.toString(status));  
			super.onNotificationSent(device, status);  
		}

		@Override  
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {  
			Log.d(TAG, "onDescriptorReadRequest: requestId = " + Integer.toString(requestId));  
			super.onDescriptorReadRequest(device, requestId, offset, descriptor);  
			mGattServer.sendResponse(device, requestId,BluetoothGatt.GATT_SUCCESS,offset, descriptor.getValue());  
		}  

		@Override  
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {  
			Log.d(TAG, "onDescriptorWriteRequest: requestId = " + Integer.toString(requestId));  
			super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);  
			mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,offset,value);  
		}  

		@Override  
		public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {  
			Log.d(TAG, "onExecuteWrite: requestId = " + Integer.toString(requestId));  
			super.onExecuteWrite(device, requestId, execute);  
			/*in case we stored data before, just execute the write action*/  
		}  

		@Override  
		public void onMtuChanged (BluetoothDevice device, int mtu) {  
			Log.d(TAG, "onMtuChanged: mtu = " + Integer.toString(mtu));  
		}

	};

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate.");
        running = true;
        initialize();
        startAdvertising();
        setTimeout();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        /**
         * Note that onDestroy is not guaranteed to be called quickly or at all. Services exist at
         * the whim of the system, and onDestroy can be delayed or skipped entirely if memory need
         * is critical.
         */
        Log.d(TAG, "onDestroy.");
        running = false;
	    mGattServer = null;
        stopAdvertising();
        mHandler.removeCallbacks(timeoutRunnable);
        super.onDestroy();
    }

    /**
     * Required for extending service, but this will be a Started Service only, so no need for
     * binding.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Get references to system Bluetooth objects if we don't have them already.
     */
	private void initialize() {
		if (mBluetoothLeAdvertiser == null) {
			BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager != null) {
				BluetoothAdapter mBluetoothAdapter = mBluetoothManager.getAdapter();
				if (mBluetoothAdapter != null) {
					mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
					mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
					if (mGattServer == null) {
						Toast.makeText(this, getString(R.string.bt_gatt_server_null), Toast.LENGTH_LONG).show(); 
					} else {
						addSupportServices(); /*build gatt server data here*/  
					}
				} else {
					Toast.makeText(this, getString(R.string.bt_null), Toast.LENGTH_LONG).show();
				}
			} else {
				Toast.makeText(this, getString(R.string.bt_null), Toast.LENGTH_LONG).show();
			}
		}

	}

    /**
     * Starts a delayed Runnable that will cause the BLE Advertising to timeout and stop after a
     * set amount of time.
     */
    private void setTimeout(){
        mHandler = new Handler();
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "AdvertiserService has reached timeout of "+TIMEOUT+" milliseconds, stopping advertising.");
                sendFailureIntent(ADVERTISING_TIMED_OUT);
                stopSelf();
		  if (mGattServer != null) {
		  	mGattServer.close();
			mGattServer = null;
		  }
		  stopAdvertising();
            }
        };
        mHandler.postDelayed(timeoutRunnable, TIMEOUT);
    }

    /**
     * Starts BLE Advertising.
     */
    private void startAdvertising() {
        Log.d(TAG, "Service: Starting Advertising");

        if (mAdvertiseCallback == null) {
            AdvertiseSettings settings = buildAdvertiseSettings();
            AdvertiseData data = buildAdvertiseData();
            mAdvertiseCallback = new SampleAdvertiseCallback();

            if (mBluetoothLeAdvertiser != null) {
                mBluetoothLeAdvertiser.startAdvertising(settings, data,
                        mAdvertiseCallback);
            }
        }
    }

    /**
     * Stops BLE Advertising.
     */
    private void stopAdvertising() {
        Log.d(TAG, "Service: Stopping Advertising");
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            mAdvertiseCallback = null;
        }
    }

    /**
     * Returns an AdvertiseData object which includes the Service UUID and Device Name.
     */
    private AdvertiseData buildAdvertiseData() {

        /**
         * Note: There is a strict limit of 31 Bytes on packets sent over BLE Advertisements.
         *  This includes everything put into AdvertiseData including UUIDs, device info, &
         *  arbitrary service or manufacturer data.
         *  Attempting to send packets over this limit will result in a failure with error code
         *  AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE. Catch this error in the
         *  onStartFailure() method of an AdvertiseCallback implementation.
         */

        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.addServiceUuid(Constants.Service_UUID);
        dataBuilder.setIncludeDeviceName(true);

        /* For example - this will cause advertising to fail (exceeds size limit) */
        //String failureData = "asdghkajsghalkxcjhfa;sghtalksjcfhalskfjhasldkjfhdskf";
        //dataBuilder.addServiceData(Constants.Service_UUID, failureData.getBytes());

        return dataBuilder.build();
    }

    /**
     * Returns an AdvertiseSettings object set to use low power (to help preserve battery life)
     * and disable the built-in timeout since this code uses its own timeout runnable.
     */
    private AdvertiseSettings buildAdvertiseSettings() {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
        settingsBuilder.setTimeout(0);
        return settingsBuilder.build();
    }

    /**
     * Custom callback after Advertising succeeds or fails to start. Broadcasts the error code
     * in an Intent to be picked up by AdvertiserFragment and stops this Service.
     */
    private class SampleAdvertiseCallback extends AdvertiseCallback {

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);

            Log.d(TAG, "Advertising failed");
            sendFailureIntent(errorCode);
            stopSelf();

        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, "Advertising successfully started");
        }
    }

	private void addSupportServices() {
		//clear services firstly.
		if(mGattServer == null)
			return;

		mGattServer.clearServices();

		BluetoothGattService voicecallService = new BluetoothGattService(UUID_VOICE_CALL_SERVICE,
				BluetoothGattService.SERVICE_TYPE_PRIMARY);
		BluetoothGattCharacteristic voicecallNum = new BluetoothGattCharacteristic(UUID_CALL_NUMBER,
				BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ,
				BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ);
		
		BluetoothGattService messageService = new BluetoothGattService(UUID_MESSAGE_SERVICE,
				BluetoothGattService.SERVICE_TYPE_PRIMARY);
		BluetoothGattCharacteristic messageNum = new BluetoothGattCharacteristic(UUID_MESSAEG_NUMBER,
				BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ,
				BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ);
		BluetoothGattCharacteristic messageText = new BluetoothGattCharacteristic(UUID_MESSAGE_TEXT,
				BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ,
				BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ);
		
		voicecallService.addCharacteristic(voicecallNum);
		Log.d(TAG, "voicecallNum = " + voicecallNum);
		Log.d(TAG, "voicecallService = " + voicecallService);
		mGattServer.addService(voicecallService);
		
		messageService.addCharacteristic(messageNum);
		messageService.addCharacteristic(messageText);
		Log.d(TAG, "messageNum = " + messageNum);
		Log.d(TAG, "messageText = " + messageText);
		Log.d(TAG, "messageService = " + messageService);
		mGattServer.addService(messageService); 
	}

    /**
     * Builds and sends a broadcast intent indicating Advertising has failed. Includes the error
     * code as an extra. This is intended to be picked up by the {@code AdvertiserFragment}.
     */
    private void sendFailureIntent(int errorCode){
        Intent failureIntent = new Intent();
        failureIntent.setAction(ADVERTISING_FAILED);
        failureIntent.putExtra(ADVERTISING_FAILED_EXTRA_CODE, errorCode);
        sendBroadcast(failureIntent);
    }

	public void handleRequest(UUID uuid) {
		if (uuid == UUID_VOICE_CALL_SERVICE) {
			setupVoiceCall();
		} else if (uuid == UUID_MESSAGE_SERVICE) {
			sendShortMessage();
		}		
	}

	public void setupVoiceCall() {
		Log.d(TAG, "setup one voice call on BLE device");
		Intent intent = new Intent(Intent.ACTION_CALL);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		Uri data = Uri.parse("tel:" + callNumber);
		intent.setData(data);
		getApplication().startActivity(intent);

		//clear the stored data
		callNumber = null;
	}

	public void sendShortMessage() {
		Log.d(TAG, "send short message on BLE device.");
		Uri uri = Uri.parse("smsto:" + msgCallNumber);
		Intent intent = new Intent(Intent.ACTION_SENDTO,uri);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("sms_body", msgText);
		getApplication().startActivity(intent);

		//clear the stored data
		msgCallNumber = null;
		msgText = null;
	}

}
