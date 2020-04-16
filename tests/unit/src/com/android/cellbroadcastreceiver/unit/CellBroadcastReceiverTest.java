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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioDeviceInfo;
import android.os.UserManager;
import android.provider.Telephony;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;

import com.android.cellbroadcastreceiver.CellBroadcastAlertService;
import com.android.cellbroadcastreceiver.CellBroadcastReceiver;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CellBroadcastReceiverTest extends CellBroadcastTest {
    private static final long MAX_INIT_WAIT_MS = 5000;

    CellBroadcastReceiver mCellBroadcastReceiver;

    @Mock
    UserManager mUserManager;
    @Mock
    SharedPreferences mSharedPreferences;

    @Mock
    Intent mIntent;

    private Configuration mConfiguration = new Configuration();
    private AudioDeviceInfo[] mDevices = new AudioDeviceInfo[0];
    private Object mLock = new Object();
    private boolean mReady;

    protected void waitUntilReady() {
        synchronized (mLock) {
            if (!mReady) {
                try {
                    mLock.wait(MAX_INIT_WAIT_MS);
                } catch (InterruptedException ie) {
                }

                if (!mReady) {
                    Assert.fail("Telephony tests failed to initialize");
                }
            }
        }
    }

    protected void setReady(boolean ready) {
        synchronized (mLock) {
            mReady = ready;
            mLock.notifyAll();
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        doReturn(mConfiguration).when(mResources).getConfiguration();
        mCellBroadcastReceiver = spy(new CellBroadcastReceiver());
        doReturn(mContext).when(mContext).getApplicationContext();
        //return false in isSystemUser, so that system services are not initiated

    }

    @Test
    public void testOnReceive_actionMarkAsRead() {
        doReturn(CellBroadcastReceiver.ACTION_MARK_AS_READ).when(mIntent).getAction();
        doNothing().when(mCellBroadcastReceiver).getCellBroadcastTask(anyLong());
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mIntent).getLongExtra(CellBroadcastReceiver.EXTRA_DELIVERY_TIME, -1);
    }

    @Test
    public void testOnReceive_actionCarrierConfigChanged() {
        doReturn(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED).when(mIntent).getAction();
        doReturn(mUserManager).when(mContext).getSystemService(anyString());
        doReturn(false).when(mUserManager).isSystemUser();
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mCellBroadcastReceiver).initializeSharedPreference();
        verify(mContext, times(2)).getSystemService(anyString());
    }

    @Test
    public void testOnReceive_cellbroadcastStartConfigAction() {
        doReturn(CellBroadcastReceiver.CELLBROADCAST_START_CONFIG_ACTION).when(mIntent).getAction();
        doReturn(mUserManager).when(mContext).getSystemService(anyString());
        doReturn(false).when(mUserManager).isSystemUser();
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mCellBroadcastReceiver, never()).initializeSharedPreference();
        verify(mContext).getSystemService(anyString());
    }

    @Test
    public void testOnReceive_actionDefaultSmsSubscriptionChanged() {
        doReturn(SubscriptionManager.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED)
                .when(mIntent).getAction();
        doReturn(mUserManager).when(mContext).getSystemService(anyString());
        doReturn(false).when(mUserManager).isSystemUser();
        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mCellBroadcastReceiver, never()).initializeSharedPreference();
        verify(mContext).getSystemService(anyString());
    }

    @Test
    public void testOnReceive_actionSmsEmergencyCbReceived() {
        doReturn(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED).when(mIntent).getAction();
        doReturn(mIntent).when(mIntent).setClass(mContext, CellBroadcastAlertService.class);

        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mIntent).setClass(mContext, CellBroadcastAlertService.class);
        verify(mContext).startService(mIntent);
    }

    @Test
    public void testOnReceive_smsCbReceivedAction() {
        doReturn(Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION).when(mIntent).getAction();
        doReturn(mIntent).when(mIntent).setClass(mContext, CellBroadcastAlertService.class);

        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mIntent).setClass(mContext, CellBroadcastAlertService.class);
        verify(mContext).startService(mIntent);
    }

    @Test
    public void testOnReceive_smsServiceCategoryProgramDataReceivedAction() {
        doReturn(Telephony.Sms.Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION)
                .when(mIntent).getAction();
        doReturn(null).when(mIntent).getParcelableArrayListExtra(anyString());

        mCellBroadcastReceiver.onReceive(mContext, mIntent);
        verify(mIntent).getParcelableArrayListExtra(anyString());
    }

    @Test
    public void testInitializeSharedPreference_ifSystemUser() {
        doReturn("An invalid action").when(mIntent).getAction();
        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        doReturn(mUserManager).when(mContext).getSystemService(anyString());
        doReturn(true).when(mUserManager).isSystemUser();
        doReturn(mSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
        doReturn(true).when(mSharedPreferences).getBoolean(anyString(), anyBoolean());
        doNothing().when(mCellBroadcastReceiver).adjustReminderInterval();
        mCellBroadcastReceiver.initializeSharedPreference();
        verify(mSharedPreferences).getBoolean(anyString(), anyBoolean());
    }

    @Test
    public void testInitializeSharedPreference_ifNotSystemUser() {
        doReturn("An invalid action").when(mIntent).getAction();
        mCellBroadcastReceiver.onReceive(mContext, mIntent);

        doReturn(mUserManager).when(mContext).getSystemService(anyString());
        doReturn(false).when(mUserManager).isSystemUser();
        doReturn(mSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
        mCellBroadcastReceiver.initializeSharedPreference();
        verify(mSharedPreferences, never()).getBoolean(anyString(), anyBoolean());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
