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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Looper;
import android.telephony.CbGeoUtils.Circle;
import android.telephony.CbGeoUtils.Geometry;
import android.telephony.CbGeoUtils.LatLng;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbLocation;
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
import com.android.internal.telephony.gsm.SmsCbConstants;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        SubscriptionManager mockSubManager = mock(SubscriptionManager.class);
        injectSystemService(SubscriptionManager.class, mockSubManager);
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mockSubManager).getActiveSubscriptionInfo(anyInt());
        Intent dummyIntent = new Intent();
        PendingIntent realPendingIntent = PendingIntent.getActivity(
                getInstrumentation().getTargetContext(), 0, dummyIntent,
                PendingIntent.FLAG_IMMUTABLE);
        doReturn(realPendingIntent).when(mMockTranslateManager).getSettingsIntent();
        CellBroadcastAlertDialog activity = getActivity();
        if (activity != null) {
            setupButtonManager(activity);
        }
    }

    @After
    public void tearDown() throws Exception {
        CellBroadcastAlertDialog.sIsTranslateFeatureEnabledForTest = null;
        super.tearDown();
    }

    /**
     * Finds a button in the button bar by its displayed text.
     */
    private Button findButtonByText(String text) {
        mButtonBar = getActivity().findViewById(R.id.button_bar);
        if (mButtonBar == null) return null;

        for (int i = 0; i < mButtonBar.getChildCount(); i++) {
            View child = mButtonBar.getChildAt(i);
            // Check for buttons wrapped in a FrameLayout (used for the progress bar overlay)
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

    /**
     * Finds the ProgressBar in the button bar. It is expected to be inside a FrameLayout.
     */
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

    /**
     * Initializes the CellBroadcastAlertButtonManager.
     */
    private void setupButtonManager(CellBroadcastAlertDialog activity) {
        activity.setTranslateManagerForTest(mMockTranslateManager);
        mButtonManager = new CellBroadcastAlertButtonManager(activity, mMockTranslateClickListener,
                mMockMapClickListener);
        mButtonBar = activity.findViewById(R.id.button_bar);
        mDismissButton = activity.findViewById(R.id.dismissButton);
    }

    /**
     * Starts a new activity with a specific intent and sets up the ButtonManager.
     * This is useful for tests that require a specific initial message/intent.
     * The original Looper.prepare() is only needed if not running in a TestRunner environment,
     * but is kept for safety if the test environment changes.
     */
    private CellBroadcastAlertDialog startActivityAndSetupButtonManager(Intent intent) {
        Looper.prepare();
        startActivity(intent, null, null);
        getInstrumentation().waitForIdleSync();
        CellBroadcastAlertDialog activity = getActivity();
        assertNotNull("Activity should not be null", activity);
        setupButtonManager(activity);

        doNothing().when(mMockTranslateManager).detectLanguage(anyString());
        getInstrumentation().waitForIdleSync();
        return activity;
    }

    /**
     * Helper to create a basic SmsCbMessage with specific body and language.
     */
    private SmsCbMessage createMessage(String messageBody, String language) {
        return new SmsCbMessage(1, 1, 1, new SmsCbLocation("123456"),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED, language,
                messageBody, 3, (SmsCbEtwsInfo) null,
                (SmsCbCmasInfo) null, 0, 0);
    }

    /**
     * Helper to create an SmsCbMessage, optionally with geographical information.
     */
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
        SmsCbCmasInfo cmasInfo = new SmsCbCmasInfo(0, 0, 0, 0, 0, 0);
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

    /**
     * Helper to create an Intent containing the given SmsCbMessage.
     */
    private Intent createIntentWithMessage(SmsCbMessage message) {
        ArrayList<SmsCbMessage> messageList = new ArrayList<>();
        messageList.add(message);
        Intent intent = new Intent(mContext, CellBroadcastAlertDialog.class);
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                messageList);
        return intent;
    }

    /**
     * Sets up the environment for tests focusing on the translate button.
     */
    void prepareTranslateTestEnvironment() {
        SmsCbMessage message = createMessage("Test Message", "invalid-lang");
        Intent intent = createIntentWithMessage(message);
        startActivityAndSetupButtonManager(intent);
    }

    /**
     * Sets up the environment for tests focusing on the map button.
     */
    void prepareMapTestEnvironment() {
        SmsCbMessage message = createSmsCbMessage(true);
        Intent intent = createIntentWithMessage(message);
        startActivityAndSetupButtonManager(intent);
    }

    public void testConfigureButtonsShowDismissOnly() throws Throwable {
        prepareTranslateTestEnvironment();

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
    }

    public void testConfigureButtonsShowMapOnly() throws Throwable {
        prepareMapTestEnvironment();
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
        LinearLayout.LayoutParams mapContainerParams =
                (LinearLayout.LayoutParams) mButtonBar.getChildAt(
                        0).getLayoutParams();
        assertEquals("Map button container weight should be 1.0", 1.0f, mapContainerParams.weight,
                0.01f);
    }

    public void testConfigureButtonsShowTranslateOnly() throws Throwable {
        prepareTranslateTestEnvironment();
        runTestOnUiThread(() -> mButtonManager.configureButtons(true, false));
        waitForUiThreadToSettle();

        assertEquals("Button bar should have two children", 2, mButtonBar.getChildCount());
        assertNotNull("Translate button should be present", findTranslateButton());
        assertNotNull("Dismiss button should be present", mDismissButton);
        assertEquals("Translate button should be visible", View.VISIBLE,
                findTranslateButton().getVisibility());
        assertNull("Map button should not be present", findMapButton());
    }

    public void testConfigureButtonsShowAll() throws Throwable {
        prepareMapTestEnvironment();

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
    }

    public void testMapButtonClick() throws Throwable {
        prepareMapTestEnvironment();
        runTestOnUiThread(() -> mButtonManager.configureButtons(false, true));
        waitForUiThreadToSettle();

        Button mapButton = findMapButton();
        assertNotNull(mapButton);
        runTestOnUiThread(() -> mapButton.performClick());
        waitForUiThreadToSettle();

        verify(mMockMapClickListener).onMapClick();
    }

    public void testTranslateButtonClick() throws Throwable {
        prepareTranslateTestEnvironment();

        runTestOnUiThread(() -> mButtonManager.configureButtons(true, false));
        getInstrumentation().waitForIdleSync();

        Button translateButton = findTranslateButton();
        assertNotNull("Translate button should be visible after manual configure", translateButton);

        runTestOnUiThread(() -> translateButton.performClick());
        getInstrumentation().waitForIdleSync();

        verify(mMockTranslateClickListener).onTranslateClick();
    }

    public void testShowTranslationInProgressTogglesVisibility() throws Throwable {
        prepareTranslateTestEnvironment();
        runTestOnUiThread(() -> mButtonManager.configureButtons(true, false));
        waitForUiThreadToSettle();

        Button translateButton = findTranslateButton();
        assertNotNull("Translate button should be present", translateButton);
        ProgressBar progressBar = findProgressBar();
        assertNotNull("Progress bar should be present", progressBar);

        // Test show in progress
        runTestOnUiThread(() -> mButtonManager.showTranslationInProgress(true));
        waitForUiThreadToSettle();
        assertEquals("Progress bar should be visible.", View.VISIBLE, progressBar.getVisibility());
        assertEquals("Translate button should be GONE.", View.GONE,
                translateButton.getVisibility());

        // Test hide in progress
        runTestOnUiThread(() -> mButtonManager.showTranslationInProgress(false));
        waitForUiThreadToSettle();
        assertEquals("Progress bar should be GONE.", View.GONE, progressBar.getVisibility());
        assertEquals("Translate button should be visible.", View.VISIBLE,
                translateButton.getVisibility());
    }

    public void testOnTranslationCompletedOnlyTranslateWasShown() throws Throwable {
        prepareTranslateTestEnvironment();
        runTestOnUiThread(() -> mButtonManager.configureButtons(true, false));
        waitForUiThreadToSettle();
        assertNotNull(findTranslateButton());

        runTestOnUiThread(() -> mButtonManager.onTranslationCompleted());
        waitForUiThreadToSettle();

        assertEquals("Button bar should have one child", 1, mButtonBar.getChildCount());
        assertNull("Translate button should be gone", findTranslateButton());
        assertNotNull("Dismiss button should be present", mDismissButton);
    }

    public void testOnTranslationCompletedMapAndTranslateWereShown() throws Throwable {
        prepareMapTestEnvironment();
        runTestOnUiThread(() -> mButtonManager.configureButtons(true, true));
        waitForUiThreadToSettle();

        runTestOnUiThread(() -> mButtonManager.onTranslationCompleted());
        waitForUiThreadToSettle();

        assertEquals("Button bar should have two children", 2, mButtonBar.getChildCount());
        assertNull("Translate button should be gone", findTranslateButton());
        assertNotNull("Map button should still be present", findMapButton());
        assertNotNull("Dismiss button should be present", mDismissButton);
    }

    /**
     * Configures the test environment by simulating a "translation required" scenario
     * based on the current system language, and includes location information for the Map button.
     */
    private void prepareMapAndTranslateTestEnvironment() {
        Locale currentLocale = Locale.getDefault();
        String systemLang = currentLocale.getLanguage();

        String messageLang = "en".equals(systemLang) ? "es" : "en";
        String messageBody = "Test Message in " + messageLang;

        List<Geometry> geometries = new ArrayList<>();
        geometries.add(new Circle(new LatLng(37.422, -122.084), 1000.0));

        SmsCbMessage message = new SmsCbMessage(1, 0, 123, new SmsCbLocation("310260"),
                4370, messageLang, 0, messageBody, 3, null, null,
                255, geometries, System.currentTimeMillis(), 0, 1);

        Intent intent = createIntentWithMessage(message);
        startActivityAndSetupButtonManager(intent);

        try {
            doNothing().when(mMockTranslateManager).detectLanguage(anyString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testConfigureButtonsIdempotent() throws Throwable {
        prepareMapAndTranslateTestEnvironment();

        runTestOnUiThread(() -> mButtonManager.configureButtons(true, true));
        waitForUiThreadToSettle();

        assertEquals("Three buttons (Dismiss, Map, Translate) should be visible.", 3,
                mButtonBar.getChildCount());

        runTestOnUiThread(() -> mButtonManager.configureButtons(true, true));
        waitForUiThreadToSettle();
        assertEquals("The button count should remain 3 even after calling again.", 3,
                mButtonBar.getChildCount());

        runTestOnUiThread(() -> mButtonManager.configureButtons(false, false));
        waitForUiThreadToSettle();
        assertEquals("Only 1 button (Dismiss) should remain when options are disabled.", 1,
                mButtonBar.getChildCount());

        runTestOnUiThread(() -> mButtonManager.configureButtons(false, false));
        waitForUiThreadToSettle();
        assertEquals("The button count should remain 1 even after calling again.", 1,
                mButtonBar.getChildCount());
    }
}
