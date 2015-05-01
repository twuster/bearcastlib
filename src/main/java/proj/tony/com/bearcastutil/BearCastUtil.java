package proj.tony.com.bearcastutil;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import edu.berkeley.sdb.blescanner.BLEScanner;

/**
 * Created by tonywu on 4/29/15.
 */
public class BearCastUtil {

    private static final String TAG = BearCastUtil.class.getSimpleName();
    private static final long TIMER_PERIOD = 5000;
    private static final long TIMER_DELAY = 500;
    private static final String FIRESTORM_MAC = "EC:C1:8E:1D:48:60";
//    private static final String TEST_LOCATION = "USA/California/Berkeley/Soda/410";
    private static final String FIRESTORM_MAC2 = "F9:86:10:B1:19:F9";
//    private static final String TEST_LOCATION2 = "USA/California/Berkeley/Soda/610";
    private static final String FIRESTORM_MAC3 = "EC:C1:8E:1D:48:60";
//    private static final String TEST_LOCATION3 = "USA/California/Berkeley/Soda/510";

    private static final String TEST_IP = "136.152.38.117";
    private static final String SERVER_PORT = "8080";
    private static final String MQTT_SERVER = "tcp://54.215.11.207:9009";
    private static final String DISCOVERY_TOPIC = "bearcast_registration";

    // Key for user shared prefenreces
    private static final String USER_PREFS_NAME = "UserKey";
    // Shared Preferences
    SharedPreferences mUserPreferences;

    private MqttAndroidClient mMQTTClient;
    private MqttCallback mMqttCallback;
    private String mResultTopic;
    private String muuid;
    private HashMap<Long, String> mRequestMapping;

    private static String sClosestDisplayTopic;
    private static String sCastText;
    private static Context sContext;

    /*
    * Gets the number of available cores
    * (not always the same as the maximum number of cores)
    */
    private static int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 1;
    // Sets the Time Unit to seconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
    // Creates a thread pool manager
    private ExecutorService mNetworkThreadPool = new ThreadPoolExecutor(
            NUMBER_OF_CORES,       // Initial pool size
            NUMBER_OF_CORES,       // Max pool size
            KEEP_ALIVE_TIME,
            KEEP_ALIVE_TIME_UNIT,
            new LinkedBlockingQueue<Runnable>());

    private String mCurrentLocation;
    private String mCurrentClosestMac;

    private HashSet<String> mValidMacAddresses;

    private Timer mBleTimer;
    private BLEScanner mBleScanner;
    private BleCallback mBleCallback;

    private Timer mHeartbeatTimer;
    private String sName;

    public BearCastUtil(Context context, String name) {
        sContext = context;
        sName = name;

        mBleScanner = new BLEScanner(sContext);
        if (!mBleScanner.enable()) {
            Log.e(TAG, "Failed to initialize BLE scanner");
        }

        mValidMacAddresses = new HashSet<>(); // TODO: Hard code this set for now
        mValidMacAddresses.add(FIRESTORM_MAC);
        mValidMacAddresses.add(FIRESTORM_MAC2);
        mValidMacAddresses.add(FIRESTORM_MAC3);

        muuid = DeviceUUID.getDeviceUUID(sContext).toString();
        sClosestDisplayTopic = null;
        mRequestMapping = new HashMap<Long, String>();
        mMqttCallback = new MyMqttCallback();

        mMQTTClient = new MqttAndroidClient(sContext, MQTT_SERVER, MqttClient.generateClientId());
        mMQTTClient.setCallback(mMqttCallback);
        mNetworkThreadPool.execute(new ConnectRunnable());
    }

    public void startBluetoothScan() {
        mBleTimer = new Timer();
        mBleCallback = new BleCallback();
        mBleTimer.scheduleAtFixedRate(new BleScannerTask(), TIMER_DELAY, TIMER_PERIOD);

        mHeartbeatTimer = new Timer();
        mHeartbeatTimer.scheduleAtFixedRate(new HeartBeatTask(), TIMER_DELAY, TIMER_PERIOD);
    }

