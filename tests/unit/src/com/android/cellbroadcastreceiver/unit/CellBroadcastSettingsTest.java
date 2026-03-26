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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.TypedValue;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.uiautomator.UiDevice;

import com.android.cellbroadcastreceiver.CellBroadcastChannelManager;
import com.android.cellbroadcastreceiver.CellBroadcastConfigService;
import com.android.cellbroadcastreceiver.CellBroadcastReceiver;
import com.android.cellbroadcastreceiver.CellBroadcastSettings;
import com.android.cellbroadcastreceiver.R;
import com.android.internal.telephony.CellBroadcastUtils;
import com.android.modules.utils.build.SdkLevel;
import com.android.settingslib.widget.SettingsThemeHelper;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Locale;


public class CellBroadcastSettingsTest extends
        CellBroadcastActivityTestCase<CellBroadcastSettings> {

    private static final String TAG = "CellBroadcastSettingsTest";

    private UiDevice mDevice;
    private boolean mIsWatch;
    private static final long DEVICE_WAIT_TIME = 1000L;
    private static final String ROAMING_OPERATOR_SUPPORTED = "roaming_operator_supported";
    private static final String ACTION_TESTING_MODE_CHANGED =
            "com.android.cellbroadcastreceiver.intent.ACTION_TESTING_MODE_CHANGED";
    private static final String TESTING_MODE = "testing_mode";
    private static final String MASTER_TOGGLE_ENABLED = "enable_alerts_master_toggle";
    private static final int PREFERENCE_PUT_TYPE_BOOL = 0;
    private static final int PREFERENCE_PUT_TYPE_STRING = 1;
    private static final long TEST_TIMEOUT_MILLIS = 1000L;

    @Captor
    private ArgumentCaptor<Intent> mIntent;
    @Mock
    private UserManager mUserManager;
    @Mock
    private SharedPreferences mMockedSharedPreference;
    @Mock
    private SharedPreferences.Editor mEditor;

    FakeSharedPreferences mFakeSharedPreferences = new FakeSharedPreferences();

    CellBroadcastReceiver.ActivityManagerProxy mTestActivityManagerProxy;

    CellBroadcastReceiver.ActivityManagerProxy mBackupActivityManagerProxy;

    public CellBroadcastSettingsTest() {
        super(CellBroadcastSettings.class);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        MockitoAnnotations.initMocks(this);
        CellBroadcastSettings.resetResourcesCache();
        SubscriptionManager mockSubManager = mock(SubscriptionManager.class);
        injectSystemService(SubscriptionManager.class, mockSubManager);
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mockSubManager).getActiveSubscriptionInfo(anyInt());
        mBackupActivityManagerProxy = CellBroadcastReceiver.sActivityManagerProxy;
        mTestActivityManagerProxy = mock(CellBroadcastReceiver.ActivityManagerProxy.class);
        CellBroadcastReceiver.sActivityManagerProxy = mTestActivityManagerProxy;
        mIsWatch = mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    @After
    public void tearDown() throws Exception {
        CellBroadcastSettings.resetResourcesCache();
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
        CellBroadcastReceiver.sActivityManagerProxy = mBackupActivityManagerProxy;
        super.tearDown();
    }

    @InstrumentationTest
    // This test has a module dependency, so it is disabled for OEM testing because it is not a true
    // unit test
    @FlakyTest
    @Test
    public void testRotateAlertReminderDialogOpen() throws InterruptedException {
        if (mIsWatch) {
            return;
        }

        try {
            mDevice.wakeUp();
            mDevice.pressMenu();
        } catch (RemoteException exception) {
            Assert.fail("Exception " + exception);
        }

        InstrumentationRegistry.getInstrumentation().startActivitySync(createActivityIntent());
        int w = mDevice.getDisplayWidth();
        int h = mDevice.getDisplayHeight();

        waitUntilDialogOpens(() -> {
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
        mContext.injectSharedPreferences(mFakeSharedPreferences);
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
                        .getBoolean(CellBroadcastSettings.KEY_RECEIVE_CMAS_IN_SECOND_LANGUAGE,
                                true));
        assertTrue("enable_alert_vibrate was not reset to the default (true)",
                PreferenceManager.getDefaultSharedPreferences(mContext)
                        .getBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_VIBRATE, false));
    }

    @Test
    public void testHasAnyPreferenceChanged() throws Throwable {
        mContext.injectSharedPreferences(mFakeSharedPreferences);
        assertFalse(CellBroadcastSettings.hasAnyPreferenceChanged(mContext));
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putBoolean("any_preference_changed_by_user", true).apply();
        assertTrue(CellBroadcastSettings.hasAnyPreferenceChanged(mContext));

        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.show_alert_speech_setting);
        doReturn(false).when(mContext.getResources()).getBoolean(
                R.bool.enable_alert_speech_default);

        CellBroadcastSettings.resetAllPreferences(mContext);
        assertFalse(CellBroadcastSettings.hasAnyPreferenceChanged(mContext));

        CellBroadcastSettings settings = startActivity();
        waitForMs(100);

        TwoStatePreference speechCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_ALERT_SPEECH);
        assertNotNull(speechCheckBox);

        speechCheckBox.performClick();
        assertTrue(CellBroadcastSettings.hasAnyPreferenceChanged(mContext));
    }

    @Test
    public void testPreferenceChangeByUser() {
        Context mockContext = mock(Context.class);
        Looper.prepare();
        CellBroadcastSettings.CellBroadcastSettingsFragment fragment =
                new CellBroadcastSettings.CellBroadcastSettingsFragment();
        doReturn(mUserManager).when(mockContext).getSystemService(Context.USER_SERVICE);
        setCurrentUser(true);
        doReturn(mMockedSharedPreference).when(mockContext).getSharedPreferences(anyString(),
                anyInt());
        doReturn(mEditor).when(mMockedSharedPreference).edit();
        doReturn(mEditor).when(mEditor).putBoolean(anyString(), anyBoolean());

        fragment.onPreferenceChangedByUser(mockContext, false);
        verify(mockContext, times(0)).startService(mIntent.capture());

        fragment.onPreferenceChangedByUser(mockContext, true);

        verify(mockContext, times(1)).startService(mIntent.capture());
        assertEquals(CellBroadcastConfigService.ACTION_ENABLE_CHANNELS,
                (String) mIntent.getValue().getAction());
    }

    @Test
    public void testGetResources() {
        Context mockContext = mock(Context.class);
        Resources mockResources = mock(Resources.class);
        Configuration configuration = new Configuration();
        doReturn(mockResources).when(mockContext).getResources();
        doReturn(configuration).when(mockResources).getConfiguration();

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
        doReturn(Context.TELEPHONY_SUBSCRIPTION_SERVICE).when(mockContext)
                .getSystemServiceName(eq(SubscriptionManager.class));
        doReturn(mockSubManager).when(mockContext).getSystemService(
                eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE));
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mockSubManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0).when(mockSubInfo).getMcc();
        doReturn(0).when(mockSubInfo).getMnc();
        doReturn(mockContext2).when(mockContext).createConfigurationContext(any());

        // The resource will not be cached for the sub
        CellBroadcastSettings.getResources(
                mockContext, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID - 1);

        verify(mockContext, times(1)).createConfigurationContext(any());
        verify(mockContext2, times(1)).getResources();

        // The resources will be cached for ths sub
        doReturn(123).when(mockSubInfo).getMcc();
        doReturn(456).when(mockSubInfo).getMnc();
        // The cache logic is updated on S
        final int timesExpected = SdkLevel.isAtLeastS() ? 2 : 1;

        CellBroadcastSettings.getResources(
                mockContext, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID - 1);

        verify(mockContext, times(timesExpected)).createConfigurationContext(any());
        verify(mockContext2, times(timesExpected)).getResources();

        // The resources should be read from the cached directly
        CellBroadcastSettings.getResources(
                mockContext, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID - 1);

        verify(mockContext, times(timesExpected)).createConfigurationContext(any());
        verify(mockContext2, times(timesExpected)).getResources();

        Configuration configuration2 = new Configuration();
        configuration2.setLocale(Locale.ROOT);
        doReturn(configuration2).when(mockResources).getConfiguration();
        CellBroadcastSettings.getResources(
                mockContext, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID - 2);

        verify(mockContext, times(timesExpected + 1)).createConfigurationContext(any());
        verify(mockContext2, times(timesExpected + 1)).getResources();
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
        String packageName = CellBroadcastUtils
                .getDefaultCellBroadcastReceiverPackageName(mContext);
        int resId = mContext.getResources().getIdentifier("alert_reminder_interval_title",
                "string", packageName);
        onView(withText(resId)).perform(click());
    }

    @Test
    public void testDisabledExtremeToggle() throws Throwable {
        SubscriptionManager mockSubManager = mock(SubscriptionManager.class);
        injectSystemService(SubscriptionManager.class, mockSubManager);
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mockSubManager).getActiveSubscriptionInfo(anyInt());

        setPreference(PREFERENCE_PUT_TYPE_BOOL, MASTER_TOGGLE_ENABLED, "true");
        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.extreme_threat_alerts_enabled_default);
        doReturn(false).when(mContext.getResources()).getBoolean(
                R.bool.disable_extreme_alert_settings);

        CellBroadcastSettings settings = startActivity();
        waitForMs(100);

        TwoStatePreference extremeCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS);
        assertNotNull(extremeCheckBox);
        assertTrue(extremeCheckBox.isEnabled());

        stopActivity();
        waitForMs(100);

        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.disable_extreme_alert_settings);

        if (isHideToolbar()) {
            settings.mCellBroadcastSettingsOldFragment.initAlertsToggleDisabledAsNeeded();
            settings.mCellBroadcastSettingsOldFragment.onResume();
        } else {
            settings.mCellBroadcastSettingsFragment.initAlertsToggleDisabledAsNeeded();
            settings.mCellBroadcastSettingsFragment.onResume();
        }

        assertFalse(extremeCheckBox.isEnabled());
    }

    @Test
    public void testTopIntroductionForRoamingSupport() throws Throwable {
        if (mIsWatch) {
            return;
        }
        String topIntroRoamingText = "test";
        String packageName = CellBroadcastUtils
            .getDefaultCellBroadcastReceiverPackageName(mContext);
        int resId = mContext.getResources().getIdentifier(
            "top_intro_roaming_text", "string", packageName);
        doReturn(topIntroRoamingText).when(mContext.getResources()).getString(eq(resId));
        setPreference(PREFERENCE_PUT_TYPE_STRING, ROAMING_OPERATOR_SUPPORTED, "XXX");

        CellBroadcastSettings settings = startActivity();
        waitForMs(100);

        Preference topIntroPreference = getPreference(settings,
                CellBroadcastSettings.KEY_PREFS_TOP_INTRO);
        assertNotNull(topIntroPreference);
        assertEquals(topIntroRoamingText, topIntroPreference.getTitle().toString());
    }

    @Test
    public void testDoNotShowTestCheckBox() throws Throwable {
        setPreference(PREFERENCE_PUT_TYPE_BOOL, TESTING_MODE, "false");
        doReturn(false).when(mContext.getResources()).getBoolean(
                eq(R.bool.show_separate_exercise_settings));
        doReturn(false).when(mContext.getResources()).getBoolean(
                eq(R.bool.show_separate_operator_defined_settings));
        doReturn(new String[]{"0x111D:rat=gsm, emergency=true"}).when(mContext.getResources())
                .getStringArray(eq(R.array.exercise_alert_range_strings));
        doReturn(new String[]{"0x111E:rat=gsm, emergency=true"}).when(mContext.getResources())
                .getStringArray(eq(R.array.operator_defined_alert_range_strings));
        CellBroadcastSettings settings = startActivity();
        waitForMs(100);

        TwoStatePreference exerciseTestCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_EXERCISE_ALERTS);
        TwoStatePreference operatorDefinedCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_OPERATOR_DEFINED_ALERTS);

        assertNotNull(exerciseTestCheckBox);
        assertFalse(exerciseTestCheckBox.isVisible());
        assertNotNull(operatorDefinedCheckBox);
        assertFalse(operatorDefinedCheckBox.isVisible());
    }

    @Test
    public void testShowTestCheckBoxWithTestingMode() throws Throwable {
        setPreference(PREFERENCE_PUT_TYPE_BOOL, TESTING_MODE, "true");
        doReturn(true).when(mContext.getResources()).getBoolean(
                eq(R.bool.show_separate_exercise_settings));
        doReturn(true).when(mContext.getResources()).getBoolean(
                eq(R.bool.show_separate_operator_defined_settings));
        doReturn(new String[]{"0x111D:rat=gsm, emergency=true"}).when(mContext.getResources())
                .getStringArray(eq(R.array.exercise_alert_range_strings));
        doReturn(new String[]{"0x111E:rat=gsm, emergency=true"}).when(mContext.getResources())
                .getStringArray(eq(R.array.operator_defined_alert_range_strings));
        CellBroadcastSettings settings = startActivity();
        waitForMs(100);

        TwoStatePreference exerciseTestCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_EXERCISE_ALERTS);
        TwoStatePreference operatorDefinedCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_OPERATOR_DEFINED_ALERTS);

        assertNotNull(exerciseTestCheckBox);
        assertTrue(exerciseTestCheckBox.isVisible());
        assertNotNull(operatorDefinedCheckBox);
        assertTrue(operatorDefinedCheckBox.isVisible());
    }

    @Test
    public void testShowTestCheckBox() throws Throwable {
        setPreference(PREFERENCE_PUT_TYPE_BOOL, TESTING_MODE, "false");
        doReturn(true).when(mContext.getResources()).getBoolean(
                eq(R.bool.show_separate_exercise_settings));
        doReturn(true).when(mContext.getResources()).getBoolean(
                eq(R.bool.show_separate_operator_defined_settings));
        doReturn(true).when(mContext.getResources()).getBoolean(
                eq(R.bool.show_exercise_settings));
        doReturn(true).when(mContext.getResources()).getBoolean(
                eq(R.bool.show_operator_defined_settings));
        doReturn(new String[]{"0x111D:rat=gsm, emergency=true"}).when(mContext.getResources())
                .getStringArray(eq(R.array.exercise_alert_range_strings));
        doReturn(new String[]{"0x111E:rat=gsm, emergency=true"}).when(mContext.getResources())
                .getStringArray(eq(R.array.operator_defined_alert_range_strings));
        CellBroadcastSettings settings = startActivity();
        waitForMs(100);

        TwoStatePreference exerciseTestCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_EXERCISE_ALERTS);
        TwoStatePreference operatorDefinedCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_OPERATOR_DEFINED_ALERTS);

        assertNotNull(exerciseTestCheckBox);
        assertTrue(exerciseTestCheckBox.isVisible());
        assertNotNull(operatorDefinedCheckBox);
        assertTrue(operatorDefinedCheckBox.isVisible());
    }

    @Test
    public void testShowReceiveCmasInSecondLanguageToggle() throws Throwable {
        String title = "title";
        String summary = "summary";
        doReturn("es").when(mContext.getResources()).getString(
                eq(R.string.emergency_alert_second_language_code));
        doReturn(title).when(mContext.getResources()).getString(
                eq(R.string.receive_cmas_in_second_language_title));
        doReturn(summary).when(mContext.getResources()).getString(
                eq(R.string.receive_cmas_in_second_language_summary));
        CellBroadcastSettings settings = startActivity();
        waitForMs(100);

        TwoStatePreference receiveCmasInSecondLangCheckBox =
                (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_RECEIVE_CMAS_IN_SECOND_LANGUAGE);

        assertNotNull(receiveCmasInSecondLangCheckBox);
        assertTrue(receiveCmasInSecondLangCheckBox.isVisible());
        assertEquals(receiveCmasInSecondLangCheckBox.getTitle(), title);
        assertEquals(receiveCmasInSecondLangCheckBox.getSummary(), summary);
    }

    private void setPreference(int putType, String key, String value) {
        mContext.injectSharedPreferences(mFakeSharedPreferences);
        switch (putType) {
            case PREFERENCE_PUT_TYPE_BOOL:
                PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                        .putBoolean(key, Boolean.valueOf(value)).apply();
                break;
            case PREFERENCE_PUT_TYPE_STRING:
                PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                        .putString(key, value).apply();
                break;
        }
    }

    @Test
    public void testResetToggle() throws Throwable {
        doReturn(false).when(mContext.getResources()).getBoolean(
                R.bool.restore_sub_toggle_to_carrier_default);
        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.severe_threat_alerts_enabled_default);
        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.amber_alerts_enabled_default);
        doReturn(false).when(mContext.getResources()).getBoolean(
                R.bool.test_alerts_enabled_default);

        CellBroadcastSettings settings = startActivity();
        waitForMs(100);

        TwoStatePreference severeCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS);
        TwoStatePreference amberCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS);
        TwoStatePreference testCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_TEST_ALERTS);

        if (isHideToolbar()) {
            settings.mCellBroadcastSettingsOldFragment.setAlertsEnabled(false);
        } else {
            settings.mCellBroadcastSettingsFragment.setAlertsEnabled(false);
        }

        assertFalse(severeCheckBox.isChecked());
        assertFalse(amberCheckBox.isChecked());
        assertFalse(testCheckBox.isChecked());

        if (isHideToolbar()) {
            settings.mCellBroadcastSettingsOldFragment.setAlertsEnabled(true);
        } else {
            settings.mCellBroadcastSettingsFragment.setAlertsEnabled(true);
        }

        assertTrue(severeCheckBox.isChecked());
        assertTrue(amberCheckBox.isChecked());
        assertTrue(testCheckBox.isChecked());
    }

    @Test
    public void testNotifyAreaInfoUpdate() throws Throwable {
        doReturn(false).when(mContext.getResources()).getBoolean(
                R.bool.test_alerts_enabled_default);

        CellBroadcastSettings cellBroadcastSettingActivity = startActivity();
        waitForMs(100);

        Intent intent = null;
        for (int i = 0; i < 5; i++) {
            intent = mContext.mSendBroadcastIntent;
            if (isHideToolbar()) {
                cellBroadcastSettingActivity.mCellBroadcastSettingsOldFragment
                        .setAlertsEnabled(false);
            } else {
                cellBroadcastSettingActivity.mCellBroadcastSettingsFragment
                        .setAlertsEnabled(false);
            }
            if (intent != null) {
                break;
            }
            waitForMs(100);
        }
        assertEquals("com.android.cellbroadcastreceiver.action.AREA_UPDATE_INFO_ENABLED",
                mContext.mSendBroadcastIntent.getAction());
        assertEquals(UserHandle.SYSTEM, mContext.mUserHandle);
        assertEquals("com.android.cellbroadcastservice.FULL_ACCESS_CELL_BROADCAST_HISTORY",
                mContext.mReceiverPermission);
    }

    @Test
    public void testRestoreToggleToCarrierDefault() throws Throwable {
        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.restore_sub_toggle_to_carrier_default);
        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.severe_threat_alerts_enabled_default);
        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.amber_alerts_enabled_default);
        doReturn(false).when(mContext.getResources()).getBoolean(
                R.bool.test_alerts_enabled_default);

        CellBroadcastSettings settings = startActivity();
        waitForMs(100);

        TwoStatePreference severeCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS);
        TwoStatePreference amberCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS);
        TwoStatePreference testCheckBox = (TwoStatePreference) getPreference(settings,
                CellBroadcastSettings.KEY_ENABLE_TEST_ALERTS);

        if (isHideToolbar()) {
            settings.mCellBroadcastSettingsOldFragment.setAlertsEnabled(false);
        } else {
            settings.mCellBroadcastSettingsFragment.setAlertsEnabled(false);
        }

        assertFalse(severeCheckBox.isChecked());
        assertFalse(amberCheckBox.isChecked());
        assertFalse(testCheckBox.isChecked());

        if (isHideToolbar()) {
            settings.mCellBroadcastSettingsOldFragment.setAlertsEnabled(true);
        } else {
            settings.mCellBroadcastSettingsFragment.setAlertsEnabled(true);
        }

        assertTrue(severeCheckBox.isChecked());
        assertTrue(amberCheckBox.isChecked());
        assertFalse(testCheckBox.isChecked());
    }

    @InstrumentationTest
    @Test
    public void testFragmentCreationInOnCreateIfNoExistingFragmentToRestore() throws Throwable {
        try {
            mDevice.wakeUp();
            mDevice.pressMenu();
        } catch (RemoteException exception) {
            Assert.fail("Exception " + exception);
        }
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        CellBroadcastSettings activity =
                (CellBroadcastSettings) instrumentation.startActivitySync(
                        createActivityIntent());
        ComponentName activityComponentName = activity.getComponentName();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                activityComponentName.getClassName(), null, false);
        waitForMs(100);
        if (isHideToolbar()) {
            assertNotNull(activity.mCellBroadcastSettingsOldFragment);
        } else {
            assertNotNull(activity.mCellBroadcastSettingsFragment);
        }

        try {
            if (mIsWatch) {
                instrumentation.runOnMainSync(activity::recreate);
            } else {
                mDevice.setOrientationLeft();
            }

            CellBroadcastSettings newActivity =
                    (CellBroadcastSettings) instrumentation.waitForMonitorWithTimeout(
                            monitor, DEVICE_WAIT_TIME * 5);

            if (isHideToolbar()) {
                assertNull(newActivity.mCellBroadcastSettingsOldFragment);
            } else {
                assertNull(newActivity.mCellBroadcastSettingsFragment);
            }
            if (!isHideToolbar()) {
                mDevice.setOrientationNatural();
            }
            instrumentation.removeMonitor(monitor);
        } catch (Exception e) {
            Assert.fail("Exception " + e);
        }
    }

    @Test
    public void testCellBroadcastSettingsRuntimeThemeApplyOrNot() throws Throwable {
        CellBroadcastSettings cellBroadcastSettings = startActivity();
        waitForMs(100);

        int attrId = R.attr.isCellBroadcastSettingsRuntimeTheme;
        final Resources.Theme theme = cellBroadcastSettings.getTheme();
        final TypedValue typedValue = new TypedValue();
        if (!isHideToolbar() && !SettingsThemeHelper.isExpressiveTheme(mContext)) {
            assertTrue(theme.resolveAttribute(attrId, typedValue, true));
        } else {
            assertFalse(theme.resolveAttribute(attrId, typedValue, true));
        }
    }

    private boolean isHideToolbar() {
        // for backward compatibility on R devices or wearable devices due to small screen device.
        return !SdkLevel.isAtLeastS() || mIsWatch;
    }

    private void setCurrentUser(boolean currentUser) {
        if (SdkLevel.isAtLeastT()) {
            int userId = currentUser ? UserHandle.myUserId() : UserHandle.myUserId() + 1;
            doReturn(userId).when(mTestActivityManagerProxy).getCurrentUser();
        } else {
            doReturn(currentUser).when(mUserManager).isSystemUser();
        }
    }

    private Preference getPreference(CellBroadcastSettings activity, String key) {
        Preference checkBox = null;
        for (int i = 0; i < 5; i++) {
            if (isHideToolbar()) {
                checkBox = activity.mCellBroadcastSettingsOldFragment.findPreference(key);
            } else {
                checkBox = activity.mCellBroadcastSettingsFragment.findPreference(key);
            }
            if (checkBox != null) {
                break;
            }
            waitForMs(100);
        }
        return checkBox;
    }
}
