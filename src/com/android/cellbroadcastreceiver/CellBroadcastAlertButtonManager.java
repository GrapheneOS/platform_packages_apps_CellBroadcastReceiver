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

package com.android.cellbroadcastreceiver;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/**
 * Manages the dynamic creation and layout of buttons in the CellBroadcastAlertDialog. This class is
 * responsible for adding a "Translate" button and a progress indicator, adjusting the layout to
 * accommodate them.
 */
public class CellBroadcastAlertButtonManager {

    private final Activity mActivity;
    private final LinearLayout mButtonBar;
    private final Button mDismissButton;

    private FrameLayout mTranslateContainer;
    private Button mTranslateButton;
    private ProgressBar mProgressBar;
    private boolean mIsTwoButtonLayout = false;
    private final int mDismissOriginalPaddingStart;
    private final int mDismissOriginalPaddingEnd;

    /**
     * Listener interface to communicate clicks on the dynamically created translate button back to
     * the hosting Activity.
     */
    public interface OnTranslateButtonClickListener {
        /** Called when the "Translate" button is clicked by the user. */
        void onTranslateClick();
    }

    private final OnTranslateButtonClickListener mListener;

    public CellBroadcastAlertButtonManager(
            Activity activity, OnTranslateButtonClickListener listener) {
        mActivity = activity;
        mListener = listener;
        mButtonBar = activity.findViewById(R.id.button_bar);
        mDismissButton = activity.findViewById(R.id.dismissButton);

        if (mDismissButton != null) {
            mDismissOriginalPaddingStart = mDismissButton.getPaddingStart();
            mDismissOriginalPaddingEnd = mDismissButton.getPaddingEnd();
        } else {
            mDismissOriginalPaddingStart = 0;
            mDismissOriginalPaddingEnd = 0;
        }
    }

    /**
     * Configures the button layout based on whether the translate button should be shown. This
     * method is state-aware and will only modify the UI if the requested state is different from
     * the current state.
     *
     * @param showTranslateButton True to show a two-button layout; false for the original
     *                            single-button layout.
     */
    public void configureButtons(boolean showTranslateButton) {
        if (mButtonBar == null || mDismissButton == null) {
            return;
        }

        if (showTranslateButton && !mIsTwoButtonLayout) {
            setupTwoButtonLayout();
            mIsTwoButtonLayout = true;
        } else if (!showTranslateButton && mIsTwoButtonLayout) {
            setupSingleButtonLayout();
            mIsTwoButtonLayout = false;
        }
    }

    /**
     * Shows or hides the translation progress indicator. When in progress, the translate button is
     * hidden and a progress bar is shown.
     *
     * @param inProgress True to show the progress bar; false to show the translate button.
     */
    public void showTranslationInProgress(boolean inProgress) {
        if (mTranslateButton != null) {
            mTranslateButton.setVisibility(inProgress ? View.GONE : View.VISIBLE);
        }
        if (mProgressBar != null) {
            mProgressBar.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Reverts the button layout to a single, centered "Dismiss" button. This is also called after a
     * successful translation.
     */
    public void onTranslationCompleted() {
        configureButtons(false);
    }

    /** Configures the UI for a two-button layout (Translate, Dismiss). */
    private void setupTwoButtonLayout() {
        // Lazily create the container that holds the translate button and progress bar.
        if (mTranslateContainer == null) {
            mTranslateContainer = new FrameLayout(mActivity);

            // Create and add the translate button and progress bar to the container.
            mTranslateButton = createTranslateButton();
            mProgressBar = createProgressBar();
            mTranslateContainer.addView(mTranslateButton);
            mTranslateContainer.addView(mProgressBar);

            // Add the container to the main button bar.
            mButtonBar.addView(mTranslateContainer, 0);
        }
        mTranslateContainer.setVisibility(View.VISIBLE);
        showTranslationInProgress(false); // Set initial state (show button, hide progress)

        // Adjust parent gravity to allow buttons to fill the space.
        mButtonBar.setGravity(Gravity.CENTER_VERTICAL);

        // Adjust dismiss button to share space.
        LinearLayout.LayoutParams dismissParams =
                (LinearLayout.LayoutParams) mDismissButton.getLayoutParams();
        dismissParams.width = 0;
        dismissParams.weight = 1.0f;
        mDismissButton.setPadding(
                0, mDismissButton.getPaddingTop(), 0, mDismissButton.getPaddingBottom());
        mDismissButton.setLayoutParams(dismissParams);

        // Adjust our new container to share space.
        LinearLayout.LayoutParams containerParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        mTranslateContainer.setLayoutParams(containerParams);
    }

    /** Configures the UI for the original single-button layout. */
    private void setupSingleButtonLayout() {
        // Hide the container for the translate button and progress bar.
        if (mTranslateContainer != null) {
            mTranslateContainer.setVisibility(View.GONE);
        }

        // Restore parent gravity to center the single button.
        mButtonBar.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

        // Restore dismiss button to its original state.
        LinearLayout.LayoutParams dismissParams =
                (LinearLayout.LayoutParams) mDismissButton.getLayoutParams();
        dismissParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        dismissParams.weight = 0f;

        // Restore padding using the stored values instead of a magic number.
        mDismissButton.setPaddingRelative(
                mDismissOriginalPaddingStart,
                mDismissButton.getPaddingTop(),
                mDismissOriginalPaddingEnd,
                mDismissButton.getPaddingBottom());
        mDismissButton.setLayoutParams(dismissParams);
    }

    /** Creates a new "Translate" button programmatically with the correct style. */
    private Button createTranslateButton() {
        Button button = new Button(mActivity, null, android.R.attr.buttonBarButtonStyle);
        button.setText(R.string.button_translate);
        button.setLayoutParams(
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        button.setOnClickListener(
                v -> {
                    if (mListener != null) {
                        mListener.onTranslateClick();
                    }
                });
        return button;
    }

    /** Creates a new ProgressBar programmatically. */
    private ProgressBar createProgressBar() {
        ProgressBar progressBar =
                new ProgressBar(mActivity, null, android.R.attr.progressBarStyleSmall);
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER);
        progressBar.setLayoutParams(params);
        progressBar.setVisibility(View.GONE);
        return progressBar;
    }
}
