/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.content.res.Resources;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;

import com.android.cellbroadcastreceiver.CellBroadcastResources;
import com.android.internal.telephony.gsm.SmsCbConstants;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;

public class CellBroadcastResourcesTest {

    @Mock
    private Context mContext;

    @Mock
    private Resources mResources;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mResources).when(mContext).getResources();
        String stringResultToReturn = "";
        doReturn(stringResultToReturn).when(mResources).getString(anyInt());
    }

    @Test
    public void testGetMessageDetails() {
        SmsCbMessage smsCbMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3, 4, 5, 6),
                0, 1);
        CharSequence details = CellBroadcastResources.getMessageDetails(mContext, true,
                smsCbMessage, -1, false,
                null);
        assertNotNull(details);
    }

    @Test
    public void testGetMessageDetailsCmasMessage() {
        SmsCbMessage smsCbMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3, 4, 5, 6),
                0, 1);
        CharSequence details = CellBroadcastResources.getMessageDetails(mContext, true,
                smsCbMessage, -1, false,
                null);
        assertNotNull(details);
    }

    @Test
    public void testGetCmasCategoryResId() throws Exception {
        int[] cats = {SmsCbCmasInfo.CMAS_CATEGORY_GEO, SmsCbCmasInfo.CMAS_CATEGORY_MET,
                SmsCbCmasInfo.CMAS_CATEGORY_SAFETY, SmsCbCmasInfo.CMAS_CATEGORY_SECURITY,
                SmsCbCmasInfo.CMAS_CATEGORY_RESCUE, SmsCbCmasInfo.CMAS_CATEGORY_FIRE,
                SmsCbCmasInfo.CMAS_CATEGORY_HEALTH, SmsCbCmasInfo.CMAS_CATEGORY_ENV,
                SmsCbCmasInfo.CMAS_CATEGORY_TRANSPORT, SmsCbCmasInfo.CMAS_CATEGORY_INFRA,
                SmsCbCmasInfo.CMAS_CATEGORY_CBRNE, SmsCbCmasInfo.CMAS_CATEGORY_OTHER};
        for (int c : cats) {
            assertNotEquals(0, getCmasCategoryResId(new SmsCbCmasInfo(0, c, 0, 0, 0, 0)));
        }

        assertEquals(0, getCmasCategoryResId(new SmsCbCmasInfo(
                0, SmsCbCmasInfo.CMAS_CATEGORY_UNKNOWN, 0, 0, 0, 0)));
    }

    @Test
    public void testGetCmasResponseResId() throws Exception {
        int[] resps = {SmsCbCmasInfo.CMAS_RESPONSE_TYPE_SHELTER,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_EVACUATE,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_PREPARE,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_EXECUTE,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_MONITOR,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_AVOID,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_ASSESS,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_NONE};
        for (int r : resps) {
            assertNotEquals(0, getCmasResponseResId(new SmsCbCmasInfo(0, 0, r, 0, 0, 0)));
        }

        assertEquals(0, getCmasResponseResId(new SmsCbCmasInfo(
                0, 0, SmsCbCmasInfo.CMAS_RESPONSE_TYPE_UNKNOWN, 0, 0, 0)));
    }

    private int getCmasCategoryResId(SmsCbCmasInfo info) throws Exception {
        Method method = CellBroadcastResources.class.getDeclaredMethod(
                "getCmasCategoryResId", SmsCbCmasInfo.class);
        method.setAccessible(true);
        return (int) method.invoke(null, info);
    }

    private int getCmasResponseResId(SmsCbCmasInfo info) throws Exception {
        Method method = CellBroadcastResources.class.getDeclaredMethod(
                "getCmasResponseResId", SmsCbCmasInfo.class);
        method.setAccessible(true);
        return (int) method.invoke(null, info);
    }
}
