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

package com.android.cellbroadcastreceiver.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.icu.util.ULocale;
import android.os.Bundle;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.CbGeoUtils.Circle;
import android.telephony.CbGeoUtils.Geometry;
import android.telephony.CbGeoUtils.LatLng;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.cellbroadcastreceiver.CellBroadcastAlertButtonManager;
import com.android.cellbroadcastreceiver.CellBroadcastAlertDialog;
import com.android.cellbroadcastreceiver.CellBroadcastAlertService;
import com.android.cellbroadcastreceiver.CellBroadcastChannelManager;
import com.android.cellbroadcastreceiver.CellBroadcastMapLauncher;
import com.android.cellbroadcastreceiver.CellBroadcastReceiverApp;
import com.android.cellbroadcastreceiver.CellBroadcastSettings;
import com.android.cellbroadcastreceiver.CellBroadcastTranslateManager;
import com.android.cellbroadcastreceiver.R;
import com.android.cellbroadcastreceiver.flags.Flags;
import com.android.internal.telephony.CellBroadcastUtils;
import com.android.internal.telephony.gsm.SmsCbConstants;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public class CellBroadcastAlertDialogTest extends
        CellBroadcastActivityTestCase<CellBroadcastAlertDialog> {

    @Mock
    private NotificationManager mMockedNotificationManager;

    @Mock
    private IPowerManager.Stub mMockedPowerManagerService;

    @Mock
    private IThermalService.Stub mMockedThermalService;

    @Mock
    LinearLayout mMockLinearLayout;

    @Captor
    private ArgumentCaptor<Integer> mInt;

    @Captor
    private ArgumentCaptor<Notification> mNotification;

    private PowerManager mPowerManager;
    private int mSubId = 0;

    public CellBroadcastAlertDialogTest() {
        super(CellBroadcastAlertDialog.class);
    }

    private int mServiceCategory = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL;
    private int mCmasMessageClass = 0;

    private ArrayList<SmsCbMessage> mMessageList;
    @Mock
    private CellBroadcastTranslateManager mMockCBTranslateManager;
    @Mock private CellBroadcastAlertButtonManager mMockCBButtonManager;

    private static final String KEY_TRANSLATE_CONSENT_ACCEPTED = "translate_consent_accepted";

    private SmsCbMessage mSmsCbMessageWithGeo;
    private SmsCbMessage mSmsCbMessageWithoutGeo;

    private boolean mIsWatch;

    @Override
    protected Intent createActivityIntent() {
        mMessageList = new ArrayList<>(1);
        mMessageList.add(CellBroadcastAlertServiceTest.createMessageForCmasMessageClass(12412,
                mServiceCategory,
                mCmasMessageClass));

        Intent intent = new Intent(getInstrumentation().getTargetContext(),
                CellBroadcastAlertDialog.class);
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                mMessageList);
        return intent;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        injectSystemService(NotificationManager.class, mMockedNotificationManager);
        // PowerManager is a final class so we can't use Mockito to mock it, but we can mock
        // its underlying service.
        doReturn(true).when(mMockedPowerManagerService).isInteractive();
        if (SdkLevel.isAtLeastU()) {
            doReturn(true).when(
                    mMockedPowerManagerService).isDisplayInteractive(anyInt());
        }
        Handler handler = new Handler(Looper.getMainLooper());
        mPowerManager = new PowerManager(mContext, mMockedPowerManagerService,
                mMockedThermalService, handler);
        injectSystemService(PowerManager.class, mPowerManager);

        mIsWatch = getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(android.content.pm.PackageManager.FEATURE_WATCH);

        SubscriptionManager mockSubManager = mock(SubscriptionManager.class);
        injectSystemService(SubscriptionManager.class, mockSubManager);
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mockSubManager).getActiveSubscriptionInfo(anyInt());

        CellBroadcastSettings.resetResourcesCache();
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
        String[] values = new String[]{"0x1112-0x1112:rat=gsm, always_on=true"};
        doReturn(values).when(mContext.getResources()).getStringArray(
                eq(com.android.cellbroadcastreceiver.R.array
                        .cmas_presidential_alerts_channels_range_strings));
        mSmsCbMessageWithGeo = createSmsCbMessage(true);
        mSmsCbMessageWithoutGeo = createSmsCbMessage(false);
        doAnswer(invocation -> {
            Consumer<Boolean> callback = invocation.getArgument(0);
            callback.accept(true);
            return null;
        }).when(mMockCBTranslateManager).checkOnDeviceTranslationCapability(any());
        doAnswer(invocation -> {
            Consumer<ULocale> callback = invocation.getArgument(1);
            callback.accept(new ULocale(Locale.ENGLISH.getLanguage()));
            return null;
        }).when(mMockCBTranslateManager).resolveTargetLanguage(any(), any());
        doReturn("legacy_linkify").when(mContext.getResources()).getString(R.string.link_method);
        CellBroadcastAlertDialog.sIsTranslateFeatureEnabledForTest = false;
    }

    @After
    public void tearDown() throws Exception {
        CellBroadcastAlertDialog.sIsTranslateFeatureEnabledForTest = null;
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = null;
        CellBroadcastMapLauncher.sIsMapActivityAvailableForTest = null;
        CellBroadcastSettings.resetResourcesCache();
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
        super.tearDown();
    }

    public void testTitleAndMessageText() throws Throwable {
        doReturn(true).when(mContext.getResources()).getBoolean(R.bool.show_alert_title);

        startActivity();
        waitForMs(100);

        CharSequence alertString =
                getActivity().getResources().getText(com.android.cellbroadcastreceiver.R.string
                        .cmas_presidential_level_alert);
        assertTrue(getActivity().getTitle().toString().startsWith(alertString.toString()));
        assertTrue(((TextView) getActivity().findViewById(
                com.android.cellbroadcastreceiver.R.id.alertTitle)).getText().toString()
                .startsWith(alertString.toString()));

        waitUntilAssertPasses(()-> {
            String body = CellBroadcastAlertServiceTest.createMessage(34596).getMessageBody();
            assertEquals(body, ((TextView) getActivity().findViewById(
                            com.android.cellbroadcastreceiver.R.id.message)).getText().toString());
        }, 1000);

        stopActivity();
    }
    public void testNoTitle() throws Throwable {
        doReturn(false).when(mContext.getResources()).getBoolean(R.bool.show_alert_title);
        startActivity();
        waitForMs(100);
        assertTrue(TextUtils.isEmpty(((TextView) getActivity().findViewById(
                com.android.cellbroadcastreceiver.R.id.alertTitle)).getText()));
        stopActivity();
    }

    public void waitUntilAssertPasses(Runnable r, long maxWaitMs) {
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

    public void testAddToNotification() throws Throwable {
        doReturn(true).when(mContext.getResources()).getBoolean(R.bool.show_alert_title);
        doReturn(false).when(mContext.getResources()).getBoolean(
                R.bool.disable_capture_alert_dialog);

        startActivity();
        waitForMs(100);
        leaveActivity();
        waitForMs(100);
        verify(mMockedNotificationManager, times(1)).notify(mInt.capture(),
                mNotification.capture());
        Bundle b = mNotification.getValue().extras;
        SmsCbMessage message = mMessageList.get(0);
        int expectedId = CellBroadcastAlertService.getNotificationId(message, mIsWatch);
        assertEquals(Notification.VISIBILITY_PUBLIC, mNotification.getValue().visibility);
        assertEquals(expectedId, (int) mInt.getValue());
        assertTrue(getActivity().getTitle().toString().startsWith(
                b.getCharSequence(Notification.EXTRA_TITLE).toString()));
        assertEquals(CellBroadcastAlertServiceTest.createMessage(98235).getMessageBody(),
                b.getCharSequence(Notification.EXTRA_TEXT));
    }

    public void testAddToNotificationWithDifferentConfiguration() throws Throwable {
        doReturn(false).when(mContext.getResources()).getBoolean(R.bool.show_alert_title);
        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.disable_capture_alert_dialog);

        startActivity();
        waitForMs(100);
        leaveActivity();
        waitForMs(100);
        verify(mMockedNotificationManager, times(1)).notify(mInt.capture(),
                mNotification.capture());
        Bundle b = mNotification.getValue().extras;
        SmsCbMessage message = mMessageList.get(0);
        int expectedId = CellBroadcastAlertService.getNotificationId(message, mIsWatch);
        assertEquals(Notification.VISIBILITY_PUBLIC, mNotification.getValue().visibility);
        assertEquals(expectedId, (int) mInt.getValue());
        assertTrue(TextUtils.isEmpty(b.getCharSequence(Notification.EXTRA_TITLE)));
        verify(mContext.getResources(), times(1)).getString(mInt.capture(), anyInt());
        assertEquals(R.string.notification_multiple, (int) mInt.getValue());
    }

    public void testDoNotAddToNotificationOnStop() throws Throwable {
        startActivity();
        waitForMs(100);
        stopActivity();
        waitForMs(100);
        verify(mMockedNotificationManager, times(0)).notify(mInt.capture(),
                mNotification.capture());
    }

    public void testDismissByDeleteIntent() throws Throwable {
        // Skip on watches: Wear OS uses dynamic notification IDs.
        // Verifying the cancellation of the phone-centric static NOTIFICATION_ID is not meaningful
        // here.
        if (mIsWatch) {
            return;
        }

        final Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertService.DISMISS_DIALOG, true);
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);
        Looper.prepare();
        CellBroadcastAlertDialog activity =
                startActivity(intent, null, null);
        getInstrumentation().callActivityOnUserLeaving(activity);
        verify(mMockedNotificationManager, atLeastOnce()).cancel(
                eq(CellBroadcastAlertService.NOTIFICATION_ID));
    }

    public void testGetNewMessageListIfNeeded() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();
        Resources spyRes = mContext.getResources();
        doReturn(false).when(spyRes).getBoolean(
                R.bool.show_cmas_messages_in_priority_order);

        SmsCbMessage testMessage1 = CellBroadcastAlertServiceTest
                .createMessageForCmasMessageClass(12412,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        mCmasMessageClass);
        waitForMs(10);
        SmsCbMessage testMessage2 = CellBroadcastAlertServiceTest
                .createMessageForCmasMessageClass(12412,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        mCmasMessageClass);
        ArrayList<SmsCbMessage> inputList1 = new ArrayList<>();
        ArrayList<SmsCbMessage> inputList2 = new ArrayList<>();

        inputList1.add(testMessage1);
        ArrayList<SmsCbMessage> messageList = activity.getNewMessageListIfNeeded(
                inputList1, inputList2);
        assertTrue(messageList.size() == 1);
        assertEquals(testMessage1.getReceivedTime(), messageList.get(0).getReceivedTime());

        inputList2.add(testMessage1);
        messageList = activity.getNewMessageListIfNeeded(inputList1, inputList2);
        assertTrue(messageList.size() == 1);
        assertEquals(testMessage1.getReceivedTime(), messageList.get(0).getReceivedTime());

        inputList2.add(testMessage2);
        messageList = activity.getNewMessageListIfNeeded(inputList1, inputList2);
        assertTrue(messageList.size() == 2);
        assertEquals(testMessage2.getReceivedTime(), messageList.get(1).getReceivedTime());

        doReturn(true).when(spyRes).getBoolean(
                R.bool.show_cmas_messages_in_priority_order);

        messageList = activity.getNewMessageListIfNeeded(inputList1, inputList2);
        assertTrue(messageList.size() == 2);
        assertEquals(testMessage1.getReceivedTime(), messageList.get(1).getReceivedTime());
    }

    @InstrumentationTest
    // This test has a module dependency (it uses the CellBroadcastContentProvider), so it is
    // disabled for OEM testing because it is not a true unit test
    public void testDismiss() throws Throwable {
        // Skip on watches: Wear OS uses dynamic notification IDs.
        // Verifying the cancellation of the phone-centric static NOTIFICATION_ID is not meaningful
        // here.
        if (mIsWatch) {
            return;
        }

        CellBroadcastAlertDialog activity = startActivity();
        waitForMs(100);
        activity.dismiss();

        verify(mMockedNotificationManager, times(1)).cancel(
                eq(CellBroadcastAlertService.NOTIFICATION_ID));
    }

    public void testOnNewIntent() throws Throwable {
        if (mIsWatch) {
            return;
        }

        Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);

        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);
        waitForMs(100);

        // Standard phone verification
        ImageView image = activity.findViewById(R.id.pictogramImage);
        assertNotNull("Pictogram must exist on phone", image);
        image.setVisibility(View.VISIBLE);
        assertEquals(View.VISIBLE, image.getVisibility());

        // add more messages to list
        mMessageList.add(CellBroadcastAlertServiceTest.createMessageForCmasMessageClass(12413,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING));
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                new ArrayList<>(mMessageList));
        activity.onNewIntent(intent);

        verify(mMockedNotificationManager, atLeastOnce()).cancel(
                eq(CellBroadcastAlertService.NOTIFICATION_ID));

        assertNotNull(image.getLayoutParams());
    }

    public void testOnNewIntentForWatch() throws Throwable {
        if (!mIsWatch) {
            return;
        }

        Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);

        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);
        waitForMs(100);

        // Verify elements that DO exist on watch
        assertNotNull("Icon should be present on watch", activity.findViewById(R.id.icon));
        assertNotNull("Message should be present on watch", activity.findViewById(R.id.message));
        assertNull("Pictogram should not be present on watch",
                activity.findViewById(R.id.pictogramImage));

        // add more messages to list
        mMessageList.add(CellBroadcastAlertServiceTest.createMessageForCmasMessageClass(12413,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING));
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                new ArrayList<>(mMessageList));
        activity.onNewIntent(intent);

        verify(mMockedNotificationManager, atLeastOnce()).cancel(
                eq(CellBroadcastAlertService.NOTIFICATION_ID));
    }

    public void testAnimationHandler() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();

        activity.mAnimationHandler.startIconAnimation(mSubId);

        assertTrue(activity.mAnimationHandler.mWarningIconVisible);

        Message m = Message.obtain();
        m.what = activity.mAnimationHandler.mCount.get();
        activity.mAnimationHandler.handleMessage(m);

        // assert that message count has gone up
        assertEquals(m.what + 1, activity.mAnimationHandler.mCount.get());
    }

    public void testOnResume() throws Throwable {
        Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);

        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);

        CellBroadcastAlertDialog.AnimationHandler mockAnimationHandler = mock(
                CellBroadcastAlertDialog.AnimationHandler.class);
        activity.mAnimationHandler = mockAnimationHandler;

        activity.onResume();
        verify(mockAnimationHandler).startIconAnimation(anyInt());
    }

    public void testOnPause() throws Throwable {
        Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);

        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);

        CellBroadcastAlertDialog.AnimationHandler mockAnimationHandler = mock(
                CellBroadcastAlertDialog.AnimationHandler.class);
        activity.mAnimationHandler = mockAnimationHandler;

        activity.onPause();
        verify(mockAnimationHandler).stopIconAnimation();
    }

    public void testOnKeyDown() throws Throwable {
        Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);

        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);

        assertTrue(activity.onKeyDown(0,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FOCUS)));
    }

    public void testOnConfigurationChanged() throws Throwable {
        if (mIsWatch) {
            return;
        }

        CellBroadcastAlertDialog activity = startActivity();
        Configuration newConfig = new Configuration();

        ImageView image = activity.findViewById(R.id.pictogramImage);
        image.setVisibility(View.VISIBLE);
        assertEquals(View.VISIBLE, image.getVisibility());

        newConfig.orientation = Configuration.ORIENTATION_LANDSCAPE;
        activity.onConfigurationChanged(newConfig);
        assertNotNull(image.getLayoutParams());

        newConfig.orientation = Configuration.ORIENTATION_PORTRAIT;
        activity.onConfigurationChanged(newConfig);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, image.getLayoutParams().height);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, image.getLayoutParams().width);
    }

    public void testOnWindowFocusChanged() throws Throwable {
        if (mIsWatch) {
            return;
        }

        CellBroadcastAlertDialog activity = startActivity();

        ImageView image = activity.findViewById(R.id.pictogramImage);
        assertNotNull("Pictogram image must exist on phone", image);
        image.setVisibility(View.VISIBLE);
        assertEquals(View.VISIBLE, image.getVisibility());
        activity.onWindowFocusChanged(true);
        assertNotNull(image.getLayoutParams());
    }

    public void testOnWindowFocusChangedForWatch() throws Throwable {
        if (!mIsWatch) {
            return;
        }

        CellBroadcastAlertDialog activity = startActivity();

        TextView message = activity.findViewById(R.id.message);
        assertNotNull("Message view must exist on Wear OS", message);
        message.setVisibility(View.VISIBLE);
        assertEquals(View.VISIBLE, message.getVisibility());
        activity.onWindowFocusChanged(true);
        assertNotNull("Message layout params must remain valid after focus change",
                message.getLayoutParams());

    }

    public void testOnKeyDownWithEmptyMessageList() throws Throwable {
        mMessageList = new ArrayList<>(1);

        Intent intent = new Intent(getInstrumentation().getTargetContext(),
                CellBroadcastAlertDialog.class);
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                mMessageList);
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);
        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);

        assertTrue(activity.onKeyDown(0,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FOCUS)));
    }

    public void testPulsationHandlerStart() throws Throwable {
        int[] pattern = new int[] {0xFFFF0000, 100000, 500, 1000};
        doReturn(pattern).when(mContext.getResources()).getIntArray(
                eq(com.android.cellbroadcastreceiver.R.array.default_pulsation_pattern));

        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
        CellBroadcastAlertDialog activity = startActivity();
        waitForMs(100);
        activity.mPulsationHandler.mLayout = mMockLinearLayout;

        assertEquals(0xFFFF0000, activity.mPulsationHandler.mHighlightColor);
        assertEquals(100000, activity.mPulsationHandler.mDuration);
        assertEquals(500, activity.mPulsationHandler.mOnInterval);
        assertEquals(1000, activity.mPulsationHandler.mOffInterval);

        waitForMs(2000);

        verify(mMockLinearLayout, atLeastOnce()).setBackgroundColor(eq(0xFFFF0000));
    }

    public void testPulsationRestartOnNewIntent() throws Throwable {
        int[] pattern = new int[] {0xFFFF0000, 100000, 500, 1000};
        doReturn(pattern).when(mContext.getResources()).getIntArray(
                eq(com.android.cellbroadcastreceiver.R.array.default_pulsation_pattern));

        CellBroadcastAlertDialog activity = startActivity();
        waitForMs(100);
        activity.mPulsationHandler.mLayout = mMockLinearLayout;

        assertEquals(0xFFFF0000, activity.mPulsationHandler.mHighlightColor);
        assertEquals(100000, activity.mPulsationHandler.mDuration);
        assertEquals(500, activity.mPulsationHandler.mOnInterval);
        assertEquals(1000, activity.mPulsationHandler.mOffInterval);

        pattern = new int[] {0xFFFFFFFF, 200000, 1000, 500};
        doReturn(pattern).when(mContext.getResources()).getIntArray(
                eq(com.android.cellbroadcastreceiver.R.array.default_pulsation_pattern));
        mMessageList.add(CellBroadcastAlertServiceTest.createMessageForCmasMessageClass(12413,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY));
        Intent intent = createActivityIntent();
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                new ArrayList<>(mMessageList));
        CellBroadcastSettings.resetResourcesCache();
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
        activity.onNewIntent(intent);
        waitForMs(100);

        // Verify existing pulsation has been stopped
        verify(mMockLinearLayout, times(1)).setBackgroundColor(
                eq(activity.mPulsationHandler.mBackgroundColor));

        activity.mPulsationHandler.mLayout = mMockLinearLayout;

        // Verify new parameters have been applied
        assertEquals(0xFFFFFFFF, activity.mPulsationHandler.mHighlightColor);
        assertEquals(200000, activity.mPulsationHandler.mDuration);
        assertEquals(1000, activity.mPulsationHandler.mOnInterval);
        assertEquals(500, activity.mPulsationHandler.mOffInterval);

        waitForMs(2000);

        // Verify new pulsation takes effect
        verify(mMockLinearLayout, atLeastOnce()).setBackgroundColor(eq(0xFFFFFFFF));
    }

    public void testPulsationHandlerHandleMessageAndStop() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();
        waitForMs(100);

        int backgroundColor = activity.mPulsationHandler.mBackgroundColor;
        activity.mPulsationHandler.mHighlightColor = 0xFFFF0000;
        activity.mPulsationHandler.mLayout = mMockLinearLayout;
        activity.mPulsationHandler.mOnInterval = 60000;
        activity.mPulsationHandler.mOffInterval = 60000;
        activity.mPulsationHandler.mDuration = 300000;

        Message m = Message.obtain();
        m.what = activity.mPulsationHandler.mCount.get();
        activity.mPulsationHandler.handleMessage(m);

        // assert that message count has gone up, and the background color is highlighted
        assertEquals(m.what + 1, activity.mPulsationHandler.mCount.get());
        assertTrue(activity.mPulsationHandler.mIsPulsationOn);
        verify(mMockLinearLayout, times(1)).setBackgroundColor(eq(0xFFFF0000));

        m = Message.obtain();
        m.what = activity.mPulsationHandler.mCount.get();
        activity.mPulsationHandler.handleMessage(m);

        // assert that message count has gone up, and the background color is restored
        assertEquals(m.what + 1, activity.mPulsationHandler.mCount.get());
        assertFalse(activity.mPulsationHandler.mIsPulsationOn);
        verify(mMockLinearLayout, times(1)).setBackgroundColor(eq(backgroundColor));

        m = Message.obtain();
        m.what = activity.mPulsationHandler.mCount.get();
        activity.mPulsationHandler.handleMessage(m);

        // assert that the background color is highlighted again
        assertEquals(m.what + 1, activity.mPulsationHandler.mCount.get());
        assertTrue(activity.mPulsationHandler.mIsPulsationOn);
        verify(mMockLinearLayout, times(2)).setBackgroundColor(eq(0xFFFF0000));

        activity.mPulsationHandler.stop();
        waitForMs(100);

        // assert that the background color is restored
        assertEquals(m.what + 2, activity.mPulsationHandler.mCount.get());
        assertFalse(activity.mPulsationHandler.mIsPulsationOn);
        verify(mMockLinearLayout, times(2)).setBackgroundColor(eq(backgroundColor));
    }

    private ArrayList<SmsCbMessage> getNewMessageList() throws Exception {
        Method method = CellBroadcastReceiverApp.class.getDeclaredMethod("getNewMessageList");
        method.setAccessible(true);
        return (ArrayList<SmsCbMessage>) method.invoke(null);
    }

    private ArrayList<SmsCbMessage> addNewMessageToList(SmsCbMessage message) {
        Class[] args = new Class[1];
        args[0] = SmsCbMessage.class;
        try {
            Method method = CellBroadcastReceiverApp.class.getDeclaredMethod(
                    "addNewMessageToList", args);
            method.setAccessible(true);
            return (ArrayList<SmsCbMessage>) method.invoke(null, message);
        } catch (Exception e) {
            return null;
        }
    }

    public void testNewMessageListCount() throws Throwable {
        SmsCbMessage testMessage1 = CellBroadcastAlertServiceTest
                .createMessageForCmasMessageClass(75103,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        mCmasMessageClass);
        SmsCbMessage testMessage2 = CellBroadcastAlertServiceTest
                .createMessageForCmasMessageClass(51030,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        mCmasMessageClass);
        SmsCbMessage testMessage3 = CellBroadcastAlertServiceTest
                .createMessageForCmasMessageClass(10307,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        mCmasMessageClass);

        // touch a notification for on-going message
        Intent intent1 = createActivityIntent();
        intent1.putExtra(CellBroadcastAlertService.DISMISS_DIALOG, false);
        intent1.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, false);
        addNewMessageToList(testMessage1);
        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent1, null, null);
        waitForMs(100);

        assertEquals(1, getNewMessageList().size());

        // touch a notification for pending message
        Intent intent2 = createActivityIntent();
        intent2.putExtra(CellBroadcastAlertService.DISMISS_DIALOG, false);
        intent2.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);
        addNewMessageToList(testMessage2);
        activity.onNewIntent(intent2);

        assertEquals(2, getNewMessageList().size());

        // swipe a notification for pending message
        Intent intent3 = createActivityIntent();
        intent3.putExtra(CellBroadcastAlertService.DISMISS_DIALOG, true);
        intent3.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);
        addNewMessageToList(testMessage3);
        activity.onNewIntent(intent3);

        assertEquals(getNewMessageList().size(), 0);

        // swipe a notification for on-going message
        Intent intent4 = createActivityIntent();
        intent4.putExtra(CellBroadcastAlertService.DISMISS_DIALOG, true);
        intent4.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, false);
        addNewMessageToList(testMessage1);
        activity.onNewIntent(intent4);

        assertEquals(getNewMessageList().size(), 1);
    }

    private void setWatchUiMode() {
        Configuration configuration = new Configuration(
                mContext.getResources().getConfiguration());
        configuration.uiMode =
                (configuration.uiMode & ~Configuration.UI_MODE_TYPE_MASK)
                | Configuration.UI_MODE_TYPE_WATCH;
        mContext.enableOverrideConfiguration(true);
        mContext = (TestContext) mContext.createConfigurationContext(configuration);
        setActivityContext(mContext);
    }

    public void testOnConfigurationChangedForWatch() throws Throwable {
        setWatchUiMode();
        CellBroadcastAlertDialog activity = startActivity();

        Configuration newConfig = new Configuration();
        newConfig.orientation = Configuration.ORIENTATION_LANDSCAPE;
        activity.onConfigurationChanged(newConfig);

        newConfig.orientation = Configuration.ORIENTATION_PORTRAIT;
        activity.onConfigurationChanged(newConfig);

        assertNull(activity.findViewById(R.id.pictogramImage));
    }

    public void testOnCreate() throws Throwable {
        doReturn(false).when(mContext.getResources()).getBoolean(
                R.bool.disable_capture_alert_dialog);
        CellBroadcastAlertDialog activity = startActivity();
        int flags = activity.getWindow().getAttributes().flags;
        assertEquals((flags & WindowManager.LayoutParams.FLAG_SECURE), 0);
        stopActivity();
    }

    public void testOnCreateWithCaptureRestriction() throws Throwable {
        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.disable_capture_alert_dialog);
        CellBroadcastAlertDialog activity = startActivity();
        int flags = activity.getWindow().getAttributes().flags;
        assertEquals((flags & WindowManager.LayoutParams.FLAG_SECURE),
                WindowManager.LayoutParams.FLAG_SECURE);
        stopActivity();
    }

    public void testTitleOnNonDefaultSubId() throws Throwable {
        Intent intent = createActivityIntent();
        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);
        waitForMs(100);

        assertFalse(TextUtils.isEmpty(((TextView) getActivity().findViewById(
                com.android.cellbroadcastreceiver.R.id.alertTitle)).getText()));

        SharedPreferences mockSharedPreferences = mock(SharedPreferences.class);
        doReturn("334090").when(mockSharedPreferences).getString(any(), any());
        mContext.injectSharedPreferences(mockSharedPreferences);
        Resources mockResources2 = mock(Resources.class);
        doReturn(false).when(mockResources2).getBoolean(R.bool.show_alert_title);
        doReturn("none").when(mockResources2).getString(R.string.link_method);

        CellBroadcastSettings.sResourcesCacheByOperator.put("334090", mockResources2);

        mMessageList.add(CellBroadcastAlertServiceTest.createMessageForCmasMessageClass(12413,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY));
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                new ArrayList<>(mMessageList));
        activity.onNewIntent(intent);

        assertTrue(TextUtils.isEmpty(((TextView) getActivity().findViewById(
                com.android.cellbroadcastreceiver.R.id.alertTitle)).getText()));
    }

    @InstrumentationTest
    public void testDialogDismissOnBackPress() {
        // Skip on watches: Wear OS relies on swipe-to-dismiss rather than a physical back button,
        // making UiDevice.pressBack() invalid here.
        if (mIsWatch) {
            return;
        }

        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Attempt to get the package name recognized by the system.
        String packageName = CellBroadcastUtils.getDefaultCellBroadcastReceiverPackageName(context);

        // Fallback to the current instrumentation target package if the utility returns null.
        // This addresses potential routing ambiguities on devices where both APEX and
        // Legacy CellBroadcast packages coexist (e.g., Wear OS).
        if (packageName == null) {
            packageName = context.getPackageName();
        }

        assertNotNull("Target package name should not be null", packageName);
        Intent intent = createActivityIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        device.wait(Until.hasObject(By.pkg(packageName)), 3000);
        device.pressBack();

        boolean isGone = device.wait(Until.gone(By.pkg(packageName)), 3000);
        assertFalse("Dialog should not be dismissed after pressing back key", isGone);
    }

    private CellBroadcastAlertDialog startActivitySetMock(Intent intent) {
        Looper.prepare();
        startActivity(intent, null, null);
        getInstrumentation().waitForIdleSync();
        CellBroadcastAlertDialog activity = getActivity();
        assertNotNull("Activity should not be null", activity);
        activity.setTranslateManagerForTest(mMockCBTranslateManager);
        activity.setButtonManagerForTest(mMockCBButtonManager);
        return activity;
    }

    private SmsCbMessage createMessage(String messageBody, String language) {
        return new SmsCbMessage(1, 1, 1, new SmsCbLocation("123456"),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED, language,
                messageBody, 3, (SmsCbEtwsInfo) null,
                (SmsCbCmasInfo) null, 0, 0);
    }

    private Intent createIntentWithMessage(SmsCbMessage message) {
        ArrayList<SmsCbMessage> messageList = new ArrayList<>();
        messageList.add(message);
        Intent intent = new Intent(mContext, CellBroadcastAlertDialog.class);
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                messageList);
        return intent;
    }

    private SharedPreferences setTranslation() {
        doReturn(true).when(mContext.getResources()).getBoolean(
                eq(R.bool.enable_alert_translation));
        SharedPreferences mockSharedPreferences = mock(SharedPreferences.class);
        SharedPreferences.Editor mMockEditor = mock(SharedPreferences.Editor.class);
        mContext.injectSharedPreferences(mockSharedPreferences);
        doReturn(mMockEditor).when(mockSharedPreferences).edit();
        doReturn(mMockEditor).when(mMockEditor).putBoolean(anyString(), anyBoolean());
        doReturn(true).when(mMockCBTranslateManager).isTranslationManagerAvailable();
        CellBroadcastAlertDialog.sIsTranslateFeatureEnabledForTest = true;
        return mockSharedPreferences;
    }

    private void setPendingIntentForTranslation() {
        Intent dummyIntent = new Intent();
        PendingIntent realPendingIntent = PendingIntent.getActivity(
                getInstrumentation().getTargetContext(), 0, dummyIntent,
                PendingIntent.FLAG_IMMUTABLE);
        doReturn(realPendingIntent).when(mMockCBTranslateManager).getSettingsIntent();
    }

    private static class TranslationTestSetupResult {
        public final CellBroadcastAlertDialog dialog;
        public final SharedPreferences sharedPreferences;

        TranslationTestSetupResult(CellBroadcastAlertDialog dialog,
                SharedPreferences sharedPreferences) {
            this.dialog = dialog;
            this.sharedPreferences = sharedPreferences;
        }
    }

    private TranslationTestSetupResult setupActivityForTranslationTest(SmsCbMessage message,
            boolean consentAccepted, boolean translatorReady, boolean disableDialogs) {
        SharedPreferences mockSharedPreferences = setTranslation();

        doReturn(consentAccepted).when(mockSharedPreferences).getBoolean(
                KEY_TRANSLATE_CONSENT_ACCEPTED, false);
        doReturn(translatorReady).when(mMockCBTranslateManager).isTranslatorReady();
        doAnswer(invocation -> {
            Consumer<ULocale> callback = invocation.getArgument(1);
            callback.accept(new ULocale("en"));
            return null;
        }).when(mMockCBTranslateManager).resolveTargetLanguage(any(), any());
        setPendingIntentForTranslation();

        CellBroadcastAlertDialog.sDisableDialogsForTest = disableDialogs;
        CellBroadcastAlertDialog.sIsTranslateFeatureEnabledForTest = false;
        doAnswer(invocation -> {
            Consumer<Boolean> callback = invocation.getArgument(0);
            callback.accept(translatorReady);
            return null;
        }).when(mMockCBTranslateManager).checkOnDeviceTranslationCapability(any());

        Intent intent = createIntentWithMessage(message);
        CellBroadcastAlertDialog dialog = startActivitySetMock(intent);
        CellBroadcastAlertDialog.sIsTranslateFeatureEnabledForTest = true;
        waitForMs(100);

        return new TranslationTestSetupResult(dialog, mockSharedPreferences);
    }

    private String getDifferentLanguage(String systemLanguage) {
        return systemLanguage.equals("en") ? "es" : "en";
    }

    private String getTestMessageBody(String systemLanguage) {
        return "en".equals(systemLanguage) ? "Mensaje de prueba" : "Test Message";
    }

    /**
     * Tests that clicking the translate button for the first time shows the consent dialog
     * and does not proceed with the translation.
     */
    public void testTranslateClickFirstTimeShowsConsentDialog() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        String systemLang = Locale.getDefault().getLanguage();
        SmsCbMessage message = createMessage("Test Message", getDifferentLanguage(systemLang));
        TranslationTestSetupResult result = setupActivityForTranslationTest(message,
                false, true, true);

        try {
            getInstrumentation().runOnMainSync(() -> {
                result.dialog.onTranslateClick();
            });

            verify(result.sharedPreferences, times(1)).getBoolean(KEY_TRANSLATE_CONSENT_ACCEPTED,
                    false);
            verify(mMockCBTranslateManager, never()).startOnDeviceTranslation(any(), any());
        } finally {
            CellBroadcastAlertDialog.sDisableDialogsForTest = false;
        }
    }

    /**
     * Tests that if the user has already given consent, clicking the translate button
     * proceeds directly to translation.
     */
    public void testTranslateClickConsentGivenProceedsToTranslation() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        final String originalMessage = "Original Message";
        String systemLang = Locale.getDefault().getLanguage();
        SmsCbMessage message = createMessage(originalMessage, getDifferentLanguage(systemLang));
        TranslationTestSetupResult setup = setupActivityForTranslationTest(message,
                true, true, true);

        getInstrumentation().runOnMainSync(() -> {
            setup.dialog.onTranslateClick();
        });

        verify(mMockCBTranslateManager, times(1)).startOnDeviceTranslation(eq(originalMessage),
                any());
    }

    /**
     * Tests that if consent is given but the translator is not ready (e.g., missing language pack),
     * the flow to download the language pack is initiated.
     */
    public void testTranslateClickTranslatorNotReadyTakesDownloadPath() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        String systemLang = Locale.getDefault().getLanguage();
        SmsCbMessage message = createMessage("Test Message", getDifferentLanguage(systemLang));
        TranslationTestSetupResult setup = setupActivityForTranslationTest(message,
                true, false, true);

        try {
            getInstrumentation().runOnMainSync(() -> {
                setup.dialog.onTranslateClick();
            });

            verify(mMockCBTranslateManager, times(1)).isTranslatorReady();
            verify(mMockCBTranslateManager, never()).startOnDeviceTranslation(any(), any());
        } finally {
            CellBroadcastAlertDialog.sDisableDialogsForTest = false;
        }
    }

    /**
     * Tests that the translation button is not shown if the source and target languages are the
     * same.
     */
    public void testInitTranslateWithSameLanguageHidesTranslateButton() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        String systemLanguage = Locale.getDefault().getLanguage();
        final String detectedLanguage = getDifferentLanguage(systemLanguage);
        final ULocale detectedULocale = new ULocale(detectedLanguage);
        SmsCbMessage message = createMessage(getTestMessageBody(detectedLanguage),
                detectedLanguage);
        TranslationTestSetupResult result = setupActivityForTranslationTest(message,
                true, true, true);
        doAnswer(invocation -> {
            Consumer<ULocale> callback = invocation.getArgument(1);
            callback.accept(detectedULocale);
            return null;
        }).when(mMockCBTranslateManager).resolveTargetLanguage(any(), any());
        CellBroadcastAlertDialog activity = result.dialog;
        activity.setButtonManagerForTest(mMockCBButtonManager);

        getInstrumentation().runOnMainSync(() -> {
            activity.initTranslate(message);
            activity.updateButtons(message);
        });

        verify(mMockCBButtonManager, atLeastOnce()).configureButtons(eq(false), anyBoolean());
    }

    /**
     * Tests that onLanguageDetectionCompleted callback correctly triggers offerTranslation.
     * This test dynamically selects a language different from the system default to ensure
     * the translation offer logic is always triggered.
     */
    public void testOnLanguageDetectionCompletedWithDetectedLanguageOffersTranslation() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        SmsCbMessage message = createMessage("A message to be detected", null);
        setPendingIntentForTranslation();
        final String systemLanguage = Locale.getDefault().getLanguage();
        final String detectedLanguage = getDifferentLanguage(systemLanguage);
        final ULocale detectedULocale = new ULocale(detectedLanguage);
        final ULocale systemULocale = new ULocale(systemLanguage);
        TranslationTestSetupResult result = setupActivityForTranslationTest(message,
                true, true, true);
        CellBroadcastAlertDialog activity = result.dialog;

        doAnswer(invocation -> {
            Consumer<ULocale> callback = invocation.getArgument(1);
            callback.accept(systemULocale);
            return null;
        }).when(mMockCBTranslateManager).resolveTargetLanguage(any(), any());

        getInstrumentation().runOnMainSync(
                () -> activity.onLanguageDetectionCompleted(Optional.of(detectedULocale)));

        verify(mMockCBTranslateManager, atLeastOnce()).initializeTranslator(eq(detectedULocale),
                eq(systemULocale));
    }

    /**
     * Tests that onNewIntent() calls destroyTranslator() to clean up resources.
     */
    public void testOnNewIntentCallsDestroyTranslator() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        SmsCbMessage message = createMessage("First message", "en");
        TranslationTestSetupResult result = setupActivityForTranslationTest(message,
                true, true, true);
        CellBroadcastAlertDialog activity = result.dialog;
        Intent newIntent = createIntentWithMessage(createMessage("Second message", "en"));
        getInstrumentation().runOnMainSync(() -> activity.onNewIntent(newIntent));
        verify(mMockCBTranslateManager, times(1)).destroyTranslator();
    }

    /**
     * Tests that the progress bar is shown when translation starts and hidden when it finishes.
     */
    public void testProgressBarVisibilityDuringTranslation() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        String systemLang = Locale.getDefault().getLanguage();
        SmsCbMessage message = createMessage("Test message", getDifferentLanguage(systemLang));

        TranslationTestSetupResult setup = setupActivityForTranslationTest(message, true, true,
                true);
        getInstrumentation().runOnMainSync(() -> setup.dialog.initTranslate(message));
        getInstrumentation().runOnMainSync(() -> setup.dialog.onTranslateClick());

        verify(mMockCBButtonManager, times(1)).showTranslationInProgress(eq(true));

        getInstrumentation().runOnMainSync(
                () -> setup.dialog.onTranslationCompleted("Translated", true));
        verify(mMockCBButtonManager, times(1)).showTranslationInProgress(eq(false));
    }

    /**
     * Tests that linkified text (e.g., URLs) is preserved after translation.
     * This verifies the functionality of setTextAndApplyLinks.
     */
    public void testLinkificationIsPreservedAfterTranslation() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        doReturn("legacy_linkify").when(mContext.getResources()).getString(R.string.link_method);

        String url = "http://www.google.com";
        String systemLang = Locale.getDefault().getLanguage();
        String messageLang = getDifferentLanguage(systemLang);
        String originalMessage = "Test message with a link " + url;
        String translatedMessage = "Translated text";

        SmsCbMessage message = createMessage(originalMessage, messageLang);
        TranslationTestSetupResult result = setupActivityForTranslationTest(message,
                true, true, true);

        getInstrumentation().runOnMainSync(() -> {
            result.dialog.onTranslationCompleted(translatedMessage, true);
        });

        TextView messageView = result.dialog.findViewById(R.id.message);
        Spannable spannable = (Spannable) messageView.getText();
        URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);

        assertTrue("URLSpan should exist after translation", spans.length > 0);
        assertEquals("The URL in the span should be correct", url, spans[0].getURL());
    }

    private PendingIntent mockSettingsIntentFound() {
        Intent dummyIntent = new Intent();
        PendingIntent realPendingIntent = PendingIntent.getActivity(
                getInstrumentation().getTargetContext(), 0, dummyIntent,
                PendingIntent.FLAG_IMMUTABLE);
        doReturn(realPendingIntent).when(mMockCBTranslateManager).getSettingsIntent();
        return realPendingIntent;
    }

    private void mockSettingsIntentNotFound() {
        doReturn(null).when(mMockCBTranslateManager).getSettingsIntent();
    }

    /**
     * Helper method to access the private mShouldOfferTranslation field via reflection.
     */
    private boolean getShouldOfferTranslation(CellBroadcastAlertDialog activity) {
        try {
            java.lang.reflect.Field field = CellBroadcastAlertDialog.class.getDeclaredField(
                    "mShouldOfferTranslation");
            field.setAccessible(true);
            return field.getBoolean(activity);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Reflection error accessing mShouldOfferTranslation: " + e.getMessage());
            return true;
        }
    }

    public void testHandleDownloadLanguagePositiveClickHandlesActivityNotFoundMockOnly()
            throws IntentSender.SendIntentException {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        String systemLang = Locale.getDefault().getLanguage();
        SmsCbMessage message = createMessage("Test Message", getDifferentLanguage(systemLang));
        TranslationTestSetupResult setup = setupActivityForTranslationTest(message, true, false,
                false);
        CellBroadcastAlertDialog activity = setup.dialog;
        CellBroadcastAlertDialog spyActivity = spy(activity);
        PendingIntent realPendingIntent = mockSettingsIntentFound();
        doThrow(new android.content.ActivityNotFoundException())
                .when(spyActivity).startIntentSenderForResult(
                        eq(realPendingIntent.getIntentSender()), anyInt(), any(), anyInt(),
                        anyInt(),
                        anyInt(), any());

        getInstrumentation().runOnMainSync(() -> {
            spyActivity.handleDownloadLanguagePositiveClick(realPendingIntent);
        });

        verify(spyActivity, times(1)).startIntentSenderForResult(
                any(), anyInt(), any(), anyInt(), anyInt(), anyInt(), any());

        assertFalse(getShouldOfferTranslation(spyActivity));
        verify(mMockCBButtonManager).configureButtons(eq(false), anyBoolean());
    }

    public void testShowDownloadLanguageDialogSettingsIntentIsNullShowsToastAndHidesButton() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        String systemLang = Locale.getDefault().getLanguage();
        SmsCbMessage message = createMessage("Test Message", getDifferentLanguage(systemLang));
        TranslationTestSetupResult setup = setupActivityForTranslationTest(message, true, false,
                false);
        CellBroadcastAlertDialog activity = setup.dialog;
        mockSettingsIntentNotFound();

        try {
            Method showDialogMethod = CellBroadcastAlertDialog.class.getDeclaredMethod(
                    "showDownloadLanguageDialog");
            showDialogMethod.setAccessible(true);
            getInstrumentation().runOnMainSync(() -> {
                try {
                    showDialogMethod.invoke(activity);
                } catch (Exception e) {
                    fail("Invocation failed: " + e.getMessage());
                }
            });
        } catch (NoSuchMethodException e) {
            fail("Could not find showDownloadLanguageDialog method: " + e.getMessage());
        }

        assertFalse(getShouldOfferTranslation(activity));
        verify(mMockCBButtonManager).configureButtons(eq(false), anyBoolean());
    }

    /**
     * Tests that onLanguageDetectionCompleted triggers button update to show the translate button.
     * This verifies the fix for the button not appearing after asynchronous language detection.
     */
    public void testOnLanguageDetectionCompletedTriggersButtonUpdate() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        String systemLang = Locale.getDefault().getLanguage();
        final String detectedLanguage = getDifferentLanguage(systemLang);
        final ULocale detectedULocale = new ULocale(detectedLanguage);

        SmsCbMessage message = createMessage("A message to be detected", null);
        TranslationTestSetupResult result = setupActivityForTranslationTest(message,
                true, true, true);
        CellBroadcastAlertDialog activity = result.dialog;

        doAnswer(invocation -> {
            Consumer<ULocale> callback = invocation.getArgument(1);
            callback.accept(new ULocale(systemLang));
            return null;
        }).when(mMockCBTranslateManager).resolveTargetLanguage(any(), any());

        getInstrumentation().runOnMainSync(
                () -> activity.onLanguageDetectionCompleted(Optional.of(detectedULocale)));

        verify(mMockCBTranslateManager, atLeastOnce()).initializeTranslator(eq(detectedULocale),
                any());
        verify(mMockCBButtonManager, atLeast(1)).configureButtons(anyBoolean(), anyBoolean());
        verify(mMockCBButtonManager, atLeast(1)).configureButtons(eq(true), anyBoolean());
    }

    // Helper method to create a real SmsCbMessage instance
    private SmsCbMessage createSmsCbMessage(boolean withGeometries) {
        int messageFormat = 1; // MESSAGE_FORMAT_3GPP
        int geographicalScope = 0; // GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE
        int serialNumber = 123;
        SmsCbLocation location = new SmsCbLocation("310260");
        int serviceCategory = 4370; // CMAS Presidential
        String language = "en";
        String body = "Test alert message";
        int priority = 3; // MESSAGE_PRIORITY_EMERGENCY
        SmsCbEtwsInfo etwsInfo = null;
        SmsCbCmasInfo cmasInfo = new SmsCbCmasInfo(0, 0, 0, 0, 0, 0); // Example CMAS info
        int slotIndex = 0;
        int subId = 1;
        long receivedTimeMillis = System.currentTimeMillis();
        int maximumWaitTimeSec = 255; // MAXIMUM_WAIT_TIME_NOT_SET

        List<Geometry> geometries = null;
        if (withGeometries) {
            geometries = new ArrayList<>();
            geometries.add(new Circle(new LatLng(37.422, -122.084), 1000.0));
        }

        return new SmsCbMessage(messageFormat, geographicalScope, serialNumber, location,
                serviceCategory, language, 0, body, priority, etwsInfo, cmasInfo,
                maximumWaitTimeSec, geometries, receivedTimeMillis, slotIndex, subId);
    }

    private void setMapConfigEnabled(boolean enabled) {
        // mContext is a Spy in the base class CellBroadcastActivityTestCase
        doReturn(enabled).when(mContext.getResources()).getBoolean(eq(R.bool.enable_map));
    }

    public void testIsGeoInfoWithGeometriesReturnsTrue() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();
        assertTrue(activity.isGeoInfo(mSmsCbMessageWithGeo));
        stopActivity();
    }

    public void testIsGeoInfoWithoutGeometriesReturnsFalse() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();
        assertFalse(activity.isGeoInfo(mSmsCbMessageWithoutGeo));
        stopActivity();
    }

    public void testIsMapConfigEnabledReturnsTrue() throws Throwable {
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog activity = startActivity();
        assertTrue("enable_map should be true when mocked to true", activity.isMapConfigEnabled());
        stopActivity();
    }

    public void testIsMapConfigEnabledReturnsFalse() throws Throwable {
        setMapConfigEnabled(false);
        CellBroadcastAlertDialog activity = startActivity();
        assertFalse("enable_map should be false when mocked to false",
                activity.isMapConfigEnabled());
        stopActivity();
    }

    public void testIsMapFlagEnabledWhenTestFlagTrueReturnsTrue() throws Throwable {
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        CellBroadcastAlertDialog activity = startActivity();
        assertTrue(activity.isMapFlagEnabled());
        stopActivity();
    }

    public void testIsMapFlagEnabledWhenTestFlagFalseReturnsFalse() throws Throwable {
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = false;
        CellBroadcastAlertDialog activity = startActivity();
        assertFalse(activity.isMapFlagEnabled());
        stopActivity();
    }

    public void testIsMapFlagEnabledWhenTestFlagNull() throws Throwable {
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = null;
        SmsCbMessage message = createSmsCbMessage(true);
        Intent intent = createIntentWithMessage(message);
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        if (Flags.enableCellbroadcastMapViewer()) {
            assertTrue(activity.isMapFlagEnabled());
        } else {
            assertFalse(activity.isMapFlagEnabled());
        }
    }

    public void testIsMapFeatureEnabledAllConditionsMetReturnsTrue() throws Throwable {
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog activity = startActivity();
        assertTrue(activity.isMapFeatureEnabled(mSmsCbMessageWithGeo));
        stopActivity();
    }

    public void testIsMapFeatureEnabledFlagOffReturnsFalse() throws Throwable {
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = false;
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog activity = startActivity();
        assertFalse(activity.isMapFeatureEnabled(mSmsCbMessageWithGeo));
        stopActivity();
    }

    public void testIsMapFeatureEnabledConfigFalseReturnsFalse() throws Throwable {
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        setMapConfigEnabled(false);
        CellBroadcastAlertDialog activity = startActivity();
        assertFalse(activity.isMapFeatureEnabled(mSmsCbMessageWithGeo));
        stopActivity();
    }

    public void testIsMapFeatureEnabledNoGeoInfoReturnsFalse() throws Throwable {
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog activity = startActivity();
        assertFalse(activity.isMapFeatureEnabled(mSmsCbMessageWithoutGeo));
        stopActivity();
    }

    /**
     * Helper method to set the private mShouldOfferTranslation field via reflection.
     */
    private void setShouldOfferTranslation(CellBroadcastAlertDialog activity, boolean value) {
        try {
            java.lang.reflect.Field field = CellBroadcastAlertDialog.class.getDeclaredField(
                    "mShouldOfferTranslation");
            field.setAccessible(true);
            field.setBoolean(activity, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Reflection error accessing mShouldOfferTranslation: " + e.getMessage());
        }
    }

    public void testUpdateButtonsTranslateTrueMapTrue() throws Throwable {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        SmsCbMessage message = createSmsCbMessage(true);

        Intent intent = createIntentWithMessage(message);
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        activity.setButtonManagerForTest(mMockCBButtonManager);
        setShouldOfferTranslation(activity, true);

        getInstrumentation().runOnMainSync(() -> activity.updateButtons(message));
        getInstrumentation().waitForIdleSync();

        verify(mMockCBButtonManager).configureButtons(eq(true), eq(true));
    }

    public void testUpdateButtonsTranslateTrueMapFalse() throws Throwable {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        setMapConfigEnabled(false);
        SmsCbMessage message = createSmsCbMessage(true);

        Intent intent = createIntentWithMessage(message);
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        activity.setButtonManagerForTest(mMockCBButtonManager);
        setShouldOfferTranslation(activity, true);

        getInstrumentation().runOnMainSync(() -> activity.updateButtons(message));
        getInstrumentation().waitForIdleSync();

        verify(mMockCBButtonManager).configureButtons(eq(true), eq(false));
    }

    public void testUpdateButtonsTranslateFalseMapTrue() throws Throwable {
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        SmsCbMessage message = createSmsCbMessage(true);

        Intent intent = createIntentWithMessage(message);
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        activity.setButtonManagerForTest(mMockCBButtonManager);
        setShouldOfferTranslation(activity, false);

        getInstrumentation().runOnMainSync(() -> activity.updateButtons(message));
        getInstrumentation().waitForIdleSync();

        verify(mMockCBButtonManager).configureButtons(eq(false), eq(true));
    }

    public void testUpdateButtonsTranslateFalseMapFalse() throws Throwable {
        setMapConfigEnabled(false);
        SmsCbMessage message = createSmsCbMessage(true);

        Intent intent = createIntentWithMessage(message);
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        activity.setButtonManagerForTest(mMockCBButtonManager);
        setShouldOfferTranslation(activity, false);

        getInstrumentation().runOnMainSync(() -> activity.updateButtons(message));
        getInstrumentation().waitForIdleSync();

        verify(mMockCBButtonManager).configureButtons(eq(false), eq(false));
    }

    public void testMapButtonVisibilityConfigDisabled() throws Throwable {
        setMapConfigEnabled(false);
        CellBroadcastAlertDialog.sDisableDialogsForTest = false;
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        SmsCbMessage message = createSmsCbMessage(true);
        Intent intent = createIntentWithMessage(message);
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        getInstrumentation().runOnMainSync(() -> activity.onResume());
        getInstrumentation().waitForIdleSync();

        verify(mMockCBButtonManager, times(1)).configureButtons(anyBoolean(),
                eq(false));
    }

    public void testMapButtonVisibilityFeatureFlagDisabled() throws Throwable {
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = false;
        SmsCbMessage message = createSmsCbMessage(true);
        Intent intent = createIntentWithMessage(message);
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        getInstrumentation().runOnMainSync(() -> activity.onResume());
        getInstrumentation().waitForIdleSync();

        verify(mMockCBButtonManager, times(1)).configureButtons(anyBoolean(),
                eq(false));
    }

    public void testMapButtonVisibilityNoGeoInfo() throws Throwable {
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        SmsCbMessage message = createSmsCbMessage(false);
        Intent intent = createIntentWithMessage(message);
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        getInstrumentation().runOnMainSync(() -> activity.onResume());
        getInstrumentation().waitForIdleSync();

        verify(mMockCBButtonManager, times(1)).configureButtons(anyBoolean(),
                eq(false));
    }

    public void testMapButtonVisibilityWithGeoInfo() throws Throwable {
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        SmsCbMessage message = createSmsCbMessage(true);
        Intent intent = createIntentWithMessage(message);
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        getInstrumentation().runOnMainSync(() -> activity.onResume());
        getInstrumentation().waitForIdleSync();

        verify(mMockCBButtonManager, times(1)).configureButtons(anyBoolean(),
                eq(true));
    }

    public void testOnMapClickWithGeometries() throws Throwable {
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        mMessageList = new ArrayList<>();
        mMessageList.add(createSmsCbMessage(true));
        Intent intent = createActivityIntent();
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        assertNotNull("Activity should not be null after startActivitySetMock", activity);

        getInstrumentation().runOnMainSync(() -> {
            activity.onMapClick();
        });
        getInstrumentation().waitForIdleSync();

        if (activity != null && !activity.isFinishing()) {
            activity.finish();
            getInstrumentation().waitForIdleSync();
        }
    }

    public void testOnMapClickWithoutGeometries() throws Throwable {
        setMapConfigEnabled(true);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        mMessageList = new ArrayList<>();
        mMessageList.add(createSmsCbMessage(false));
        Intent intent = createActivityIntent();
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        assertNotNull("Activity should not be null after startActivitySetMock", activity);

        getInstrumentation().runOnMainSync(() -> {
            activity.onMapClick();
        });
        getInstrumentation().waitForIdleSync();

        if (activity != null && !activity.isFinishing()) {
            activity.finish();
            getInstrumentation().waitForIdleSync();
        }
    }

    public void testOnMapClickNullMessage() throws Throwable {
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;
        mMessageList = new ArrayList<>();
        Intent intent = createActivityIntent();
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                new ArrayList<SmsCbMessage>());
        CellBroadcastAlertDialog activity = startActivitySetMock(intent);
        assertNotNull("Activity should not be null after startActivitySetMock", activity);

        getInstrumentation().runOnMainSync(() -> {
            activity.onMapClick();
        });
        getInstrumentation().waitForIdleSync();

        if (activity != null && !activity.isFinishing()) {
            activity.finish();
            getInstrumentation().waitForIdleSync();
        }
    }

    /**
     * Tests that the translate button is hidden after translation is completed.
     */
    public void testTranslateButtonHiddenAfterTranslation() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }
        String systemLang = Locale.getDefault().getLanguage();
        SmsCbMessage message = createMessage(getTestMessageBody(systemLang),
                getDifferentLanguage(systemLang));
        TranslationTestSetupResult setup = setupActivityForTranslationTest(message, true, true,
                true);

        // Simulate translation completion
        getInstrumentation().runOnMainSync(() -> {
            setup.dialog.onTranslationCompleted("Translated Text", true);
        });

        // Verify mTranslateDone is true
        assertTrue("mTranslateDone should be true after translation",
                getTranslateDone(setup.dialog));
    }

    /**
     * Helper method to access the private mTranslateDone field via reflection.
     */
    private boolean getTranslateDone(CellBroadcastAlertDialog activity) {
        try {
            java.lang.reflect.Field field = CellBroadcastAlertDialog.class.getDeclaredField(
                    "mTranslateDone");
            field.setAccessible(true);
            return field.getBoolean(activity);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Reflection error accessing mTranslateDone: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tests that the translate button remains hidden after screen off/on (onResume)
     * if translation was already completed.
     */
    public void testTranslateButtonRemainsHiddenAfterScreenOffOn() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        String systemLang = Locale.getDefault().getLanguage();
        SmsCbMessage message = createMessage(getTestMessageBody(systemLang),
                getDifferentLanguage(systemLang));
        TranslationTestSetupResult setup = setupActivityForTranslationTest(message, true, true,
                true);

        getInstrumentation().runOnMainSync(() -> {
            setup.dialog.onTranslationCompleted("Translated Text", true);
        });

        clearInvocations(mMockCBButtonManager);

        getInstrumentation().runOnMainSync(() -> {
            setup.dialog.onResume();
        });

        assertTrue("mTranslateDone should remain true after onResume",
                getTranslateDone(setup.dialog));
        verify(mMockCBButtonManager, atLeastOnce()).configureButtons(eq(false), anyBoolean());
    }

    /**
     * Tests that the translation state is reset when a new message arrives,
     * allowing the translate button to be shown again.
     */
    public void testTranslateButtonResetOnNewMessage() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        String systemLang = Locale.getDefault().getLanguage();
        SmsCbMessage message = createMessage(getTestMessageBody(systemLang),
                getDifferentLanguage(systemLang));
        TranslationTestSetupResult setup = setupActivityForTranslationTest(message, true, true,
                true);

        // 1. Complete translation for the first message
        getInstrumentation().runOnMainSync(() -> {
            setup.dialog.onTranslationCompleted("Translated Text 1", true);
        });

        assertTrue(getTranslateDone(setup.dialog));

        // 2. Arrive new message
        SmsCbMessage newMessage = createMessage(getTestMessageBody(systemLang) + " 2",
                getDifferentLanguage(systemLang));
        Intent newIntent = createIntentWithMessage(newMessage);

        // Reset mock to clear previous interactions
        clearInvocations(mMockCBButtonManager);

        // Simulate new intent arrival
        getInstrumentation().runOnMainSync(() -> {
            setup.dialog.onNewIntent(newIntent);
        });

        assertFalse("mTranslateDone should be reset to false for new message",
                getTranslateDone(setup.dialog));
    }

    /**
     * Tests that when the system language is Traditional Chinese (Taiwan),
     * initTranslate requests translation to "zh_Hant".
     */
    public void testInitTranslateTargetZhTwRequestsZhHant() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.TAIWAN);
            SmsCbMessage message = createMessage("Test Message", "en");
            TranslationTestSetupResult result = setupActivityForTranslationTest(message,
                    true, true, true);
            doAnswer(invocation -> {
                Consumer<ULocale> callback = invocation.getArgument(1);
                callback.accept(new ULocale("zh_Hant"));
                return null;
            }).when(mMockCBTranslateManager).resolveTargetLanguage(any(), any());

            getInstrumentation().runOnMainSync(() -> {
                result.dialog.initTranslate(message);
            });

            verify(mMockCBTranslateManager, atLeastOnce()).initializeTranslator(any(ULocale.class),
                    eq(new ULocale("zh_Hant")));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * Tests that when the system language is Simplified Chinese (China),
     * initTranslate requests translation to "zh" (default behavior).
     */
    public void testInitTranslateTargetZhCnRequestsZh() {
        // Skip on watches: The translation feature is explicitly disabled on Wear OS
        // (!isWatch() in isTranslateFeatureEnabled).
        if (mIsWatch) {
            return;
        }

        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.CHINA);
            SmsCbMessage message = createMessage("Test Message", "en");
            TranslationTestSetupResult result = setupActivityForTranslationTest(message,
                    true, true, true);
            doAnswer(invocation -> {
                Consumer<ULocale> callback = invocation.getArgument(1);
                callback.accept(new ULocale("zh"));
                return null;
            }).when(mMockCBTranslateManager).resolveTargetLanguage(any(), any());

            getInstrumentation().runOnMainSync(() -> {
                result.dialog.initTranslate(message);
            });

            verify(mMockCBTranslateManager, atLeastOnce()).initializeTranslator(any(ULocale.class),
                    eq(new ULocale("zh")));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    public void testInitTranslateOnDeviceTranslationNotSupportedHidesButton() {
        // Skip on watches: This test yields a false positive on Wear OS because the early
        // !isWatch() check short-circuits the logic to false before the intended conditions
        // are ever evaluated.
        if (mIsWatch) {
            return;
        }

        String systemLang = Locale.getDefault().getLanguage();
        SmsCbMessage message = createMessage("Test Message", getDifferentLanguage(systemLang));
        TranslationTestSetupResult result = setupActivityForTranslationTest(message, true, true,
                true);
        CellBroadcastAlertDialog activity = result.dialog;

        doAnswer(invocation -> {
            Consumer<Boolean> callback = invocation.getArgument(0);
            callback.accept(false);
            return null;
        }).when(mMockCBTranslateManager).checkOnDeviceTranslationCapability(any());

        getInstrumentation().runOnMainSync(() -> activity.initTranslate(message));

        assertFalse("Should not offer translation", getShouldOfferTranslation(activity));
    }

    public void testInitTranslateTranslationManagerNotAvailableShouldNotOffer() {
        // Skip on watches: This test yields a false positive on Wear OS because the early
        // !isWatch() check short-circuits the logic to false before the intended conditions
        // are ever evaluated.
        if (mIsWatch) {
            return;
        }

        SmsCbMessage message = createMessage("Test Message", "en");
        TranslationTestSetupResult result = setupActivityForTranslationTest(message, true, true,
                true);
        CellBroadcastAlertDialog activity = result.dialog;

        doReturn(false).when(mMockCBTranslateManager).isTranslationManagerAvailable();

        getInstrumentation().runOnMainSync(() -> activity.initTranslate(message));

        assertFalse("Should not offer translation if TranslationManager is not available",
                getShouldOfferTranslation(activity));
        verify(mMockCBTranslateManager, never()).checkOnDeviceTranslationCapability(any());
    }

    public void testInitTranslateNoSettingsIntentShouldNotOffer() {
        // Skip on watches: This test yields a false positive on Wear OS because the early
        // !isWatch() check short-circuits the logic to false before the intended conditions
        // are ever evaluated.
        if (mIsWatch) {
            return;
        }

        SmsCbMessage message = createMessage("Test Message", "en");
        TranslationTestSetupResult result = setupActivityForTranslationTest(message, true, true,
                true);
        CellBroadcastAlertDialog activity = result.dialog;
        doReturn(null).when(mMockCBTranslateManager).getSettingsIntent();

        getInstrumentation().runOnMainSync(() -> activity.initTranslate(message));

        assertFalse("Should not offer translation if SettingsIntent is null",
                getShouldOfferTranslation(activity));
        verify(mMockCBTranslateManager, never()).checkOnDeviceTranslationCapability(any());
    }

    public void testInitTranslateEmptyMessageBodyShouldNotOffer() {
        // Skip on watches: This test yields a false positive on Wear OS because the early
        // !isWatch() check short-circuits the logic to false before the intended conditions
        // are ever evaluated.
        if (mIsWatch) {
            return;
        }

        SmsCbMessage message = createMessage("", "en");
        TranslationTestSetupResult result = setupActivityForTranslationTest(message, true, true,
                true);
        CellBroadcastAlertDialog activity = result.dialog;

        getInstrumentation().runOnMainSync(() -> activity.initTranslate(message));

        assertFalse("Should not offer translation if message body is empty",
                getShouldOfferTranslation(activity));
        verify(mMockCBTranslateManager, never()).checkOnDeviceTranslationCapability(any());
    }

    public void testInitTranslateInvalidSourceLanguageTriggersDetection() {
        // Skip on watches: Translation is explicitly disabled on Wear OS.
        // This test relies on heavily mocked state and static flag injection that bypasses
        // realistic device constraints.
        if (mIsWatch) {
            return;
        }

        SmsCbMessage message = createMessage("Message with invalid lang", "invalid_code");
        TranslationTestSetupResult result = setupActivityForTranslationTest(message, true, true,
                true);
        doAnswer(invocation -> {
            Consumer<Boolean> callback = invocation.getArgument(0);
            callback.accept(true);
            return null;
        }).when(mMockCBTranslateManager).checkOnDeviceTranslationCapability(any());

        getInstrumentation().runOnMainSync(() -> {
            result.dialog.initTranslate(message);
        });

        verify(mMockCBTranslateManager).detectLanguage(eq("Message with invalid lang"));
        verify(mMockCBTranslateManager, never()).initializeTranslator(any(), any());
    }


    public void testIsMapFeatureEnabledWhenActivityAvailableReturnsTrue() throws Throwable {
        // On Wear OS, alerts are displayed as notifications rather than using the full-screen
        // alert dialog. Therefore, testing dialog-specific UI logic is not applicable.
        if (mIsWatch) {
            return;
        }
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;

        CellBroadcastAlertDialog activity = startActivity();
        assertTrue("Map feature should be enabled if activity is available",
                activity.isMapFeatureEnabled(mSmsCbMessageWithGeo));
        stopActivity();
    }

    public void testIsMapFeatureEnabledWhenActivityNotAvailableReturnsFalse() throws Throwable {
        // On Wear OS, alerts are displayed as notifications rather than using the full-screen
        // alert dialog. Therefore, testing dialog-specific UI logic is not applicable.
        if (mIsWatch) {
            return;
        }
        setMapConfigEnabled(true);
        setMapActivityAvailable(false);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;

        CellBroadcastAlertDialog activity = startActivity();
        assertFalse("Map feature should be disabled if no activity can handle the intent",
                activity.isMapFeatureEnabled(mSmsCbMessageWithGeo));
        stopActivity();
    }

    private void setMapActivityAvailable(boolean available) {
        CellBroadcastMapLauncher.sIsMapActivityAvailableForTest = available;
    }

    public void testUpdateButtonsOnDismiss() throws Throwable {
        // On Wear OS, alerts are displayed as notifications rather than using the full-screen
        // alert dialog. Therefore, testing dialog-specific UI logic is not applicable.
        if (mIsWatch) {
            return;
        }
        setMapConfigEnabled(true);
        setMapActivityAvailable(true);
        CellBroadcastAlertDialog.sIsMapFeatureEnabledForTest = true;

        SmsCbMessage msgNoGeo = createSmsCbMessage(false);
        SmsCbMessage msgWithGeo = createSmsCbMessage(true);
        ArrayList<SmsCbMessage> list = new ArrayList<>();
        list.add(msgNoGeo);
        list.add(msgWithGeo);

        Intent intent = new Intent(mContext, CellBroadcastAlertDialog.class);
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA, list);

        CellBroadcastAlertDialog activity = startActivitySetMock(intent);

        getInstrumentation().runOnMainSync(() -> activity.updateButtons(msgWithGeo));
        verify(mMockCBButtonManager, atLeastOnce()).configureButtons(anyBoolean(), eq(true));
        clearInvocations(mMockCBButtonManager);

        getInstrumentation().runOnMainSync(() -> {
            activity.dismiss();
        });
        getInstrumentation().waitForIdleSync();

        verify(mMockCBButtonManager, atLeastOnce()).configureButtons(anyBoolean(), eq(false));
    }
}
