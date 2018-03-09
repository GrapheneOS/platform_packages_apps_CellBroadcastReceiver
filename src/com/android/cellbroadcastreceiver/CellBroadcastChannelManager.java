/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.telephony.CellBroadcastMessage;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.cellbroadcastreceiver.CellBroadcastAlertService.AlertType;
import com.android.internal.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CellBroadcastChannelManager handles the additional cell broadcast channels that
 * carriers might enable through resources.
 * Syntax: "<channel id range>:[type=<alert type>], [emergency=true/false]"
 * For example,
 * <string-array name="additional_cbs_channels_strings" translatable="false">
 *     <item>"43008:type=earthquake, emergency=true"</item>
 *     <item>"0xAFEE:type=tsunami, emergency=true"</item>
 *     <item>"0xAC00-0xAFED:type=other"</item>
 *     <item>"1234-5678"</item>
 * </string-array>
 * If no tones are specified, the alert type will be set to DEFAULT. If emergency is not set,
 * by default it's not emergency.
 */
public class CellBroadcastChannelManager {

    private static final String TAG = "CellBroadcastChannelManager";

    private static CellBroadcastChannelManager sInstance = null;

    private static List<Integer> sCellBroadcastRangeResourceKeys = new ArrayList<>(
            Arrays.asList(R.array.additional_cbs_channels_strings,
                    R.array.emergency_alerts_channels_range_strings,
                    R.array.cmas_presidential_alerts_channels_range_strings,
                    R.array.cmas_alert_extreme_channels_range_strings,
                    R.array.cmas_alerts_severe_range_strings,
                    R.array.cmas_amber_alerts_channels_range_strings,
                    R.array.required_monthly_test_range_strings,
                    R.array.exercise_alert_range_strings,
                    R.array.operator_defined_alert_range_strings,
                    R.array.etws_alerts_range_strings,
                    R.array.etws_test_alerts_range_strings,
                    R.array.public_safety_messages_channels_range_strings
            ));
    /**
     * Cell broadcast channel range
     * A range is consisted by starting channel id, ending channel id, and the alert type
     */
    public static class CellBroadcastChannelRange {

        private static final String KEY_TYPE = "type";
        private static final String KEY_EMERGENCY = "emergency";
        private static final String KEY_RAT = "rat";
        private static final String KEY_SCOPE = "scope";
        private static final String KEY_VIBRATION = "vibration";

        public static final int SCOPE_UNKNOWN       = 0;
        public static final int SCOPE_CARRIER       = 1;
        public static final int SCOPE_DOMESTIC      = 2;
        public static final int SCOPE_INTERNATIONAL = 3;

        public int mStartId;
        public int mEndId;
        public AlertType mAlertType;
        public boolean mIsEmergency;
        public int mRat;
        public int mScope;
        public int[] mVibrationPattern;

        public CellBroadcastChannelRange(Context context, String channelRange) throws Exception {

            mAlertType = AlertType.DEFAULT;
            mIsEmergency = false;
            mRat = SmsManager.CELL_BROADCAST_RAN_TYPE_GSM;
            mScope = SCOPE_UNKNOWN;
            mVibrationPattern = context.getResources().getIntArray(
                    R.array.default_vibration_pattern);

            int colonIndex = channelRange.indexOf(':');
            if (colonIndex != -1) {
                // Parse the alert type and emergency flag
                String[] pairs = channelRange.substring(colonIndex + 1).trim().split(",");
                for (String pair : pairs) {
                    pair = pair.trim();
                    String[] tokens = pair.split("=");
                    if (tokens.length == 2) {
                        String key = tokens[0].trim();
                        String value = tokens[1].trim();
                        if (value == null) continue;
                        switch (key) {
                            case KEY_TYPE:
                                mAlertType = AlertType.valueOf(value.toUpperCase());
                                break;
                            case KEY_EMERGENCY:
                                mIsEmergency = value.equalsIgnoreCase("true");
                                break;
                            case KEY_RAT:
                                mRat = value.equalsIgnoreCase("cdma")
                                        ? SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA :
                                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM;
                                break;
                            case KEY_SCOPE:
                                if (value.equalsIgnoreCase("carrier")) {
                                    mScope = SCOPE_CARRIER;
                                } else if (value.equalsIgnoreCase("domestic")) {
                                    mScope = SCOPE_DOMESTIC;
                                } else if (value.equalsIgnoreCase("international")) {
                                    mScope = SCOPE_INTERNATIONAL;
                                }
                                break;
                            case KEY_VIBRATION:
                                String[] vibration = value.split("\\|");
                                if (!ArrayUtils.isEmpty(vibration)) {
                                    mVibrationPattern = new int[vibration.length];
                                    for (int i = 0; i < vibration.length; i++) {
                                        mVibrationPattern[i] = Integer.parseInt(vibration[i]);
                                    }
                                }
                                break;
                        }
                    }
                }
                channelRange = channelRange.substring(0, colonIndex).trim();
            }

            // Parse the channel range
            int dashIndex = channelRange.indexOf('-');
            if (dashIndex != -1) {
                // range that has start id and end id
                mStartId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                mEndId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
            } else {
                // Not a range, only a single id
                mStartId = mEndId = Integer.decode(channelRange);
            }
        }
    }

