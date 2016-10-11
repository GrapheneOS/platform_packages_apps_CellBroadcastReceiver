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
 * limitations under the License
 */

package com.android.cellbroadcastreceiver;

import android.content.Intent;
import android.provider.Telephony;
import android.telephony.CellBroadcastMessage;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;

import com.android.internal.telephony.gsm.SmsCbConstants;

import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;

import static com.android.cellbroadcastreceiver.CellBroadcastAlertAudio.ALERT_AUDIO_TONE_TYPE;
import static com.android.cellbroadcastreceiver.CellBroadcastAlertService.SHOW_NEW_ALERT_ACTION;

public class CellBroadcastAlertServiceTest extends
        CellBroadcastServiceTestCase<CellBroadcastAlertService> {

    public CellBroadcastAlertServiceTest() {
        super(CellBroadcastAlertService.class);
    }

    static SmsCbMessage createMessage() {
        return new SmsCbMessage(1, 2, 3, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(1, 2, 3, 4, 5, 6));
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private static void compareCellBroadCastMessage(CellBroadcastMessage cbm1,
                                                    CellBroadcastMessage cbm2) {
        assertEquals(cbm1.getCmasMessageClass(), cbm2.getCmasMessageClass());
        assertEquals(cbm1.getCmasWarningInfo(), cbm2.getCmasWarningInfo());
        assertEquals(cbm1.getEtwsWarningInfo(), cbm2.getEtwsWarningInfo());
        assertEquals(cbm1.getLanguageCode(), cbm2.getLanguageCode());
        assertEquals(cbm1.getMessageBody(), cbm2.getMessageBody());
        assertEquals(cbm1.getSerialNumber(), cbm2.getSerialNumber());
        assertEquals(cbm1.getServiceCategory(), cbm2.getServiceCategory());
        assertEquals(cbm1.getSubId(), cbm2.getSubId());
        assertEquals(cbm1.getSerialNumber(), cbm2.getSerialNumber());
    }

    // Test handleCellBroadcastIntent method
    public void testHandleCellBroadcastIntent() throws Exception {
        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);

        SmsCbMessage m = createMessage();
        intent.putExtra("message", m);

        startService(intent);
        waitForMs(200);

        assertEquals(SHOW_NEW_ALERT_ACTION, mServiceIntentToVerify.getAction());
        assertEquals(CellBroadcastAlertService.class.getName(),
                intent.getComponent().getClassName());

        CellBroadcastMessage cbmTest =
                (CellBroadcastMessage) mServiceIntentToVerify.getExtras().get("message");
        CellBroadcastMessage cbm = new CellBroadcastMessage(m);

        compareCellBroadCastMessage(cbm, cbmTest);
    }

    // Test showNewAlert method
    public void testShowNewAlert() throws Exception {

        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(SHOW_NEW_ALERT_ACTION);

        SmsCbMessage message = createMessage();
        intent.putExtra("message", new CellBroadcastMessage(message));

        startService(intent);
        waitForMs(200);

        // verify audio service intent
        assertEquals(CellBroadcastAlertAudio.ACTION_START_ALERT_AUDIO,
                mServiceIntentToVerify.getAction());
        assertEquals(CellBroadcastAlertAudio.ToneType.CMAS_DEFAULT,
                mServiceIntentToVerify.getSerializableExtra(ALERT_AUDIO_TONE_TYPE));
        assertEquals(message.getMessageBody(),
                mServiceIntentToVerify.getStringExtra(
                        CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY));

        // verify alert dialog activity intent
        ArrayList<CellBroadcastMessage> newMessageList = mActivityIntentToVerify
                .getParcelableArrayListExtra(CellBroadcastMessage.SMS_CB_MESSAGE_EXTRA);
        assertEquals(1, newMessageList.size());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK,
                (mActivityIntentToVerify.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK));
        compareCellBroadCastMessage(new CellBroadcastMessage(message), newMessageList.get(0));
    }
}