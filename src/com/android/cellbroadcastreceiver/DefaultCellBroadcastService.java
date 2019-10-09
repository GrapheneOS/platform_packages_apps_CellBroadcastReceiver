/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.telephony.CellBroadcastService;
import android.util.Log;

/**
 * The default implementation of CellBroadcastService, which is used for handling GSM and CDMA
 * cell broadcast messages.
 */
public class DefaultCellBroadcastService extends CellBroadcastService {
    private GsmCellBroadcastHandler mGsmCellBroadcastHandler;

    private static final String TAG = "DefaultCellBroadcastService";

    @Override
    public void onCreate() {
        super.onCreate();
        mGsmCellBroadcastHandler =
                GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(getApplicationContext());
    }

    @Override
    public void onGsmCellBroadcastSms(int slotIndex, byte[] message) {
        Log.d(TAG, "onGsmCellBroadcastSms received message on slotId=" + slotIndex);
        mGsmCellBroadcastHandler.onGsmCellBroadcastSms(slotIndex, message);
    }

    @Override
    public void onCdmaCellBroadcastSms(int slotIndex, byte[] message) {
        // TODO implement CDMA
    }
}
