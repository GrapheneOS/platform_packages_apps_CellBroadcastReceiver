/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cellbroadcastreceiver.compliancetests;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemProperties;
import android.support.test.uiautomator.UiDevice;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.mockmodem.IRadioMessagingImpl;
import android.telephony.mockmodem.MockModemConfigBase.SimInfoChangedResult;
import android.telephony.mockmodem.MockModemManager;
import android.telephony.mockmodem.MockSimService;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.CellBroadcastUtils;
import com.android.modules.utils.build.SdkLevel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class CellBroadcastBaseTest {
    private static final String TAG = "CellBroadcastBaseTest";
    protected static MockModemManager sMockModemManager;
    protected static int sSlotId = 0;
    protected static JSONObject sCarriersObject;
    protected static JSONObject sChannelsObject;
    protected static JSONObject sSettingsObject;
    protected static int sPreconditionError = 0;
    protected static final int ERROR_SDK_VERSION = 1;
    protected static final int ERROR_NO_TELEPHONY = 2;
    protected static final int ERROR_MOCK_MODEM_DISABLE = 3;

    protected static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    protected static final boolean DEBUG = !"user".equals(Build.TYPE);

    protected static final String EXPECTED_RESULT_CHANNELS_JSON = "emergency_alert_channels.json";
    protected static final String CARRIER_LISTS_JSON = "region_plmn_list.json";
    protected static final String EXPECTED_RESULT_SETTINGS_JSON = "emergency_alert_settings.json";
    protected static final String CARRIER_MCCMNC_FIELD = "mccmnc";
    protected static final String CHANNEL_DEFAULT_VALUE_FIELD = "default_value";

    protected static final String ACTION_SET_CHANNELS_DONE =
            "android.cellbroadcast.compliancetest.SET_CHANNELS_DONE";
    protected static CountDownLatch sSetChannelIsDone =  new CountDownLatch(1);
    protected static String sInputMccMnc = null;
    protected static BroadcastReceiver sReceiver = null;

    protected static final int MAX_WAIT_TIME = 15 * 1000;

    protected static Instrumentation sInstrumentation = null;
    protected static UiDevice sDevice = null;
    protected static String sPackageName = null;
    protected static IRadioMessagingImpl.CallBackWithExecutor sCallBackWithExecutor = null;
    private static ServiceStateListener sServiceStateCallback;
    private static int sServiceState = ServiceState.STATE_OUT_OF_SERVICE;
    private static final Object OBJECT = new Object();
    private static final int SERVICE_STATE_MAX_WAIT = 20 * 1000;
    protected static CountDownLatch sServiceStateLatch =  new CountDownLatch(1);

    private static class ServiceStateListener extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            Log.d(TAG, "Callback: service state = " + serviceState.getVoiceRegState());
            synchronized (OBJECT) {
                sServiceState = serviceState.getVoiceRegState();
                if (sServiceState == ServiceState.STATE_IN_SERVICE) {
                    sServiceStateLatch.countDown();
                    logd("countdown sServiceStateLatch");
                }
            }
        }
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static class BroadcastChannelListener
            implements IRadioMessagingImpl.BroadcastCallback {
        @Override
        public void onGsmBroadcastActivated() {
            TelephonyManager tm = getContext().getSystemService(TelephonyManager.class);
            String mccmnc = tm.getSimOperator(SubscriptionManager.getDefaultSubscriptionId());
            logd("onGsmBroadcastActivated, mccmnc = " + mccmnc);
            if (sInputMccMnc != null && sInputMccMnc.equals(mccmnc)) {
                sSetChannelIsDone.countDown();
                logd("wait is released");
            }
        }

        @Override
        public void onCdmaBroadcastActivated() {
        }
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd("CellBroadcastBaseTest#beforeAllTests()");
        // TODO: Make cellbroadcastcompliancetest use old mockmodem lib so that test can be
        // run on the previous platform as well.
        if (!SdkLevel.isAtLeastV()) {
            Log.i(TAG, "sdk level is below the latest platform");
            sPreconditionError = ERROR_SDK_VERSION;
            return;
        }

        final PackageManager pm = getContext().getPackageManager();
        boolean hasTelephonyFeature = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        if (!hasTelephonyFeature) {
            Log.i(TAG, "Not have Telephony Feature");
            sPreconditionError = ERROR_NO_TELEPHONY;
            return;
        }

        if (!isMockModemAllowed()) {
            Log.i(TAG, "Mock Modem is not allowed");
            sPreconditionError = ERROR_MOCK_MODEM_DISABLE;
            return;
        }

        if (!SdkLevel.isAtLeastU()) {
            sReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (ACTION_SET_CHANNELS_DONE.equals(action)) {
                        int subId = intent.getIntExtra("sub_id", -1);
                        logd("INTENT_SET_CHANNELS_DONE is received, subId=" + subId);
                        TelephonyManager tm = getContext().getSystemService(TelephonyManager.class)
                                .createForSubscriptionId(subId);
                        if (tm != null) {
                            String mccMncOfIntent = tm.getSimOperator();
                            logd("mccMncOfIntent = " + mccMncOfIntent);
                            if (sInputMccMnc != null && sInputMccMnc.equals(mccMncOfIntent)) {
                                sSetChannelIsDone.countDown();
                                logd("wait is released");
                            }
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_SET_CHANNELS_DONE);
            getContext().registerReceiver(sReceiver, filter, Context.RECEIVER_EXPORTED);
        }

        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sDevice = UiDevice.getInstance(sInstrumentation);

        sMockModemManager = new MockModemManager();
        assertTrue(sMockModemManager.connectMockModemService(
                MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT));

        if (SdkLevel.isAtLeastU()) {
            BroadcastChannelListener broadcastCallback = new BroadcastChannelListener();
            sCallBackWithExecutor = new IRadioMessagingImpl.CallBackWithExecutor(
                    Runnable::run, broadcastCallback);
            sMockModemManager.registerBroadcastCallback(sSlotId, sCallBackWithExecutor);
        }
        waitForNotify();

        enterService();

        String jsonCarrier = loadJsonFile(CARRIER_LISTS_JSON);
        sCarriersObject = new JSONObject(jsonCarrier);
        String jsonChannels = loadJsonFile(EXPECTED_RESULT_CHANNELS_JSON);
        sChannelsObject = new JSONObject(jsonChannels);
        String jsonSettings = loadJsonFile(EXPECTED_RESULT_SETTINGS_JSON);
        sSettingsObject = new JSONObject(jsonSettings);
        sPackageName = CellBroadcastUtils
                .getDefaultCellBroadcastReceiverPackageName(getContext());
    }

    private static void waitForNotify() {
        try {
            sSetChannelIsDone.await(MAX_WAIT_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        logd("CellBroadcastBaseTest#afterAllTests()");

        if (sReceiver != null) {
            getContext().unregisterReceiver(sReceiver);
        }
        if (sCallBackWithExecutor != null && sMockModemManager != null) {
            sMockModemManager.unregisterBroadcastCallback(sSlotId, sCallBackWithExecutor);
        }
        if (sMockModemManager != null) {
            // Rebind all interfaces which is binding to MockModemService to default.
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;
        }
        sInputMccMnc = null;
    }

    @Rule
    public final TestName mTestNameRule = new TestName();
    @Before
    public void beforeTest() throws Exception {
        assumeTrue(getErrorMessage(sPreconditionError), sPreconditionError == 0);
    }

    protected static String loadJsonFile(String jsonFile) {
        String json = null;
        try {
            InputStream inputStream = getContext().getAssets().open(jsonFile);
            int size = inputStream.available();
            byte[] byteArray = new byte[size];
            inputStream.read(byteArray);
            inputStream.close();
            json = new String(byteArray, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return json;
    }

    protected String[] paramsForTest() throws Throwable {
        logd("paramsForTest");
        String jsonCarrier = loadJsonFile(CARRIER_LISTS_JSON);
        JSONObject carriersObject = new JSONObject(jsonCarrier);
        Iterator<String> carrierList = carriersObject.keys();

        ArrayList<String> carrierLists = new ArrayList<>();
        for (Iterator<String> it = carrierList; it.hasNext();) {
            carrierLists.add(it.next());
        }
        return carrierLists.toArray(new String[]{});
    }

    protected Object[] paramsCarrierAndMccMncForTest() throws Throwable {
        logd("paramsCarrierAndMccMncForTest");
        String jsonCarrier = loadJsonFile(CARRIER_LISTS_JSON);
        JSONObject carriersObject = new JSONObject(jsonCarrier);
        Iterator<String> carrierList = carriersObject.keys();

        ArrayList<Object> result = new ArrayList<Object>();
        for (Iterator<String> it = carrierList; it.hasNext();) {
            String carrierName = it.next();
            JSONObject carrierObject = carriersObject.getJSONObject(carrierName);
            JSONArray mccMncList = carrierObject.getJSONArray(CARRIER_MCCMNC_FIELD);
            for (int i = 0; i < mccMncList.length(); i++) {
                String mccMnc = mccMncList.getString(i);
                result.add(new String[]{carrierName, mccMnc});
            }
        }
        return result.toArray(new Object[]{});
    }

    protected Object[] paramsCarrierAndChannelForTest() throws Throwable {
        logd("paramsCarrierAndChannelForTest");
        String jsonCarrier = loadJsonFile(CARRIER_LISTS_JSON);
        JSONObject carriersObject = new JSONObject(jsonCarrier);
        Iterator<String> carrierList = carriersObject.keys();

        ArrayList<Object> result = new ArrayList<Object>();
        for (Iterator<String> it = carrierList; it.hasNext();) {
            String carrierName = it.next();
            String jsonChannels = loadJsonFile(EXPECTED_RESULT_CHANNELS_JSON);
            JSONObject channelsObject = new JSONObject(jsonChannels);
            JSONObject channelsForCarrier = channelsObject.getJSONObject(carrierName);
            for (Iterator<String> iterator = channelsForCarrier.keys(); iterator.hasNext();) {
                String channelId = iterator.next();
                result.add(new String[]{carrierName, channelId});
            }
        }
        return result.toArray(new Object[]{});
    }

    protected void setSimInfo(String carrierName, String inputMccMnc) throws Throwable {
        String mcc = inputMccMnc.substring(0, 3);
        String mnc = inputMccMnc.substring(3);
        sInputMccMnc = inputMccMnc;
        sSetChannelIsDone = new CountDownLatch(1);

        String[] mccMnc = new String[] {mcc, mnc};
        logd("carrierName = " + carrierName
                + ", mcc = " + mccMnc[0] + ", mnc = " + mccMnc[1]);

        int slotId = 0;

        boolean isSuccessful = sMockModemManager.setSimInfo(slotId,
                SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC, mccMnc);
        assertTrue(isSuccessful);
        waitForNotify();
    }

    private static boolean isMockModemAllowed() {
        boolean isAllowed = SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false);
        // Check for developer settings for user build. Always allow for debug builds
        return isAllowed || DEBUG;
    }

    protected String getErrorMessage(int error) {
        String errorMessage = "Precondition Error";
        switch (error) {
            case ERROR_SDK_VERSION:
                errorMessage = "SDK level is below T";
                break;
            case ERROR_NO_TELEPHONY:
                errorMessage = "Not have Telephony Feature";
                break;
            case ERROR_MOCK_MODEM_DISABLE:
                errorMessage = "Please enable mock modem to run the test! The option can be "
                        + "updated in Settings -> System -> Developer options -> Allow Mock Modem";
                break;
        }
        return errorMessage;
    }

    protected static void logd(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }

    protected static void enterService() throws Exception {
        logd("enterService");
        HandlerThread serviceStateChangeCallbackHandlerThread =
                new HandlerThread("ServiceStateChangeCallback");
        serviceStateChangeCallbackHandlerThread.start();
        Handler serviceStateChangeCallbackHandler =
                new Handler(serviceStateChangeCallbackHandlerThread.getLooper());
        TelephonyManager telephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        sSetChannelIsDone = new CountDownLatch(1);
        // Register service state change callback
        synchronized (OBJECT) {
            sServiceState = ServiceState.STATE_OUT_OF_SERVICE;
        }

        serviceStateChangeCallbackHandler.post(
                () -> {
                    sServiceStateCallback = new ServiceStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            telephonyManager,
                            (tm) -> tm.registerTelephonyCallback(
                                    Runnable::run, sServiceStateCallback));
                });

        // Enter Service
        logd("Enter Service");
        sMockModemManager.changeNetworkService(sSlotId, MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT,
                true);

        // Expect: Home State
        logd("Wait for service state change to in service");
        waitForNotifyForServiceState();

        // Unregister service state change callback
        telephonyManager.unregisterTelephonyCallback(sServiceStateCallback);
        sServiceStateCallback = null;
    }

    private static void waitForNotifyForServiceState() {
        try {
            sServiceStateLatch.await(SERVICE_STATE_MAX_WAIT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // do nothing
        }
    }
}
