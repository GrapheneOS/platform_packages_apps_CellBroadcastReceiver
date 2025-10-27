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

import android.app.PendingIntent;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsEnabled;
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
import com.android.cellbroadcastreceiver.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

/**
 * Instrumentation tests for the {@link CellBroadcastAlertButtonManager}.
 *
 * <p>This class tests the mechanical UI manipulation logic of the button manager, such as changing
 * layouts, independent of the decision-making logic in the {@link CellBroadcastAlertDialog}.
 */
public class CellBroadcastAlertButtonManagerTest
        extends CellBroadcastActivityTestCase<CellBroadcastAlertDialog> {

    private CellBroadcastAlertButtonManager mButtonManager;
    private LinearLayout mButtonBar;
    private Button mDismissButton;
    @Mock
    private CellBroadcastTranslateManager mMockTranslateManager;

    public CellBroadcastAlertButtonManagerTest() {
        super(CellBroadcastAlertDialog.class);
    }

    @Override
    protected Intent createActivityIntent() {
        // Use a minimal message list, as the content is not relevant for these tests.
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

    private Button findTranslateButton() {
        LinearLayout buttonBar = getActivity().findViewById(R.id.button_bar);
        if (buttonBar == null) return null;

        for (int i = 0; i < buttonBar.getChildCount(); i++) {
            View child = buttonBar.getChildAt(i);
            if (child instanceof FrameLayout) {
                FrameLayout container = (FrameLayout) child;
                for (int j = 0; j < container.getChildCount(); j++) {
                    View innerChild = container.getChildAt(j);
                    if (innerChild instanceof Button) {
                        return (Button) innerChild;
                    }
                }
            }
        }
        return null;
    }

    private ProgressBar findProgressBar() {
        LinearLayout buttonBar = getActivity().findViewById(R.id.button_bar);
        if (buttonBar == null) return null;

        for (int i = 0; i < buttonBar.getChildCount(); i++) {
            View child = buttonBar.getChildAt(i);
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
     * Test that calling {@link CellBroadcastAlertButtonManager#configureButtons(boolean)} with
     * {@code true} correctly sets up the two-button layout (Translate and Dismiss).
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testConfigureButtonsShowTranslateChangesLayoutToTwoButtons() throws Throwable {
        startActivity();
        CellBroadcastAlertDialog activity = getActivity();
        activity.setTranslateManagerForTest(mMockTranslateManager);
        mButtonManager = activity.getButtonManager();
        assertNotNull("ButtonManager should be initialized", mButtonManager);

        runTestOnUiThread(() -> {
            mButtonManager.configureButtons(true);

            mButtonBar = activity.findViewById(R.id.button_bar);
            mDismissButton = activity.findViewById(R.id.dismissButton);
            Button translateButton = findTranslateButton();

            assertNotNull("Translate button should be found", translateButton);
            assertEquals("Button bar should have two children.", 2, mButtonBar.getChildCount());
            assertEquals("Translate button should be visible.", View.VISIBLE,
                    translateButton.getVisibility());

            LinearLayout.LayoutParams dismissParams =
                    (LinearLayout.LayoutParams) mDismissButton.getLayoutParams();
            assertEquals("Dismiss button weight should be 1.0.", 1.0f, dismissParams.weight, 0.01f);
        });
        stopActivity();
    }

    /**
     * Test that calling {@link CellBroadcastAlertButtonManager#configureButtons(boolean)} with
     * {@code false} after being in a two-button state correctly reverts the UI back to the
     * original single-button layout, including restoring original padding.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testConfigureButtonsHideTranslateRevertsLayoutToSingleButton() throws Throwable {
        startActivity();
        CellBroadcastAlertDialog activity = getActivity();
        activity.setTranslateManagerForTest(mMockTranslateManager);
        CellBroadcastAlertButtonManager buttonManager = activity.getButtonManager();

        assertNotNull("ButtonManager should be initialized", buttonManager);

        runTestOnUiThread(() -> {
            Button dismissButton = activity.findViewById(R.id.dismissButton);
            final int originalPaddingStart = dismissButton.getPaddingStart();
            final int originalPaddingEnd = dismissButton.getPaddingEnd();

            buttonManager.configureButtons(true);

            assertNotSame("Padding should change for two-button layout",
                    originalPaddingStart, dismissButton.getPaddingStart());

            buttonManager.configureButtons(false);

            LinearLayout buttonBar = activity.findViewById(R.id.button_bar);
            View translateContainer = buttonBar.getChildAt(0);

            assertEquals("Translate container should be GONE.", View.GONE,
                    translateContainer.getVisibility());
            LinearLayout.LayoutParams dismissParams =
                    (LinearLayout.LayoutParams) dismissButton.getLayoutParams();
            assertEquals("Dismiss button weight should be 0.", 0f, dismissParams.weight, 0.01f);

            assertEquals("Dismiss button start padding should be restored.",
                    originalPaddingStart, dismissButton.getPaddingStart());
            assertEquals("Dismiss button end padding should be restored.",
                    originalPaddingEnd, dismissButton.getPaddingEnd());
        });
        stopActivity();
    }

    /**
     * Test that calling {@link CellBroadcastAlertButtonManager#onTranslationCompleted()}
     * correctly reverts the UI from a two-button layout back to the single-button layout.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testOnTranslationCompletedRevertsLayoutToSingleButton() throws Throwable {
        startActivity();
        CellBroadcastAlertDialog activity = getActivity();
        activity.setTranslateManagerForTest(mMockTranslateManager);
        mButtonManager = activity.getButtonManager();
        runTestOnUiThread(() -> mButtonManager.configureButtons(true));
        waitForMs(200);

        runTestOnUiThread(() -> mButtonManager.onTranslationCompleted());
        waitForMs(200);

        mButtonBar = activity.findViewById(R.id.button_bar);
        FrameLayout translateContainer = (FrameLayout) mButtonBar.getChildAt(0);
        assertEquals(
                "Translate container should be GONE after translation completes.",
                View.GONE,
                translateContainer.getVisibility());

        stopActivity();
    }

    /**
     * Tests that {@link CellBroadcastAlertButtonManager#showTranslationInProgress(boolean)}
     * correctly toggles the visibility of the translate button and the progress bar.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testShowTranslationInProgressTogglesVisibility() throws Throwable {
        // Arrange
        startActivity();
        CellBroadcastAlertDialog activity = getActivity();
        activity.setTranslateManagerForTest(mMockTranslateManager);
        mButtonManager = activity.getButtonManager();
        runTestOnUiThread(() -> mButtonManager.configureButtons(true));
        waitForMs(200);

        Button translateButton = findTranslateButton();
        assertNotNull("Translate button should be present", translateButton);
        ProgressBar progressBar = findProgressBar();
        assertNotNull("Progress bar should be present", progressBar);

        // Act 1: Show progress
        runTestOnUiThread(() -> mButtonManager.showTranslationInProgress(true));
        waitForMs(100);

        // Assert 1
        assertEquals("Progress bar should be visible.", View.VISIBLE, progressBar.getVisibility());
        assertEquals(
                "Translate button should be GONE.", View.GONE, translateButton.getVisibility());

        // Act 2: Hide progress
        runTestOnUiThread(() -> mButtonManager.showTranslationInProgress(false));
        waitForMs(100);

        // Assert 2
        assertEquals("Progress bar should be GONE.", View.GONE, progressBar.getVisibility());
        assertEquals(
                "Translate button should be visible.",
                View.VISIBLE,
                translateButton.getVisibility());

        stopActivity();
    }

    /**
     * Test that the manager is stateful and does not perform redundant UI changes when
     * {@link CellBroadcastAlertButtonManager#configureButtons(boolean)} is called multiple times
     * with the same state.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testConfigureButtonsIsStatefulAndIdempotent() throws Throwable {
        // Arrange
        startActivity();
        CellBroadcastAlertDialog activity = getActivity();
        activity.setTranslateManagerForTest(mMockTranslateManager);
        mButtonManager = activity.getButtonManager();
        mButtonBar = activity.findViewById(R.id.button_bar);

        // Act & Assert: Call to set two-button layout twice.
        runTestOnUiThread(() -> mButtonManager.configureButtons(true));
        waitForMs(200);
        assertEquals(
                "Button bar should have two children after first call.",
                2,
                mButtonBar.getChildCount());

        runTestOnUiThread(() -> mButtonManager.configureButtons(true));
        waitForMs(200);
        assertEquals(
                "Button bar should still have two children after second call.",
                2,
                mButtonBar.getChildCount());

        // Act & Assert: Call to set single-button layout twice.
        runTestOnUiThread(() -> mButtonManager.configureButtons(false));
        waitForMs(200);
        FrameLayout translateContainer = (FrameLayout) mButtonBar.getChildAt(0);
        assertEquals(
                "Translate container should be GONE after first call.",
                View.GONE,
                translateContainer.getVisibility());

        runTestOnUiThread(() -> mButtonManager.configureButtons(false));
        waitForMs(200);
        assertEquals(
                "Translate container should still be GONE after second call.",
                View.GONE,
                translateContainer.getVisibility());

        stopActivity();
    }
}
