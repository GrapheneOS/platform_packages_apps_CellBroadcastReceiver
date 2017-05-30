/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.cellbroadcastreceiver;

import android.app.Activity;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserManager;
import android.provider.Settings;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.TwoStatePreference;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Settings activity for the cell broadcast receiver.
 */
public class CellBroadcastSettings extends Activity {

    private static final String TAG = "CellBroadcastSettings";

    private static final boolean DBG = false;

    // Preference key for whether to enable emergency notifications (default enabled).
    public static final String KEY_ENABLE_EMERGENCY_ALERTS = "enable_emergency_alerts";

    // Enable vibration on alert (unless master volume is silent).
    public static final String KEY_ENABLE_ALERT_VIBRATE = "enable_alert_vibrate";

    // Speak contents of alert after playing the alert sound.
    public static final String KEY_ENABLE_ALERT_SPEECH = "enable_alert_speech";

    // Always play at full volume when playing the alert sound.
    public static final String KEY_USE_FULL_VOLUME = "use_full_volume";

    // Preference category for emergency alert and CMAS settings.
    public static final String KEY_CATEGORY_EMERGENCY_ALERTS = "category_emergency_alerts";

    // Preference category for alert preferences.
    public static final String KEY_CATEGORY_ALERT_PREFERENCES = "category_alert_preferences";

    // Whether to display CMAS extreme threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS =
            "enable_cmas_extreme_threat_alerts";

    // Whether to display CMAS severe threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS =
            "enable_cmas_severe_threat_alerts";

    // Whether to display CMAS amber alert messages (default is enabled).
    public static final String KEY_ENABLE_CMAS_AMBER_ALERTS = "enable_cmas_amber_alerts";

    // Preference category for development settings (enabled by settings developer options toggle).
    public static final String KEY_CATEGORY_DEV_SETTINGS = "category_dev_settings";

    // Whether to display ETWS test messages (default is disabled).
    public static final String KEY_ENABLE_ETWS_TEST_ALERTS = "enable_etws_test_alerts";

    // Whether to display CMAS monthly test messages (default is disabled).
    public static final String KEY_ENABLE_CMAS_TEST_ALERTS = "enable_cmas_test_alerts";

    // Preference key for whether to enable channel 50 notifications
    // Enabled by default for phones sold in Brazil, otherwise this setting may be hidden.
    public static final String KEY_ENABLE_CHANNEL_50_ALERTS = "enable_channel_50_alerts";

    // Preference key for initial opt-in/opt-out dialog.
    public static final String KEY_SHOW_CMAS_OPT_OUT_DIALOG = "show_cmas_opt_out_dialog";

    // Alert reminder interval ("once" = single 2 minute reminder).
    public static final String KEY_ALERT_REMINDER_INTERVAL = "alert_reminder_interval";

    // Preference key for emergency alerts history
    public static final String KEY_EMERGENCY_ALERT_HISTORY = "emergency_alert_history";

