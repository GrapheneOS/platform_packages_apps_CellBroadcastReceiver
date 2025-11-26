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
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/**
 * Manages the dynamic creation and layout of buttons in the CellBroadcastAlertDialog. This class
 * is responsible for adding a "Translate" button with a progress indicator, and a "Map" button,
 * adjusting the layout to accommodate them based on feature enablement and data availability.
 * The button order is Map, Translate, Dismiss.
 */
public class CellBroadcastAlertButtonManager {
    private static final String TAG = CellBroadcastAlertButtonManager.class.getSimpleName();
    private final Activity mActivity;
    private final LinearLayout mButtonBar;
    private final Button mDismissButton;

    private FrameLayout mTranslateContainer;
    private Button mTranslateButton;
    private ProgressBar mProgressBar;
    private final int mDismissOriginalPaddingStart;
    private final int mDismissOriginalPaddingEnd;
    private FrameLayout mMapContainer;
    private Button mMapButton;

    /**
     * Listener interface to communicate clicks on the dynamically created translate button back to
     * the hosting Activity.
     */
    public interface OnTranslateButtonClickListener {
        /** Called when the "Translate" button is clicked by the user. */
        void onTranslateClick();
    }

    private final OnTranslateButtonClickListener mTranslateListener;

    /**
     * Listener interface to communicate clicks on the dynamically created map button back to
     * the hosting Activity.
     */
    public interface OnMapButtonClickListener {
        /** Called when the "Map" button is clicked by the user. */
        void onMapClick();
    }

    private final OnMapButtonClickListener mMapListener;

    /**
     * Constructs the button manager, initializing listeners and caching necessary views.
     *
     * @param activity The hosting {@code Activity} instance.
     * @param listener The callback listener for the translate button.
     * @param mapListener The callback listener for the map button.
     */
    public CellBroadcastAlertButtonManager(
            Activity activity, OnTranslateButtonClickListener listener,
            OnMapButtonClickListener mapListener) {
        mActivity = activity;
        mTranslateListener = listener;
        mMapListener = mapListener;
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
     * Configures the button layout, dynamically adding and ordering the Translate, Map and Dismiss
     * buttons based on feature enablement.
     *
     * @param showTranslate True to show the translate button.
     * @param showMap       True to show the map button.
     */
    public void configureButtons(boolean showTranslate, boolean showMap) {
        if (mButtonBar == null || mDismissButton == null) {
            return;
        }

        if (showTranslate) {
            ensureTranslateButtonCreated();
            showTranslationInProgress(false);
        }
        if (mTranslateContainer != null) {
            mTranslateContainer.setVisibility(showTranslate ? View.VISIBLE : View.GONE);
        }

        if (showMap) {
            ensureMapButtonCreated();
        }
        if (mMapContainer != null) {
            mMapContainer.setVisibility(showMap ? View.VISIBLE : View.GONE);
        }

        mButtonBar.removeAllViews();
        if (showTranslate) mButtonBar.addView(mTranslateContainer);
        if (showMap) mButtonBar.addView(mMapContainer);
        mButtonBar.addView(mDismissButton);

        int visibleButtonCount = 1 + (showTranslate ? 1 : 0) + (showMap ? 1 : 0);
        Log.d(TAG, "configureButtons: visibleButtonCount=" + visibleButtonCount + ", showTranslate="
                + showTranslate + ", showMap=" + showMap);

        if (visibleButtonCount > 1) {
            mButtonBar.setGravity(Gravity.CENTER_VERTICAL);
            float weight = 1.0f;

            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, weight);

            if (showMap) mMapContainer.setLayoutParams(buttonParams);
            if (showTranslate) mTranslateContainer.setLayoutParams(buttonParams);

            mDismissButton.setLayoutParams(buttonParams);
            mDismissButton.setPadding(0, mDismissButton.getPaddingTop(), 0,
                    mDismissButton.getPaddingBottom());
        } else {
            mButtonBar.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams dismissParams =
                    (LinearLayout.LayoutParams) mDismissButton.getLayoutParams();
            dismissParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            dismissParams.weight = 0f;
            mDismissButton.setLayoutParams(dismissParams);
            mDismissButton.setPaddingRelative(mDismissOriginalPaddingStart,
                    mDismissButton.getPaddingTop(), mDismissOriginalPaddingEnd,
                    mDismissButton.getPaddingBottom());
        }
    }

    /** Creates the Translate button, progress bar, and their container if they do not exist. */
    private void ensureTranslateButtonCreated() {
        if (mTranslateContainer == null) {
            mTranslateContainer = new FrameLayout(mActivity);
            mTranslateButton = createTranslateButton();
            mProgressBar = createProgressBar();
            mTranslateContainer.addView(mTranslateButton);
            mTranslateContainer.addView(mProgressBar);
        }
    }

    /** Creates the Map button and its container if they do not exist. */
    private void ensureMapButtonCreated() {
        if (mMapContainer == null) {
            mMapContainer = new FrameLayout(mActivity);
            mMapButton = createMapButton();
            mMapContainer.addView(mMapButton);
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
     * Called when translation is completed to potentially hide the translate button.
     */
    public void onTranslationCompleted() {
        boolean showMap = mMapContainer != null && mMapContainer.getVisibility() == View.VISIBLE;
        configureButtons(false, showMap);
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
                    if (mTranslateListener != null) {
                        mTranslateListener.onTranslateClick();
                    }
                });
        return button;
    }

    /** Creates a new "Map" button programmatically with the correct style. */
    private Button createMapButton() {
        Button button = new Button(mActivity, null, android.R.attr.buttonBarButtonStyle);
        button.setText(R.string.button_map);
        button.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        button.setOnClickListener(v -> {
            if (mMapListener != null) {
                mMapListener.onMapClick();
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
