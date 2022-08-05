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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.telephony.mockmodem.MockModemConfigBase.SimInfoChangedResult;
import android.telephony.mockmodem.MockModemManager;
import android.telephony.mockmodem.MockSimService;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.modules.utils.build.SdkLevel;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class CellBroadcastConfigTest {
    private static final String TAG = "CellBroadcastConfigTest";
    private static MockModemManager sMockModemManager;
    private static JSONObject sCarriersObject;
    private static JSONObject sChannelsObject;
    private static int sPreconditionError = 0;
    private static final int ERROR_SDK_VERSION = 1;
    private static final int ERROR_NO_TELEPHONY = 2;
    private static final int ERROR_MULTI_SIM = 3;
    private static final int ERROR_MOCK_MODEM_DISABLE = 4;

    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    private static final boolean DEBUGGABLE = SystemProperties.getInt("ro.debuggable", 0) == 1;

    private static final String EXPECTED_RESULT_CHANNELS_JSON = "emergency_alert_channels.json";
    private static final String CARRIER_LISTS_JSON = "region_plmn_list.json";
    private static final String CARRIER_MCCMNC_FIELD = "mccmnc";
    private static final String CHANNEL_DEFAULT_VALUE_FIELD = "default_value";

    private static final String ACTION_SET_CHANNELS_DONE =
            "android.cellbroadcast.compliancetest.SET_CHANNELS_DONE";
    private static CountDownLatch sSetChannelIsDone =  new CountDownLatch(1);
    private static String sInputMccMnc = null;
    private static BroadcastReceiver sReceiver = null;

    private static final int MAX_WAIT_TIME = 15 * 1000;

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd("CellBroadcastConfigTest#beforeAllTests()");
        if (!SdkLevel.isAtLeastT()) {
            Log.i(TAG, "sdk level is below T");
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

        TelephonyManager tm =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        boolean isMultiSim = tm != null && tm.getPhoneCount() > 1;
        if (isMultiSim) {
            Log.i(TAG, "Not support Multi-Sim");
            sPreconditionError = ERROR_MULTI_SIM;
            return;
        }

        if (!isMockModemAllowed()) {
            Log.i(TAG, "Mock Modem is not allowed");
            sPreconditionError = ERROR_MOCK_MODEM_DISABLE;
            return;
        }

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
        getContext().registerReceiver(sReceiver, filter);

        sMockModemManager = new MockModemManager();
        assertTrue(sMockModemManager.connectMockModemService(
                MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT));
        waitForNotify();

        String jsonCarrier = loadJsonFile(CARRIER_LISTS_JSON);
        sCarriersObject = new JSONObject(jsonCarrier);
        String jsonChannels = loadJsonFile(EXPECTED_RESULT_CHANNELS_JSON);
        sChannelsObject = new JSONObject(jsonChannels);
    }

    private static void waitForNotify() {
        logd("waitForNotify start");
        while (sSetChannelIsDone.getCount() > 0) {
            try {
                sSetChannelIsDone.await(MAX_WAIT_TIME, TimeUnit.MILLISECONDS);
                sSetChannelIsDone.countDown();
            } catch (InterruptedException e) {
                // do nothing
            }
        }
        logd("waitForNotify done");
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        logd("CellBroadcastConfigTest#afterAllTests()");

        if (sReceiver != null) {
            getContext().unregisterReceiver(sReceiver);
        }

        if (sMockModemManager != null) {
            // Rebind all interfaces which is binding to MockModemService to default.
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;
        }
    }

    @Before
    public void beforeTest() {
        logd("CellBroadcastConfigTest#beforeTest()");

        assumeTrue(getErrorMessage(sPreconditionError), sPreconditionError == 0);


    }

    private static String loadJsonFile(String jsonFile) {
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

    private String[] paramsForTest() throws Throwable {
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

    @Test
    @Parameters(method = "paramsForTest")
    public void testCellBroadcastRange(String carrierName) throws Throwable {
        logd("CellBroadcastConfigTest#testCellBroadcastRange");

        JSONObject carrierObject = sCarriersObject.getJSONObject(carrierName);
        String mccMncFromObject = carrierObject.getString(CARRIER_MCCMNC_FIELD);
        String mcc = mccMncFromObject.substring(0, 3);
        String mnc = mccMncFromObject.substring(3);
        sInputMccMnc = mccMncFromObject;
        sSetChannelIsDone = new CountDownLatch(1);

        String[] mccMnc = new String[] {mcc, mnc};
        logd("carrierName = " + carrierName
                + ", mcc = " + mccMnc[0] + ", mnc = " + mccMnc[1]);

        int slotId = 0;

        boolean isSuccessful = sMockModemManager.setSimInfo(slotId,
                SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC, mccMnc);
        assertTrue(isSuccessful);
        waitForNotify();

        logd("Check Broadcast Channel Configs");
        Set<Integer> outputConfigs = sMockModemManager.getGsmBroadcastConfig();
        verifyOutput(outputConfigs, carrierName);
    }

    private void verifyOutput(Set<Integer> outputSet, String carrierName) throws JSONException {
        logd("verifyOutput");

        JSONObject channelsForCarrier = sChannelsObject.getJSONObject(carrierName);
        Set<Integer> expectedChannels = new ArraySet<>();
        for (Iterator<String> iterator = channelsForCarrier.keys(); iterator.hasNext();) {
            String channelId = iterator.next();
            JSONObject object = channelsForCarrier.getJSONObject(channelId);
            String defaultValue = object.getString(CHANNEL_DEFAULT_VALUE_FIELD);
            if (!TextUtils.isEmpty(defaultValue) && defaultValue.equals("false")) {
                logd("default configuration is off, let's skip " + channelId);
                // skip
            } else {
                logd("Expected channel " + channelId);
                Integer i = Integer.parseInt(channelId);
                String endChannel = null;
                try {
                    endChannel = object.getString("end_channel");
                } catch (Exception JSONException) {
                }
                if (TextUtils.isEmpty(endChannel)) {
                    expectedChannels.add(i);
                } else {
                    logd("endChannel " + endChannel);
                    Integer end = Integer.parseInt(endChannel);
                    for (int j = i.intValue(); j <= end; j++) {
                        expectedChannels.add(j);
                    }
                }
            }
        }

        assertEquals("Check Channel Configs for " + carrierName,
                expectedChannels, outputSet);
    }

    private static boolean isMockModemAllowed() {
        boolean isAllowed = SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false);
        // Check for developer settings for user build. Always allow for debug builds
        return isAllowed || DEBUG;
    }

    private String getErrorMessage(int error) {
        String errorMessage = "Precondition Error";
        switch (error) {
            case ERROR_SDK_VERSION:
                errorMessage = "SDK level is below T";
                break;
            case ERROR_NO_TELEPHONY:
                errorMessage = "Not have Telephony Feature";
                break;
            case ERROR_MULTI_SIM:
                errorMessage = "Multi-sim is not supported in Mock Modem";
                break;
            case ERROR_MOCK_MODEM_DISABLE:
                errorMessage = "Please enable mock modem to run the test! The option can be "
                        + "updated in Settings -> System -> Developer options -> Allow Mock Modem";
                break;
        }
        return errorMessage;
    }

    private static void logd(String msg) {
        if (DEBUGGABLE) Log.d(TAG, msg);
    }
}