    // Brazil country code
    private static final String COUNTRY_BRAZIL = "br";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)) {
            setContentView(R.layout.cell_broadcast_disallowed_preference_screen);
            return;
        }

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new CellBroadcastSettingsFragment()).commit();
    }

    /**
     * New fragment-style implementation of preferences.
     */
    public static class CellBroadcastSettingsFragment extends PreferenceFragment {

        private TwoStatePreference mExtremeCheckBox;
        private TwoStatePreference mSevereCheckBox;
        private TwoStatePreference mAmberCheckBox;
        private TwoStatePreference mEmergencyCheckBox;
        private ListPreference mReminderInterval;
        private TwoStatePreference mSpeechCheckBox;
        private TwoStatePreference mFullVolumeCheckBox;
        private TwoStatePreference mEtwsTestCheckBox;
        private TwoStatePreference mChannel50CheckBox;
        private TwoStatePreference mCmasTestCheckBox;
        private Preference mAlertHistory;
        private PreferenceCategory mAlertCategory;
        private PreferenceCategory mAlertPreferencesCategory;
        private PreferenceCategory mDevSettingCategory;
        private boolean mDisableSevereWhenExtremeDisabled = true;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            PreferenceScreen preferenceScreen = getPreferenceScreen();

            mExtremeCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS);
            mSevereCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS);
            mAmberCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_CMAS_AMBER_ALERTS);
            mEmergencyCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_EMERGENCY_ALERTS);
            mReminderInterval = (ListPreference)
                    findPreference(KEY_ALERT_REMINDER_INTERVAL);
            mSpeechCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_ALERT_SPEECH);
            mFullVolumeCheckBox = (TwoStatePreference)
                    findPreference(KEY_USE_FULL_VOLUME);
            mEtwsTestCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_ETWS_TEST_ALERTS);
            mChannel50CheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_CHANNEL_50_ALERTS);
            mCmasTestCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_CMAS_TEST_ALERTS);
            mAlertHistory = findPreference(KEY_EMERGENCY_ALERT_HISTORY);
            mAlertCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_EMERGENCY_ALERTS);
            mAlertPreferencesCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_ALERT_PREFERENCES);
            mDevSettingCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_DEV_SETTINGS);

            mDisableSevereWhenExtremeDisabled = isFeatureEnabled(getContext(),
                    CarrierConfigManager.KEY_DISABLE_SEVERE_WHEN_EXTREME_DISABLED_BOOL, true);

            // Handler for settings that require us to reconfigure enabled channels in radio
            Preference.OnPreferenceChangeListener startConfigServiceListener =
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference pref, Object newValue) {
                            CellBroadcastReceiver.startConfigService(pref.getContext());

                            if (mDisableSevereWhenExtremeDisabled) {
                                if (pref.getKey().equals(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS)) {
                                    boolean isExtremeAlertChecked = (Boolean) newValue;
                                    if (mSevereCheckBox != null) {
                                        mSevereCheckBox.setEnabled(isExtremeAlertChecked);
                                        mSevereCheckBox.setChecked(false);
                                    }
                                }
                            }

                            if (pref.getKey().equals(KEY_ENABLE_EMERGENCY_ALERTS)) {
                                boolean isEnableAlerts = (Boolean) newValue;
                                setAlertsEnabled(isEnableAlerts);
                            }

                            // Notify backup manager a backup pass is needed.
                            new BackupManager(getContext()).dataChanged();
                            return true;
                        }
                    };

            // Show extra settings when developer options is enabled in settings.
            boolean enableDevSettings = Settings.Global.getInt(getContext().getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;

            Resources res = getResources();

            initReminderIntervalList();

            boolean forceDisableEtwsCmasTest = CellBroadcastSettings.isFeatureEnabled(getContext(),
                    CarrierConfigManager.KEY_CARRIER_FORCE_DISABLE_ETWS_CMAS_TEST_BOOL, false);

            boolean emergencyAlertOnOffOptionEnabled = isFeatureEnabled(getContext(),
                    CarrierConfigManager.KEY_ALWAYS_SHOW_EMERGENCY_ALERT_ONOFF_BOOL, false);

            if (enableDevSettings || emergencyAlertOnOffOptionEnabled) {
                // enable/disable all alerts except CMAS presidential alerts.
                if (mEmergencyCheckBox != null) {
                    mEmergencyCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
                }
                // If allow alerts are disabled, we turn all sub-alerts off. If it's enabled, we
                // leave them as they are.
                if (!mEmergencyCheckBox.isChecked()) {
                    setAlertsEnabled(false);
                }
            } else {
                preferenceScreen.removePreference(mEmergencyCheckBox);
            }

            // Show alert settings and ETWS categories for ETWS builds and developer mode.
            if (enableDevSettings) {
                if (forceDisableEtwsCmasTest) {
                    if (mDevSettingCategory != null) {
                        // Remove ETWS test preference.
                        mDevSettingCategory.removePreference(mEtwsTestCheckBox);
                        // Remove CMAS test preference.
                        mDevSettingCategory.removePreference(mCmasTestCheckBox);
                    }
                }
            } else {
                preferenceScreen.removePreference(mDevSettingCategory);
            }

            if (!res.getBoolean(R.bool.show_cmas_settings)) {
                // Remove CMAS preference items in emergency alert category.
                mAlertCategory.removePreference(mExtremeCheckBox);
                mAlertCategory.removePreference(mSevereCheckBox);
                mAlertCategory.removePreference(mAmberCheckBox);
            }

            TelephonyManager tm = (TelephonyManager) getContext().getSystemService(
                    Context.TELEPHONY_SERVICE);

            // We display channel 50 enable/disable menu if one of the followings is true
            // 1. The setting through resource overlay is set to true.
            // 2. At least one SIM inserted is Brazilian SIM.

            boolean enableChannel50Support = res.getBoolean(R.bool.show_brazil_settings);

            if (!enableChannel50Support) {
                SubscriptionManager sm = SubscriptionManager.from(getContext());
                for (int subId : sm.getActiveSubscriptionIdList()) {
                    if (COUNTRY_BRAZIL.equals(tm.getSimCountryIso(subId))) {
                        enableChannel50Support = true;
                        break;
                    }
                }
            }

            if (!enableChannel50Support) {
                mAlertCategory.removePreference(mChannel50CheckBox);
            }

            if (mChannel50CheckBox != null) {
                mChannel50CheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mEtwsTestCheckBox != null) {
                mEtwsTestCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mExtremeCheckBox != null) {
                mExtremeCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            if (mSevereCheckBox != null) {
                mSevereCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
                if (mDisableSevereWhenExtremeDisabled) {
                    if (mExtremeCheckBox != null) {
                        mSevereCheckBox.setEnabled(mExtremeCheckBox.isChecked());
                    }
                }
            }
            if (mAmberCheckBox != null) {
                mAmberCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mCmasTestCheckBox != null) {
                mCmasTestCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            if (mAlertHistory != null) {
                mAlertHistory.setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(final Preference preference) {
                                final Intent intent = new Intent(getContext(),
                                        CellBroadcastListActivity.class);
                                startActivity(intent);
                                return true;
                            }
                        });
            }
        }

        private void initReminderIntervalList() {

            String[] activeValues =
                    getResources().getStringArray(R.array.alert_reminder_interval_active_values);
            String[] allEntries =
                    getResources().getStringArray(R.array.alert_reminder_interval_entries);
            String[] newEntries = new String[activeValues.length];

            // Only add active interval to the list
            for (int i = 0; i < activeValues.length; i++) {
                int index = mReminderInterval.findIndexOfValue(activeValues[i]);
                if (index != -1) {
                    newEntries[i] = allEntries[index];
                    if (DBG) Log.d(TAG, "Added " + allEntries[index]);
                } else {
                    Log.e(TAG, "Can't find " + activeValues[i]);
                }
            }

            mReminderInterval.setEntries(newEntries);
            mReminderInterval.setEntryValues(activeValues);
            mReminderInterval.setSummary(mReminderInterval.getEntry());
            mReminderInterval.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference pref, Object newValue) {
                            final ListPreference listPref = (ListPreference) pref;
                            final int idx = listPref.findIndexOfValue((String) newValue);
                            listPref.setSummary(listPref.getEntries()[idx]);
                            return true;
                        }
                    });
        }


        private void setAlertsEnabled(boolean alertsEnabled) {
            if (mSevereCheckBox != null) {
                mSevereCheckBox.setEnabled(alertsEnabled);
                mSevereCheckBox.setChecked(alertsEnabled);
            }
            if (mExtremeCheckBox != null) {
                mExtremeCheckBox.setEnabled(alertsEnabled);
                mExtremeCheckBox.setChecked(alertsEnabled);
            }
            if (mAmberCheckBox != null) {
                mAmberCheckBox.setEnabled(alertsEnabled);
                mAmberCheckBox.setChecked(alertsEnabled);
            }
            if (mChannel50CheckBox != null) {
                mChannel50CheckBox.setEnabled(alertsEnabled);
                mChannel50CheckBox.setChecked(alertsEnabled);
            }
            if (mAlertPreferencesCategory != null) {
                mAlertPreferencesCategory.setEnabled(alertsEnabled);
            }
            if (mDevSettingCategory != null) {
                mDevSettingCategory.setEnabled(alertsEnabled);
            }
        }
    }

    public static boolean isFeatureEnabled(Context context, String feature, boolean defaultValue) {
        int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            subId = SubscriptionManager.getDefaultSubscriptionId();
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return defaultValue;
            }
        }

        CarrierConfigManager configManager =
                (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);

        if (configManager != null) {
            PersistableBundle carrierConfig = configManager.getConfigForSubId(subId);

            if (carrierConfig != null) {
                return carrierConfig.getBoolean(feature, defaultValue);
            }
        }

        return defaultValue;
    }
}