    /**
     * Get the instance of the cell broadcast other channel manager
     * @return The singleton instance
     */
    public static CellBroadcastChannelManager getInstance() {
        if (sInstance == null) {
            sInstance = new CellBroadcastChannelManager();
        }
        return sInstance;
    }

    /**
     * Get cell broadcast channels enabled by the carriers from resource key
     * @param context Application context
     * @param key Resource key
     * @return The list of channel ranges enabled by the carriers.
     */
    public static ArrayList<CellBroadcastChannelRange> getCellBroadcastChannelRanges(
            Context context, int key) {
        ArrayList<CellBroadcastChannelRange> result = new ArrayList<>();
        String[] ranges = context.getResources().getStringArray(key);

        if (ranges != null) {
            for (String range : ranges) {
                try {
                    result.add(new CellBroadcastChannelRange(context, range));
                } catch (Exception e) {
                    loge("Failed to parse \"" + range + "\". e=" + e);
                }
            }
        }

        return result;
    }

    /**
     * @param subId Subscription index
     * @param channel Cell broadcast message channel
     * @param context Application context
     * @param key Resource key
     * @return {@code TRUE} if the input channel is within the channel range defined from resource.
     * return {@code FALSE} otherwise
     */
    public static boolean checkCellBroadcastChannelRange(int subId, int channel, int key,
            Context context) {
        ArrayList<CellBroadcastChannelRange> ranges = CellBroadcastChannelManager
                .getInstance().getCellBroadcastChannelRanges(context, key);
        if (ranges != null) {
            for (CellBroadcastChannelRange range : ranges) {
                if (channel >= range.mStartId && channel <= range.mEndId) {
                    return checkScope(context, subId, range.mScope);
                }
            }
        }
        return false;
    }

    /**
     * Check if the channel scope matches the current network condition.
     *
     * @param subId Subscription id
     * @param rangeScope Range scope. Must be SCOPE_CARRIER, SCOPE_DOMESTIC, or SCOPE_INTERNATIONAL.
     * @return True if the scope matches the current network roaming condition.
     */
    public static boolean checkScope(Context context, int subId, int rangeScope) {
        if (rangeScope == CellBroadcastChannelRange.SCOPE_UNKNOWN) return true;
        if (context != null) {
            TelephonyManager tm =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            ServiceState ss = tm.getServiceStateForSubscriber(subId);
            if (ss != null) {
                if (ss.getVoiceRegState() == ServiceState.STATE_IN_SERVICE
                        || ss.getVoiceRegState() == ServiceState.STATE_EMERGENCY_ONLY) {
                    if (ss.getVoiceRoamingType() == ServiceState.ROAMING_TYPE_NOT_ROAMING) {
                        return true;
                    } else if (ss.getVoiceRoamingType() == ServiceState.ROAMING_TYPE_DOMESTIC
                            && rangeScope == CellBroadcastChannelRange.SCOPE_DOMESTIC) {
                        return true;
                    } else if (ss.getVoiceRoamingType() == ServiceState.ROAMING_TYPE_INTERNATIONAL
                            && rangeScope == CellBroadcastChannelRange.SCOPE_INTERNATIONAL) {
                        return true;
                    }
                    return false;
                }
            }
        }
        // If we can't determine the scope, for safe we should assume it's in.
        return true;
    }

    /**
     * Return corresponding cellbroadcast range where message belong to
     * @param context
     * @param message
     */
    public static CellBroadcastChannelRange getCellBroadcastChannelRangeFromMessage(
            Context context, CellBroadcastMessage message) {
        int subId = message.getSubId();
        int channel = message.getServiceCategory();
        ArrayList<CellBroadcastChannelRange> ranges = null;

        for (int key : sCellBroadcastRangeResourceKeys) {
            if (checkCellBroadcastChannelRange(subId, channel, key, context)) {
                ranges = getCellBroadcastChannelRanges(context, key);
                break;
            }
        }
        if (ranges != null) {
            for (CellBroadcastChannelRange range : ranges) {
                if (range.mStartId <= message.getServiceCategory()
                        && range.mEndId >= message.getServiceCategory()) {
                    return range;
                }
            }
        }
        return null;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
