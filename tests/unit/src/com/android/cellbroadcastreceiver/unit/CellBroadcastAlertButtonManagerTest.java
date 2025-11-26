/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.PendingIntent;
import android.content.Intent;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.android.cellbroadcastreceiver.CellBroadcastAlertButtonManager;
import com.android.cellbroadcastreceiver.CellBroadcastAlertDialog;
import com.android.cellbroadcastreceiver.CellBroadcastAlertService;
import com.android.cellbroadcastreceiver.CellBroadcastTranslateManager;
import com.android.cellbroadcastreceiver.R;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

/**
 * Instrumentation tests for the {@link CellBroadcastAlertButtonManager}.
 *
 * This class tests the mechanical UI manipulation logic of the button manager, such as changing
 * layouts, independent of the decision-making logic in the {@link CellBroadcastAlertDialog}.
 */
public class CellBroadcastAlertButtonManagerTest
        extends CellBroadcastActivityTestCase<CellBroadcastAlertDialog> {

    private CellBroadcastAlertButtonManager mButtonManager;
    private LinearLayout mButtonBar;
    private Button mDismissButton;
    @Mock
    private CellBroadcastTranslateManager mMockTranslateManager;
    @Mock
    private CellBroadcastAlertButtonManager.OnMapButtonClickListener mMockMapClickListener;
    @Mock
    private CellBroadcastAlertButtonManager.OnTranslateButtonClickListener
            mMockTranslateClickListener;

    public CellBroadcastAlertButtonManagerTest() {
        super(CellBroadcastAlertDialog.class);
    }

    @Override
    protected Intent createActivityIntent() {
        ArrayList<SmsCbMessage> messageList = new ArrayList<>();
        messageList.add(CellBroadcastAlertServiceTest.createMessageForCmasMessageClass(1, 1, 1));

        Intent intent =
                new Intent(getInstrumentation().getTargetContext(), CellBroadcastAlertDialog.class);
        intent.putParcelableArrayListExtra(
                CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA, messageList);
        return intent;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        CellBroadcastAlertDialog.sIsTranslateFeatureEnabledForTest = true;
        CellBroadcastAlertDialog.sIsWatchForTest = false;
        doReturn(true).when(mContext.getResources()).getBoolean(R.bool.enable_alert_translation);
        doReturn(true).when(mContext.getResources()).getBoolean(R.bool.enable_map);
        Intent dummyIntent = new Intent();
        PendingIntent realPendingIntent = PendingIntent.getActivity(
                getInstrumentation().getTargetContext(), 0, dummyIntent,
                PendingIntent.FLAG_IMMUTABLE);
        doReturn(realPendingIntent).when(mMockTranslateManager).getSettingsIntent();

        SubscriptionManager mockSubManager = mock(SubscriptionManager.class);
        injectSystemService(SubscriptionManager.class, mockSubManager);
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mockSubManager).getActiveSubscriptionInfo(anyInt());
    }

    @After
    public void tearDown() throws Exception {
        CellBroadcastAlertDialog.sIsTranslateFeatureEnabledForTest = null;
        super.tearDown();
    }

    private Button findButtonByText(String text) {
        mButtonBar = getActivity().findViewById(R.id.button_bar);
        if (mButtonBar == null) return null;

        for (int i = 0; i < mButtonBar.getChildCount(); i++) {
            View child = mButtonBar.getChildAt(i);
            if (child instanceof FrameLayout) {
                FrameLayout container = (FrameLayout) child;
                for (int j = 0; j < container.getChildCount(); j++) {
                    View innerChild = container.getChildAt(j);
                    if (innerChild instanceof Button) {
                        Button button = (Button) innerChild;
                        if (text.equals(button.getText().toString())) {
                            return button;
                        }
                    }
                }
            } else if (child instanceof Button) {
                Button button = (Button) child;
                if (text.equals(button.getText().toString())) {
                    return button;
                }
            }
        }
        return null;
    }

    private Button findMapButton() {
        return findButtonByText(mContext.getString(R.string.button_map));
    }

    private Button findTranslateButton() {
        return findButtonByText(mContext.getString(R.string.button_translate));
    }

    private void waitForUiThreadToSettle() {
        getInstrumentation().waitForIdleSync();
    }

    private ProgressBar findProgressBar() {
        mButtonBar = getActivity().findViewById(R.id.button_bar);
        if (mButtonBar == null) return null;
        for (int i = 0; i < mButtonBar.getChildCount(); i++) {
            View child = mButtonBar.getChildAt(i);
            if (child instanceof FrameLayout) {
                FrameLayout container = (FrameLayout) child;
                for (int j = 0; j < container.getChildCount(); j++) {
                    View innerChild = container.getChildAt(j);
                    if (innerChild instanceof ProgressBar) {
                        return (ProgressBar) innerChild;
                    }
                }
            }
        }
        return null;
    }

    private void setupButtonManager() {
        CellBroadcastAlertDialog activity = getActivity();
        activity.setTranslateManagerForTest(mMockTranslateManager);
        mButtonManager = new CellBroadcastAlertButtonManager(activity, mMockTranslateClickListener,
                mMockMapClickListener);
        mButtonBar = activity.findViewById(R.id.button_bar);
        mDismissButton = activity.findViewById(R.id.dismissButton);
    }

    public void testConfigureButtonsShowDismissOnly() throws Throwable {
        startActivity();
        setupButtonManager();

        runTestOnUiThread(() -> mButtonManager.configureButtons(false, false));
        waitForUiThreadToSettle();

        assertEquals("Button bar should have one child", 1, mButtonBar.getChildCount());
        assertNotNull("Dismiss button should be present", mDismissButton);
        assertEquals("Dismiss button should be visible", View.VISIBLE,
                mDismissButton.getVisibility());
        assertNull("Map button should not be present", findMapButton());
        assertNull("Translate button should not be present", findTranslateButton());

        LinearLayout.LayoutParams dismissParams =
                (LinearLayout.LayoutParams) mDismissButton.getLayoutParams();
        assertEquals("Dismiss button weight should be 0", 0f, dismissParams.weight, 0.01f);
        assertTrue("Dismiss button width should be WRAP_CONTENT",
                dismissParams.width == LinearLayout.LayoutParams.WRAP_CONTENT);
        stopActivity();
    }

    public void testConfigureButtonsShowMapOnly() throws Throwable {
        startActivity();
        setupButtonManager();

        runTestOnUiThread(() -> mButtonManager.configureButtons(false, true));
        waitForUiThreadToSettle();

        assertEquals("Button bar should have two children", 2, mButtonBar.getChildCount());
        assertNotNull("Map button should be present", findMapButton());
        assertNotNull("Dismiss button should be present", mDismissButton);
        assertEquals("Map button should be visible", View.VISIBLE, findMapButton().getVisibility());
        assertNull("Translate button should not be present", findTranslateButton());

        LinearLayout.LayoutParams dismissParams =
                (LinearLayout.LayoutParams) mDismissButton.getLayoutParams();
        assertEquals("Dismiss button weight should be 1.0", 1.0f, dismissParams.weight, 0.01f);
        LinearLayout.LayoutParams mapParams = (LinearLayout.LayoutParams) mButtonBar.getChildAt(
                0).getLayoutParams();
        assertEquals("Map button weight should be 1.0", 1.0f, mapParams.weight, 0.01f);
        stopActivity();
    }

    public void testConfigureButtonsShowTranslateOnly() throws Throwable {
        startActivity();
        setupButtonManager();

        runTestOnUiThread(() -> mButtonManager.configureButtons(true, false));
        waitForUiThreadToSettle();

        assertEquals("Button bar should have two children", 2, mButtonBar.getChildCount());
        assertNotNull("Translate button should be present", findTranslateButton());
        assertNotNull("Dismiss button should be present", mDismissButton);
        assertEquals("Translate button should be visible", View.VISIBLE,
                findTranslateButton().getVisibility());
        assertNull("Map button should not be present", findMapButton());
        stopActivity();
    }

    public void testConfigureButtonsShowAll() throws Throwable {
        startActivity();
        setupButtonManager();

        runTestOnUiThread(() -> mButtonManager.configureButtons(true, true));
        waitForUiThreadToSettle();

        assertEquals("Button bar should have three children", 3, mButtonBar.getChildCount());
        assertNotNull("Map button should be present", findMapButton());
        assertNotNull("Translate button should be present", findTranslateButton());
        assertNotNull("Dismiss button should be present", mDismissButton);

        View mapContainer = mButtonBar.getChildAt(0);
        View translateContainer = mButtonBar.getChildAt(1);
        Button dismissButton = (Button) mButtonBar.getChildAt(2);

        assertTrue("First child should contain Map button",
                ((FrameLayout) mapContainer).getChildAt(0) instanceof Button);
        assertTrue("Second child should contain Translate button",
                ((FrameLayout) translateContainer).getChildAt(0) instanceof Button);
        assertEquals("Third child should be Dismiss button",
                mContext.getString(R.string.button_dismiss), dismissButton.getText().toString());

        stopActivity();
    }

    public void testMapButtonClick() throws Throwable {
        startActivity();
        setupButtonManager();
        runTestOnUiThread(() -> mButtonManager.configureButtons(false, true));
        waitForUiThreadToSettle();

        Button mapButton = findMapButton();
        assertNotNull(mapButton);
        runTestOnUiThread(() -> mapButton.performClick());
        waitForUiThreadToSettle();

        verify(mMockMapClickListener).onMapClick();
        stopActivity();
    }

    public void testTranslateButtonClick() throws Throwable {
        startActivity();
        setupButtonManager();
        runTestOnUiThread(() -> mButtonManager.configureButtons(true, false));
        waitForUiThreadToSettle();

        Button translateButton = findTranslateButton();
        assertNotNull(translateButton);
        runTestOnUiThread(() -> translateButton.performClick());
        waitForUiThreadToSettle();

        verify(mMockTranslateClickListener).onTranslateClick();
        stopActivity();
    }

    public void testShowTranslationInProgressTogglesVisibility() throws Throwable {
        startActivity();
        setupButtonManager();
        runTestOnUiThread(() -> mButtonManager.configureButtons(true, false));
        waitForUiThreadToSettle();

        Button translateButton = findTranslateButton();
        assertNotNull("Translate button should be present", translateButton);
        ProgressBar progressBar = findProgressBar();
        assertNotNull("Progress bar should be present", progressBar);

        runTestOnUiThread(() -> mButtonManager.showTranslationInProgress(true));
        waitForUiThreadToSettle();
        assertEquals("Progress bar should be visible.", View.VISIBLE, progressBar.getVisibility());
        assertEquals("Translate button should be GONE.", View.GONE,
                translateButton.getVisibility());

        runTestOnUiThread(() -> mButtonManager.showTranslationInProgress(false));
        waitForUiThreadToSettle();
        assertEquals("Progress bar should be GONE.", View.GONE, progressBar.getVisibility());
        assertEquals("Translate button should be visible.", View.VISIBLE,
                translateButton.getVisibility());
        stopActivity();
    }

    public void testOnTranslationCompletedOnlyTranslateWasShown() throws Throwable {
        startActivity();
        setupButtonManager();
        runTestOnUiThread(() -> mButtonManager.configureButtons(true, false));
        waitForUiThreadToSettle();
        assertNotNull(findTranslateButton());

        runTestOnUiThread(() -> mButtonManager.onTranslationCompleted());
        waitForUiThreadToSettle();

        assertEquals("Button bar should have one child", 1, mButtonBar.getChildCount());
        assertNull("Translate button should be gone", findTranslateButton());
        assertNotNull("Dismiss button should be present", mDismissButton);
        stopActivity();
    }

    public void testOnTranslationCompletedMapAndTranslateWereShown() throws Throwable {
        startActivity();
        setupButtonManager();
        runTestOnUiThread(() -> mButtonManager.configureButtons(true, true));
        waitForUiThreadToSettle();

        runTestOnUiThread(() -> mButtonManager.onTranslationCompleted());
        waitForUiThreadToSettle();

        assertEquals("Button bar should have two children", 2, mButtonBar.getChildCount());
        assertNull("Translate button should be gone", findTranslateButton());
        assertNotNull("Map button should still be present", findMapButton());
        assertNotNull("Dismiss button should be present", mDismissButton);
        stopActivity();
    }

    public void testConfigureButtonsIdempotent() throws Throwable {
        startActivity();
        setupButtonManager();

        runTestOnUiThread(() -> mButtonManager.configureButtons(true, true));
        waitForUiThreadToSettle();
        assertEquals(3, mButtonBar.getChildCount());

        runTestOnUiThread(() -> mButtonManager.configureButtons(true, true));
        waitForUiThreadToSettle();
        assertEquals("Should still be 3 children", 3, mButtonBar.getChildCount());

        runTestOnUiThread(() -> mButtonManager.configureButtons(false, false));
        waitForUiThreadToSettle();
        assertEquals(1, mButtonBar.getChildCount());

        runTestOnUiThread(() -> mButtonManager.configureButtons(false, false));
        waitForUiThreadToSettle();
        assertEquals("Should still be 1 child", 1, mButtonBar.getChildCount());
        stopActivity();
    }
}