    public void stopBlueToothScan() {
        mBleTimer.cancel();

        mHeartbeatTimer.cancel();
    }


    public void castMessage(String text) {
        sCastText = text;
        if (sClosestDisplayTopic != null) {
            mNetworkThreadPool.execute(new CastRunnable());
            Toast.makeText(sContext, "Sending Message", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(sContext, "Waiting to Discover Closest Device", Toast.LENGTH_SHORT).show();
        }
    }

    // Gets the client's current location
//    @Deprecated
//    private String getLocation() {
//        if (mCurrentClosestMac != null){
//            mCurrentLocation = mBleMacToLocation.get(mCurrentClosestMac);
//        }
//        return mCurrentLocation;
//    }

    private class MyMqttCallback implements MqttCallback {
        @Override
        public void connectionLost(Throwable cause) {
            Log.d(TAG, "MQTT Server connection lost");
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            Log.d(TAG, String.format("Message arrived: %s: %s", topic, message.toString()));

            if (!topic.equals(mResultTopic)) {
                return;
            }

            JSONObject msg = null;

            try {
                String payload = new String(message.getPayload());
                msg = new JSONObject(payload);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                long reqNum = msg.getLong("req_num");
                if (mRequestMapping.containsKey(reqNum)) {
                    if (mRequestMapping.get(reqNum) == "discover") {
                        JSONObject result = msg.getJSONObject("result");
                        sClosestDisplayTopic = result.getString("server_topic");
                        Log.d(TAG, "Received topic: " + sClosestDisplayTopic);
                        mRequestMapping.remove(reqNum);
                    } else if (mRequestMapping.get(reqNum) == "cast") {
                        Log.d(TAG, "FINISHED CASTING");
                        mRequestMapping.remove(reqNum);
                    } else if (mRequestMapping.get(reqNum).equals("heartbeat")) {
                        Log.d(TAG, "FINISHED HEARTBEAT");
                        mRequestMapping.remove(reqNum);
                    } else if (mRequestMapping.get(reqNum).equals("get_location")) {
                        Log.d(TAG, "FINISHED GET LOCATION");
                        mRequestMapping.remove(reqNum);
                        JSONObject result = msg.getJSONObject("result");
                        mCurrentLocation = result.getString("location");
                        // Discover after getting a location
                        mNetworkThreadPool.execute(new DiscoverRunnable());
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            Log.d(TAG, "Delivery complete");
        }
    }

    private class CastRunnable implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "CASTING");

            final JSONObject json = new JSONObject();
            Long epoch = System.currentTimeMillis()/1000;

            try {
                json.put("req_type", "cast");
                final JSONObject reqContent = new JSONObject();

                String message = sCastText;
                reqContent.put("data", message);
                reqContent.put("name", sName);
                json.put("req_content", reqContent);

                mResultTopic = muuid + "-reply";
                json.put("reply_topic", muuid + "-reply");

                json.put("req_num", epoch);

            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                MqttMessage message = new MqttMessage();
                message.setPayload(json.toString().getBytes());
                mRequestMapping.put(epoch, "cast");
                IMqttDeliveryToken token = mMQTTClient.publish(sClosestDisplayTopic, message);
                token.waitForCompletion();
                Log.d(TAG, "DONE CASTING");

            } catch (MqttException e) {
                e.printStackTrace();
            }

        }
    }

    private class ConnectRunnable implements Runnable {

        @Override
        public void run() {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            try {
                IMqttToken token = mMQTTClient.connect(options);
                token.waitForCompletion();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    private class DiscoverRunnable implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "RUNNING");

            if (mCurrentLocation == null) {
                Log.e(TAG, "CURRENT LOCATION IS NULL");
                return;
            }

            final JSONObject json = new JSONObject();
            Long epoch = System.currentTimeMillis()/1000;

            try {
                mResultTopic = muuid + "-reply";
                IMqttToken token = mMQTTClient.subscribe(mResultTopic, 0);
                token.waitForCompletion();
            } catch (MqttException e ) {
                e.printStackTrace();
            }

            try {
                json.put("req_type", "discover");
                final JSONObject reqContent = new JSONObject();
                reqContent.put("location", mCurrentLocation);
                json.put("req_content", reqContent);

                json.put("reply_topic", mResultTopic);

                json.put("req_num", epoch);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                Log.d(TAG, "POST MESSAGE");

                MqttMessage message = new MqttMessage();
                message.setPayload(json.toString().getBytes());
                mRequestMapping.put(epoch, "discover");
                IMqttDeliveryToken token = mMQTTClient.publish(DISCOVERY_TOPIC, message);
                token.waitForCompletion();
                Log.d(TAG, "DONE");

            } catch (MqttException e) {
                e.printStackTrace();
            }

        }
    }

    private class GetLocationRunnable implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "GETTING LOCATION");

