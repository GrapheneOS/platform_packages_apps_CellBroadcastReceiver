/**
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cellbroadcastreceiver.unit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Looper;
import android.os.RemoteException;
import android.support.test.uiautomator.UiDevice;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.preference.PreferenceManager;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.cellbroadcastreceiver.CellBroadcastSettings;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(AndroidJUnit4.class)
public class CellBroadcastSettingsTest extends
        CellBroadcastActivityTestCase<CellBroadcastSettings> {
    private Instrumentation mInstrumentation;
    private Context mContext;
    private UiDevice mDevice;
    private static final long DEVICE_WAIT_TIME = 1000L;

    public CellBroadcastSettingsTest() {
        super(CellBroadcastSettings.class);
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mDevice = UiDevice.getInstance(mInstrumentation);
    }

    @InstrumentationTest
    // This test has a module dependency, so it is disabled for OEM testing because it is not a true
    // unit test
    @FlakyTest
    @Test
    public void testRotateAlertReminderDialogOpen() throws InterruptedException {
        try {
            mDevice.wakeUp();
            mDevice.pressMenu();
        } catch (RemoteException exception) {
            Assert.fail("Exception " + exception);
        }

        mInstrumentation.startActivitySync(createActivityIntent());
        int w = mDevice.getDisplayWidth();
        int h = mDevice.getDisplayHeight();

        waitUntilDialogOpens(()-> {
            mDevice.swipe(w / 2 /* start X */,
                    h / 2 /* start Y */,
                    w / 2 /* end X */,
                    0 /* end Y */,
                    100 /* steps */);

            openAlertReminderDialog();
        }, DEVICE_WAIT_TIME);

        try {
            mDevice.setOrientationLeft();
            mDevice.setOrientationNatural();
            mDevice.setOrientationRight();
        } catch (Exception e) {
            Assert.fail("Exception " + e);
        }
    }

    @Test
    public void testResetAllPreferences() throws Throwable {
        Looper.prepare();
        // set a few preferences so we can verify they are reset to the default
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putBoolean(CellBroadcastSettings.KEY_RECEIVE_CMAS_IN_SECOND_LANGUAGE, true)
                .putBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_VIBRATE, false).apply();
        assertTrue("receive_cmas_in_second_language was not set to true",
                PreferenceManager.getDefaultSharedPreferences(mContext)
                        .getBoolean(CellBroadcastSettings.KEY_RECEIVE_CMAS_IN_SECOND_LANGUAGE,
                                false));
        assertFalse("enable_alert_vibrate was not set to false",
                PreferenceManager.getDefaultSharedPreferences(mContext)
                        .getBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_VIBRATE, true));

        // see preferences.xml for default values.
        // receive_cmas_in_second_language is false by default
        // enable_alert_vibrate is true by default
        CellBroadcastSettings.resetAllPreferences(mContext);

        assertFalse("receive_cmas_in_second_language was not reset to the default (false)",
                PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean(CellBroadcastSettings.KEY_RECEIVE_CMAS_IN_SECOND_LANGUAGE, true));
        assertTrue("enable_alert_vibrate was not reset to the default (true)",
                PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_VIBRATE, false));
    }

    @Test
    public void testHasAnyPreferenceChanged() {
        assertFalse(CellBroadcastSettings.hasAnyPreferenceChanged(mContext));
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putBoolean("any_preference_changed_by_user", true).apply();
        assertTrue(CellBroadcastSettings.hasAnyPreferenceChanged(mContext));
    }

    @Test
    public void testGetResources() {
        Context mockContext = mock(Context.class);
        Resources mockResources = mock(Resources.class);
        doReturn(mockResources).when(mockContext).getResources();

        CellBroadcastSettings.getResources(
                mockContext, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verify(mockContext, never()).getSystemService(anyString());
        verify(mockContext, times(1)).getResources();

        CellBroadcastSettings.getResources(
                mockContext, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mockContext, never()).getSystemService(anyString());
        verify(mockContext, times(2)).getResources();

        Context mockContext2 = mock(Context.class);
        doReturn(mockResources).when(mockContext2).getResources();
        SubscriptionManager mockSubManager = mock(SubscriptionManager.class);
        TelephonyManager mockTelephonyManager = mock(TelephonyManager.class);
        doReturn(Context.TELEPHONY_SUBSCRIPTION_SERVICE).when(mockContext)
                .getSystemServiceName(eq(SubscriptionManager.class));
        doReturn(mockSubManager).when(mockContext).getSystemService(
                eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE));
        doReturn(Context.TELEPHONY_SERVICE).when(mockContext)
                .getSystemServiceName(eq(TelephonyManager.class));
        doReturn(mockTelephonyManager).when(mockContext)
                .getSystemService(eq(Context.TELEPHONY_SERVICE));
        doReturn(TelephonyManager.SIM_STATE_UNKNOWN).when(mockTelephonyManager)
                .getSimApplicationState(anyInt());
        doReturn(mockContext2).when(mockContext).createConfigurationContext(any());
        CellBroadcastSettings.getResources(
                mockContext, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID - 1);

        verify(mockContext, times(1)).getSystemService(anyString());
        verify(mockContext, times(3)).getResources();


        doReturn(TelephonyManager.SIM_STATE_LOADED).when(mockTelephonyManager)
                .getSimApplicationState(anyInt());
        CellBroadcastSettings.getResources(
                mockContext, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID - 2);

        verify(mockContext, times(3)).getResources();
        verify(mockSubManager, times(1)).getActiveSubscriptionInfo(anyInt());
        verify(mockContext2, times(1)).getResources();

        doThrow(NullPointerException.class).when(mockTelephonyManager)
                .getSimApplicationState(anyInt());
        CellBroadcastSettings.getResources(
                mockContext, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID - 2);

        verify(mockContext, times(4)).getResources();
        verify(mockSubManager, times(1)).getActiveSubscriptionInfo(anyInt());
        verify(mockContext2, times(1)).getResources();
    }

    @Test
    public void testGetResourcesByOperator() {
        Context mockContext = mock(Context.class);
        Resources mockResources = mock(Resources.class);
        doReturn(mockResources).when(mockContext).getResources();

        CellBroadcastSettings.getResourcesByOperator(mockContext,
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, "");
        verify(mockContext, never()).createConfigurationContext(any());
        verify(mockContext, times(1)).getResources();

        int mcc = 123;
        int mnc = 456;
        Context mockContext2 = mock(Context.class);
        ArgumentCaptor<Configuration> captorConfig = ArgumentCaptor.forClass(Configuration.class);
        doReturn(mockResources).when(mockContext2).getResources();
        doReturn(mockContext2).when(mockContext).createConfigurationContext(any());

        CellBroadcastSettings.getResourcesByOperator(mockContext,
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                Integer.toString(mcc) + Integer.toString(mnc));
        verify(mockContext, times(1)).getResources();
        verify(mockContext2, times(1)).getResources();
        verify(mockContext, times(1)).createConfigurationContext(captorConfig.capture());
        assertEquals(mcc, captorConfig.getValue().mcc);
        assertEquals(mnc, captorConfig.getValue().mnc);
    }

    public void waitUntilDialogOpens(Runnable r, long maxWaitMs) {
        long waitTime = 0;
        while (waitTime < maxWaitMs) {
            try {
                r.run();
                // if the assert succeeds, return
                return;
            } catch (Exception e) {
                waitTime += 100;
                waitForMs(100);
            }
        }
        // if timed out, run one last time without catching exception
        r.run();
    }

    @Override
    protected Intent createActivityIntent() {
        Intent intent = new Intent(mContext, CellBroadcastSettings.class);
        intent.setPackage("com.android.cellbroadcastreceiver");
        intent.setAction("android.intent.action.MAIN");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private void openAlertReminderDialog() {
        onView(withText(mContext.getString(com.android.cellbroadcastreceiver.R
                .string.alert_reminder_interval_title))).perform(click());
    }
}
