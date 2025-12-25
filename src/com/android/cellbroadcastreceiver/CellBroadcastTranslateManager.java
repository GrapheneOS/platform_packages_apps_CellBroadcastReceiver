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

import android.app.PendingIntent;
import android.content.Context;
import android.icu.util.ULocale;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.autofill.AutofillId;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextLanguage;
import android.view.translation.TranslationCapability;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationManager;
import android.view.translation.TranslationRequest;
import android.view.translation.TranslationRequestValue;
import android.view.translation.TranslationResponseValue;
import android.view.translation.TranslationSpec;
import android.view.translation.Translator;
import android.view.translation.ViewTranslationRequest;
import android.view.translation.ViewTranslationResponse;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CellBroadcastTranslateManager {
    private static final String TAG = "CellBroadcastTranslateManager";
    private final TranslationManagerWrapper mTranslationManagerWrapper;
    private final TextClassifierWrapper mTextClassifierWrapper;
    private Translator mTranslator;
    private final Executor mMainExecutor;
    private final Executor mExecutor;
    private final TranslateManagerCallback mCallback;
    // TODO: Confirm and adjust the appropriate value for the text classifier's
    //  language detection confidence threshold.
    private static final float MIN_LANGUAGE_DETECTION_CONFIDENCE_THRESHOLD = 0.85f;
    private final AtomicBoolean mIsCapabilityCheckInProgress = new AtomicBoolean(false);
    private final AtomicBoolean mIsResolvingTargetLanguage = new AtomicBoolean(false);
    // Timeout duration for translation checks (to prevent UI hanging)
    private static final long TRANSLATION_CHECK_TIMEOUT_MS = 400;
    // Handler for timeout callbacks
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    /**
     * A callback interface for {@link CellBroadcastTranslateManager} to communicate results back to
     * the UI thread, such as within an Activity or Dialog.
     */
    public interface TranslateManagerCallback {
        /**
         * Called when language detection is complete.
         *
         * @param detectedLocale The detected source language. Empty if detection fails
         *                       or confidence is too low.
         */
        void onLanguageDetectionCompleted(Optional<ULocale> detectedLocale);

        /**
         * Called when the on-device translator creation process is complete. This is invoked on the
         * main thread.
         *
         * @param success {@code true} if the translator was created successfully and is ready to
         *                use;
         *                {@code false} otherwise (e.g., if language packs are missing).
         */
        void onTranslatorCreated(boolean success);

        /**
         * Called when the translation process is complete. This is invoked on the main thread.
         *
         * @param translatedText The translated text if the operation was successful;
         *                       {@code null} otherwise.
         * @param success        {@code true} if the translation was successful;
         *                       {@code false} otherwise.
         */
        void onTranslationCompleted(CharSequence translatedText, boolean success);
    }

    /**
     * An interface that wraps the static {@link TranslationManager} to facilitate testing.
     * This allows for injecting a mock implementation during unit tests to simulate the behavior
     * of the actual TranslationManager service.
     */
    public interface TranslationManagerWrapper {
        /**
         * Checks if the underlying {@link TranslationManager} service is available on the device.
         *
         * @return {@code true} if the service is available, {@code false} otherwise.
         */
        boolean isAvailable();

        /**
         * A wrapper method for {@link TranslationManager#createOnDeviceTranslator(
         *TranslationContext, Executor, Consumer)}.
         * Asynchronously creates an on-device translator.
         *
         * @param translationContext The context defining the translation session, including source
         *                           and target specs.
         * @param executor           The executor on which to dispatch callback events.
         * @param callback           A consumer that will receive the created {@link Translator} or
         *                           {@code null} if creation fails.
         */
        void createOnDeviceTranslator(TranslationContext translationContext, Executor executor,
                Consumer<Translator> callback);

        /**
         * A wrapper method for
         * {@link TranslationManager#getOnDeviceTranslationSettingsActivityIntent()}.
         * Returns a {@link PendingIntent} to launch the system's translation settings screen.
         *
         * @return A {@link PendingIntent} for the settings activity, or {@code null} if
         * unavailable.
         */
        PendingIntent getOnDeviceTranslationSettingsActivityIntent();

        /**
         * A wrapper method for
         * {@link TranslationManager#getOnDeviceTranslationCapabilities(int, int)}.
         * Returns a set of {@link TranslationCapability} describing the supported translation
         * capabilities.
         *
         * @param sourceFormat The data format for the source data.
         * @param targetFormat The data format for the target data.
         * @return A set of supported capabilities.
         */
        @RequiresApi(Build.VERSION_CODES.S)
        Set<TranslationCapability> getOnDeviceTranslationCapabilities(int sourceFormat,
                int targetFormat);
    }

    // Default implementation using real TranslationManager
    private static class DefaultTranslationManagerWrapper implements TranslationManagerWrapper {
        private final TranslationManager mManager;

        DefaultTranslationManagerWrapper(Context context) {
            mManager = context.getSystemService(TranslationManager.class);
        }

        @Override
        public boolean isAvailable() {
            return mManager != null;
        }

        @Override
        public void createOnDeviceTranslator(TranslationContext translationContext,
                Executor executor, Consumer<Translator> callback) {
            if (mManager != null) {
                mManager.createOnDeviceTranslator(translationContext, executor, callback);
            } else {
                Log.e(TAG, "DefaultTranslationManagerWrapper: TranslationManager is null");
                if (callback != null) {
                    executor.execute(() -> callback.accept(null));
                }
            }
        }

        @Override
        public PendingIntent getOnDeviceTranslationSettingsActivityIntent() {
            return (mManager != null) ? mManager.getOnDeviceTranslationSettingsActivityIntent()
                    : null;
        }

        @Override
        @RequiresApi(Build.VERSION_CODES.S)
        public Set<TranslationCapability> getOnDeviceTranslationCapabilities(
                int sourceFormat, int targetFormat) {
            if (mManager != null && SdkLevel.isAtLeastS()) {
                return mManager.getOnDeviceTranslationCapabilities(sourceFormat, targetFormat);
            }
            return Collections.emptySet();
        }
    }

    /**
     * An interface that wraps the {@link TextClassifier} to facilitate testing.
     * <p>
     * This allows for injecting a mock implementation during unit tests to simulate the behavior
     * of the actual {@code TextClassifier}, which is a final class and cannot be directly mocked.
     */
    @VisibleForTesting
    public interface TextClassifierWrapper {
        /**
         * Detects the language of the given text.
         *
         * @param text The input string to detect the language from.
         * @return An {@link Optional} containing the detected {@link ULocale} if the detection
         * was successful and the confidence score is high enough. Otherwise, returns an
         * empty {@code Optional}.
         */
        Optional<ULocale> detectLanguage(String text);
    }

    private static class DefaultTextClassifierWrapper implements TextClassifierWrapper {
        private final TextClassifier mTextClassifier;

        DefaultTextClassifierWrapper(Context context) {
            TextClassificationManager tcm =
                    context.getSystemService(TextClassificationManager.class);
            mTextClassifier = (tcm != null) ? tcm.getTextClassifier() : null;
        }

        @Override
        public Optional<ULocale> detectLanguage(String text) {
            if (mTextClassifier == null) {
                Log.e(TAG, "TextClassifier is not available.");
                return Optional.empty();
            }
            try {
                TextLanguage.Request request = new TextLanguage.Request.Builder(text).build();
                TextLanguage result = mTextClassifier.detectLanguage(request);

                if (result != null && result.getLocale(0) != null
                        && result.getConfidenceScore(result.getLocale(0))
                        > MIN_LANGUAGE_DETECTION_CONFIDENCE_THRESHOLD) {
                    Log.d(TAG, "detectLanguage: getConfidenceScore=" + result.getConfidenceScore(
                            result.getLocale(0)));
                    return Optional.of(result.getLocale(0));
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during language detection.", e);
            }
            return Optional.empty();
        }
    }

    /**
     * Constructs a new CellBroadcastTranslateManager.
     *
     * @param context      The application or activity context.
     * @param mainExecutor The main thread executor for posting results back to the UI thread.
     * @param callback     The callback to report results to. This is typically the calling Activity
     *                     or Dialog that implements {@link TranslateManagerCallback}.
     */
    public CellBroadcastTranslateManager(Context context, Executor mainExecutor,
            TranslateManagerCallback callback) {
        this(mainExecutor, callback, new DefaultTranslationManagerWrapper(context),
                new DefaultTextClassifierWrapper(context),
                Executors.newSingleThreadExecutor());
    }

    // Constructor for testing
    public CellBroadcastTranslateManager(Executor mainExecutor,
            TranslateManagerCallback callback, TranslationManagerWrapper wrapper,
            TextClassifierWrapper textClassifierWrapper,
            Executor backgroundExecutor) {
        mCallback = callback;
        mMainExecutor = mainExecutor;
        mTranslationManagerWrapper = wrapper;
        mTextClassifierWrapper = textClassifierWrapper;
        mExecutor = backgroundExecutor;
        if (!mTranslationManagerWrapper.isAvailable()) {
            Log.e(TAG, "CellBroadcastTranslateManager: TranslationManagerWrapper is not available");
        }
    }

    /**
     * Checks if the system's {@link TranslationManager} service is available on the device.
     * The translation feature should be hidden if this returns {@code false}.
     *
     * @return {@code true} if the TranslationManager service is available, {@code false} otherwise.
     */
    public boolean isTranslationManagerAvailable() {
        return mTranslationManagerWrapper != null && mTranslationManagerWrapper.isAvailable();
    }

    /**
     * Asynchronously detects the language of the given text.
     * The result is delivered via the onLanguageDetectionCompleted callback.
     *
     * @param text The text for language detection.
     */
    public void detectLanguage(String text) {
        mExecutor.execute(() -> {
            try {
                Optional<ULocale> detectedLocale = mTextClassifierWrapper.detectLanguage(text);

                if (detectedLocale.isPresent()) {
                    Log.d(TAG, "Language detected: " + detectedLocale.get().toLanguageTag());
                } else {
                    Log.w(TAG, "Language detection failed.");
                }
                mMainExecutor.execute(() -> mCallback.onLanguageDetectionCompleted(detectedLocale));

            } catch (Exception e) {
                Log.e(TAG, "Exception during detectLanguage execution", e);
                mMainExecutor.execute(
                        () -> mCallback.onLanguageDetectionCompleted(Optional.empty()));
            }
        });
    }

    /**
     * Asynchronously initializes an on-device {@link Translator} for the given language pair.
     * The result of this operation is delivered via the
     * {@link TranslateManagerCallback#onTranslatorCreated(boolean)} callback.
     *
     * @param sourceLocale The source language to translate from.
     * @param targetLocale The target language to translate to.
     */
    public void initializeTranslator(ULocale sourceLocale, ULocale targetLocale) {
        Log.d(TAG, "initializeTranslator: sourceLocale=" + sourceLocale + " , targetLocale="
                + targetLocale);
        if (!isTranslationManagerAvailable()) {
            Log.e(TAG, "initializeTranslator: TranslationManager not available");
            mMainExecutor.execute(() -> mCallback.onTranslatorCreated(false));
            return;
        }

        mExecutor.execute(() -> {
            try {
                final TranslationSpec sourceSpec = new TranslationSpec(sourceLocale,
                        TranslationSpec.DATA_FORMAT_TEXT);
                final TranslationSpec targetSpec = new TranslationSpec(targetLocale,
                        TranslationSpec.DATA_FORMAT_TEXT);
                final TranslationContext translationContext = new TranslationContext.Builder(
                        sourceSpec, targetSpec).build();

                mTranslationManagerWrapper.createOnDeviceTranslator(
                        translationContext,
                        mMainExecutor,
                        translator -> {
                            mTranslator = translator;
                            boolean success = translator != null;
                            if (!success) {
                                Log.e(TAG,
                                        "initializeTranslator: createOnDeviceTranslator returned "
                                                + "null");
                            }
                            mCallback.onTranslatorCreated(success);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Exception during initializeTranslator", e);
                mMainExecutor.execute(() -> mCallback.onTranslatorCreated(false));
            }
        });
    }

    /**
     * Checks if the {@link Translator} has been successfully initialized and is ready for use.
     *
     * @return {@code true} if the translator is ready, {@code false} otherwise.
     */
    @VisibleForTesting
    public boolean isTranslatorReady() {
        return mTranslator != null;
    }

    /**
     * Asynchronously starts the translation process for the given text. This should only be called
     * after {@link #isTranslatorReady()} returns {@code true}. The result is delivered via the
     * {@link TranslateManagerCallback#onTranslationCompleted(CharSequence, boolean)} callback.
     *
     * @param textToTranslate The original text to be translated.
     * @param autofillId      The {@link AutofillId} of the view containing the text, required for
     *                        the translation request.
     */
    public void startOnDeviceTranslation(String textToTranslate, AutofillId autofillId) {
        Log.d(TAG, "startOnDeviceTranslation: textToTranslate:" + textToTranslate);
        if (!isTranslatorReady()) {
            Log.e(TAG, "startOnDeviceTranslation: Translator not ready.");
            mMainExecutor.execute(
                    () -> mCallback.onTranslationCompleted(null, false));
            return;
        }

        if (autofillId == null) {
            Log.e(TAG, "startOnDeviceTranslation: AutofillId is null, cannot start translation.");
            mMainExecutor.execute(
                    () -> mCallback.onTranslationCompleted(null, false));
            return;
        }

        mExecutor.execute(() -> {
            try {
                ViewTranslationRequest viewRequest = new ViewTranslationRequest.Builder(autofillId)
                        .setValue(ViewTranslationRequest.ID_TEXT,
                                TranslationRequestValue.forText(textToTranslate))
                        .build();
                TranslationRequest request = new TranslationRequest.Builder()
                        .setViewTranslationRequests(Collections.singletonList(viewRequest))
                        .build();

                mTranslator.translate(request, null, mMainExecutor,
                        response -> {
                            try {
                                if (response == null) {
                                    Log.e(TAG, "Translation failed: Response is null.");
                                    mCallback.onTranslationCompleted(null, false);
                                    return;
                                }
                                final SparseArray<ViewTranslationResponse> viewResponses =
                                        response.getViewTranslationResponses();
                                if (viewResponses != null && viewResponses.size() > 0) {
                                    final ViewTranslationResponse viewResponse =
                                            viewResponses.valueAt(0);
                                    if (viewResponse != null) {
                                        final TranslationResponseValue result =
                                                viewResponse.getValue(
                                                        ViewTranslationRequest.ID_TEXT);
                                        if (result != null && result.getStatusCode()
                                                == TranslationResponseValue.STATUS_SUCCESS) {
                                            Log.d(TAG, "Translation successful. "
                                                    + result.getText());
                                            mCallback.onTranslationCompleted(result.getText(),
                                                    true);
                                        } else {
                                            int statusCode =
                                                    (result != null) ? result.getStatusCode() : -1;
                                            Log.e(TAG, "Translation failed. Status: " + statusCode);
                                            mCallback.onTranslationCompleted(null, false);
                                        }
                                    } else {
                                        Log.e(TAG,
                                                "Translation failed: ViewTranslationResponse is "
                                                        + "null.");
                                        mCallback.onTranslationCompleted(null, false);
                                    }
                                } else {
                                    Log.e(TAG, "Translation failed: No response values.");
                                    mCallback.onTranslationCompleted(null, false);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Exception in translation response consumer", e);
                                mCallback.onTranslationCompleted(null, false);
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Exception during startOnDeviceTranslation", e);
                mMainExecutor.execute(
                        () -> mCallback.onTranslationCompleted(null, false));
            }
        });
    }

    /**
     * Destroys the current translator instance. This should be called when the translator
     * is no longer needed or needs to be reset, for example when a new message arrives.
     */
    public void destroyTranslator() {
        if (mTranslator != null) {
            Log.d(TAG, "Destroying translator.");
            mTranslator.destroy();
            mTranslator = null;
        }
    }

    /**
     * Returns a {@link PendingIntent} that can be used to launch the system's on-device
     * translation settings screen. This is typically used to guide the user to download
     * missing language packs.
     *
     * @return A {@link PendingIntent} to the settings activity, or {@code null} if the intent
     * is not available.
     */
    public PendingIntent getSettingsIntent() {
        if (!isTranslationManagerAvailable()) return null;
        try {
            return mTranslationManagerWrapper.getOnDeviceTranslationSettingsActivityIntent();
        } catch (Exception e) {
            Log.e(TAG, "Exception in getSettingsIntent", e);
            return null;
        }
    }

    /**
     * Checks if the system's TranslationManager service is available AND supports on-device
     * translation asynchronously with a timeout.
     *
     * @param callback The callback to receive the result (true if supported) on the main thread.
     */
    public void checkOnDeviceTranslationCapability(Consumer<Boolean> callback) {
        if (!isTranslationManagerAvailable()) {
            mMainExecutor.execute(() -> callback.accept(false));
            return;
        }

        if (mIsCapabilityCheckInProgress.getAndSet(true)) {
            Log.d(TAG, "checkOnDeviceTranslationCapability: Already in progress, ignoring it");
            return;
        }

        AtomicBoolean hasResponded = new AtomicBoolean(false);
        final AtomicReference<Future<?>> futureRef = new AtomicReference<>();

        Runnable timeoutRunnable = () -> {
            if (hasResponded.compareAndSet(false, true)) {
                Log.w(TAG, "checkOnDeviceTranslationCapability: Timed out.");
                mIsCapabilityCheckInProgress.set(false); // Release lock
                callback.accept(false); // Default to unsupported on timeout

                Future<?> future = futureRef.get();
                if (future != null) {
                    future.cancel(true);
                }
            }
        };

        mMainHandler.postDelayed(timeoutRunnable, TRANSLATION_CHECK_TIMEOUT_MS);

        Runnable task = () -> {
            boolean isSupported = false;
            try {
                if (SdkLevel.isAtLeastS()) {
                    try {
                        if (Thread.currentThread().isInterrupted()) return;

                        Set<TranslationCapability> capabilities =
                                mTranslationManagerWrapper.getOnDeviceTranslationCapabilities(
                                        TranslationSpec.DATA_FORMAT_TEXT,
                                        TranslationSpec.DATA_FORMAT_TEXT);
                        if (capabilities != null && !capabilities.isEmpty()) {
                            isSupported = true;
                        } else {
                            Log.w(TAG, "checkOnDeviceTranslationCapability: empty.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking translation capabilities", e);
                    }
                }
            } finally {
                final boolean finalResult = isSupported;
                mMainExecutor.execute(() -> {
                    if (hasResponded.compareAndSet(false, true)) {
                        mMainHandler.removeCallbacks(timeoutRunnable);
                        mIsCapabilityCheckInProgress.set(false);
                        callback.accept(finalResult);
                    }
                });
            }
        };

        if (mExecutor instanceof ExecutorService) {
            futureRef.set(((ExecutorService) mExecutor).submit(task));
        } else {
            mExecutor.execute(task);
        }
    }

    /**
     * Checks if the system locale is Traditional Chinese (Taiwan, Hong Kong, or Hant script).
     */
    private boolean isTraditionalChinese(Locale locale) {
        if (locale == null) {
            return false;
        }
        if ("zh".equalsIgnoreCase(locale.getLanguage())) {
            return "Hant".equalsIgnoreCase(locale.getScript())
                    || "TW".equalsIgnoreCase(locale.getCountry())
                    || "HK".equalsIgnoreCase(locale.getCountry());
        }
        return false;
    }

    /**
     * Resolves the target ULocale for translation based on the system locale asynchronously
     * with a timeout.
     *
     * @param systemLocale The device's system locale.
     * @param callback     The callback to receive the resolved target ULocale on the main thread.
     */
    public void resolveTargetLanguage(Locale systemLocale, Consumer<ULocale> callback) {
        if (systemLocale == null) {
            mMainExecutor.execute(() -> callback.accept(ULocale.getDefault()));
            return;
        }

        if (mIsResolvingTargetLanguage.getAndSet(true)) {
            Log.d(TAG, "resolveTargetLanguage: Already in progress, ignoring it.");
            return;
        }

        AtomicBoolean hasResponded = new AtomicBoolean(false);
        ULocale defaultFallback = new ULocale(systemLocale.getLanguage());
        final AtomicReference<Future<?>> futureRef = new AtomicReference<>();

        Runnable timeoutRunnable = () -> {
            if (hasResponded.compareAndSet(false, true)) {
                Log.w(TAG, "resolveTargetLanguage: Timed out.");
                mIsResolvingTargetLanguage.set(false);
                callback.accept(defaultFallback);
                Future<?> future = futureRef.get();
                if (future != null) {
                    future.cancel(true);
                }
            }
        };

        mMainHandler.postDelayed(timeoutRunnable, TRANSLATION_CHECK_TIMEOUT_MS);

        Runnable task = () -> {
            ULocale targetLocale = defaultFallback;
            try {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                boolean isTraditional = isTraditionalChinese(systemLocale);
                if (isTraditional) {
                    if (SdkLevel.isAtLeastS() && isTranslationManagerAvailable()
                            && getSettingsIntent() != null) {
                        try {
                            Set<TranslationCapability> capabilities =
                                    mTranslationManagerWrapper.getOnDeviceTranslationCapabilities(
                                            TranslationSpec.DATA_FORMAT_TEXT,
                                            TranslationSpec.DATA_FORMAT_TEXT);
                            if (capabilities != null) {
                                for (TranslationCapability capability : capabilities) {
                                    if (capability == null) continue;
                                    TranslationSpec targetSpec = capability.getTargetSpec();
                                    if (targetSpec == null) continue;
                                    ULocale locale = targetSpec.getLocale();
                                    if (locale != null && isTraditionalChinese(locale.toLocale())) {
                                        targetLocale = locale;
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Exception in getOnDeviceTranslationCapabilities", e);
                        }
                    }
                }
            } finally {
                final ULocale finalTargetLocale = targetLocale;
                mMainExecutor.execute(() -> {
                    if (hasResponded.compareAndSet(false, true)) {
                        mMainHandler.removeCallbacks(timeoutRunnable);
                        mIsResolvingTargetLanguage.set(false);
                        callback.accept(finalTargetLocale);
                    }
                });
            }
        };

        if (mExecutor instanceof ExecutorService) {
            futureRef.set(((ExecutorService) mExecutor).submit(task));
        } else {
            mExecutor.execute(task);
        }
    }
}