            final JSONObject json = new JSONObject();
            Long epoch = System.currentTimeMillis() / 1000;

            try {
                mResultTopic = muuid + "-reply";
                IMqttToken token = mMQTTClient.subscribe(mResultTopic, 0);
                token.waitForCompletion();
            } catch (MqttException e ) {
                e.printStackTrace();
            }

            try {
                json.put("req_type", "get_location");
                final JSONObject reqContent = new JSONObject();

                reqContent.put("key", mCurrentClosestMac);
                json.put("req_content", reqContent);

                json.put("reply_topic", mResultTopic);

                json.put("req_num", epoch);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                MqttMessage message = new MqttMessage();
                message.setPayload(json.toString().getBytes());
                mRequestMapping.put(epoch, "get_location");
                IMqttDeliveryToken token = mMQTTClient.publish(DISCOVERY_TOPIC, message);
                token.waitForCompletion();
                Log.d(TAG, "DONE GETTING LOCATION");

            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }
    
    private class HeartBeatTask extends TimerTask {
        @Override
        public void run() {
            Log.d(TAG, "HEARTBEAT");

            final JSONObject json = new JSONObject();
            Long epoch = System.currentTimeMillis()/1000;

            try {
                json.put("req_type", "heartbeat");
                final JSONObject reqContent = new JSONObject();

                reqContent.put("data", sName);
                json.put("req_content", reqContent);

                mResultTopic = muuid + "-reply";
                json.put("reply_topic", muuid + "-reply");

                json.put("req_num", epoch);

            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                MqttMessage message = new MqttMessage();
                message.setPayload(json.toString().getBytes());
                mRequestMapping.put(epoch, "heartbeat");
                IMqttDeliveryToken token = mMQTTClient.publish(sClosestDisplayTopic, message);
                token.waitForCompletion();
                Log.d(TAG, "DONE HEARTBEAT");

            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    private class BleScannerTask extends TimerTask {
        @Override
        public void run() {
            mBleScanner.getBLEDeviceMap(mBleCallback);
        }
    }

    // Callback for BLE Scanner
    private class BleCallback implements BLEScanner.BluetoothScannerCallBack {
        @Override
        public void onMapReturned(HashMap<String, Integer> signalStrengths) {
            String newClosestMac = null;
            int maxStrength = Integer.MIN_VALUE;

            for (HashMap.Entry<String,Integer> e : signalStrengths.entrySet()) {
                String macAddr = e.getKey();
                int signalStrength = e.getValue();
                Log.d(TAG, "RSSI: " + signalStrength);

                if (signalStrength > maxStrength && mValidMacAddresses.contains(macAddr)) {
                    Log.d(TAG, "Found closest mac address: " + macAddr);
                    maxStrength = signalStrength;
                    newClosestMac = macAddr;
                }
            }

            if (newClosestMac != null && !newClosestMac.equals(mCurrentClosestMac)) {
                mCurrentClosestMac = newClosestMac;
            }
            mNetworkThreadPool.execute(new GetLocationRunnable());
        }
    }
}
