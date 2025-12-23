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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.icu.util.ULocale;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.SparseArray;
import android.view.autofill.AutofillId;
import android.view.translation.TranslationCapability;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationResponseValue;
import android.view.translation.TranslationSpec;
import android.view.translation.Translator;
import android.view.translation.ViewTranslationRequest;
import android.view.translation.ViewTranslationResponse;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.android.cellbroadcastreceiver.CellBroadcastTranslateManager;
import com.android.cellbroadcastreceiver.CellBroadcastTranslateManager.TextClassifierWrapper;
import com.android.cellbroadcastreceiver.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class CellBroadcastTranslateManagerTest {

    @Mock
    private CellBroadcastTranslateManager.TranslateManagerCallback mMockCallback;
    @Mock
    private CellBroadcastTranslateManager.TranslationManagerWrapper mMockWrapper;
    @Mock
    private Translator mMockTranslator;
    @Mock
    private TextClassifierWrapper mMockTextClassifierWrapper;

    private Context mContext;
    private CellBroadcastTranslateManager mTranslateManager;
    private final Executor mDirectExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        mTranslateManager = new CellBroadcastTranslateManager(mDirectExecutor,
                mMockCallback, mMockWrapper, mMockTextClassifierWrapper, mDirectExecutor);
    }

    /**
     * Tests that {@link CellBroadcastTranslateManager#isTranslationManagerAvailable()} returns
     * {@code true} when the underlying wrapper reports that the service is available.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testIsTranslationManagerAvailableTrue() {
        doReturn(true).when(mMockWrapper).isAvailable();
        assertTrue(mTranslateManager.isTranslationManagerAvailable());
    }

    /**
     * Tests that {@link CellBroadcastTranslateManager#isTranslationManagerAvailable()} returns
     * {@code false} when the underlying wrapper reports that the service is not available.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testIsTranslationManagerAvailableFalse() {
        doReturn(false).when(mMockWrapper).isAvailable();
        assertFalse(mTranslateManager.isTranslationManagerAvailable());
    }

    /**
     * Tests the successful initialization of the translator. Verifies that
     * {@link CellBroadcastTranslateManager.TranslateManagerCallback#onTranslatorCreated(boolean)}
     * is called with {@code true} and that the translator is marked as ready.
     */
    @SuppressWarnings("unchecked")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testInitializeTranslatorSuccess() throws Exception {
        doReturn(true).when(mMockWrapper).isAvailable();
        doAnswer(invocation -> {
            Executor executor = invocation.getArgument(1);
            Consumer<Translator> callback = invocation.getArgument(2);
            executor.execute(() -> callback.accept(mMockTranslator));
            return null;
        }).when(mMockWrapper).createOnDeviceTranslator(any(TranslationContext.class),
                any(Executor.class), any(Consumer.class));

        mTranslateManager.initializeTranslator(ULocale.KOREAN, ULocale.ENGLISH);

        verify(mMockWrapper).createOnDeviceTranslator(any(TranslationContext.class),
                eq(mDirectExecutor), any(Consumer.class));
        verify(mMockCallback).onTranslatorCreated(eq(true));
        assertTrue(mTranslateManager.isTranslatorReady());
    }

    /**
     * Tests the failed initialization of the translator where the wrapper returns a null
     * translator.
     * Verifies that
     * {@link CellBroadcastTranslateManager.TranslateManagerCallback#onTranslatorCreated(boolean)}
     * is called with {@code false} and that the translator is not marked as ready.
     */
    @SuppressWarnings("unchecked")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testInitializeTranslatorFailure() throws Exception {
        doReturn(true).when(mMockWrapper).isAvailable();
        doAnswer(invocation -> {
            Executor executor = invocation.getArgument(1);
            Consumer<Translator> callback = invocation.getArgument(2);
            executor.execute(() -> callback.accept(null));
            return null;
        }).when(mMockWrapper).createOnDeviceTranslator(any(TranslationContext.class),
                any(Executor.class), any(Consumer.class));

        mTranslateManager.initializeTranslator(ULocale.KOREAN, ULocale.ENGLISH);
        verify(mMockCallback).onTranslatorCreated(eq(false));
        assertFalse(mTranslateManager.isTranslatorReady());
    }

    /**
     * Tests that an exception during translator creation is handled gracefully. Verifies that
     * {@link CellBroadcastTranslateManager.TranslateManagerCallback#onTranslatorCreated(boolean)}
     * is called with {@code false}.
     */
    @SuppressWarnings("unchecked")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testInitializeTranslatorWrapperException() throws Exception {
        doReturn(true).when(mMockWrapper).isAvailable();
        doThrow(new RuntimeException("Wrapper Exception")).when(mMockWrapper)
                .createOnDeviceTranslator(any(TranslationContext.class), any(Executor.class),
                        any(Consumer.class));

        mTranslateManager.initializeTranslator(ULocale.KOREAN, ULocale.ENGLISH);
        verify(mMockCallback).onTranslatorCreated(eq(false));
        assertFalse(mTranslateManager.isTranslatorReady());
    }

    /**
     * Tests that {@link CellBroadcastTranslateManager#getSettingsIntent()} successfully returns
     * the {@link PendingIntent} provided by the wrapper.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testGetSettingsIntentSuccess() throws Exception {
        doReturn(true).when(mMockWrapper).isAvailable();
        Intent intent = new Intent("TEST_ACTION");
        PendingIntent realPendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        doReturn(realPendingIntent).when(
                mMockWrapper).getOnDeviceTranslationSettingsActivityIntent();

        PendingIntent result = mTranslateManager.getSettingsIntent();
        assertEquals(realPendingIntent, result);
    }

    /**
     * Tests that {@link CellBroadcastTranslateManager#getSettingsIntent()} returns {@code null}
     * when the wrapper throws an exception.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testGetSettingsIntentException() throws Exception {
        doReturn(true).when(mMockWrapper).isAvailable();
        doThrow(new RuntimeException("Settings Exception")).when(
                mMockWrapper).getOnDeviceTranslationSettingsActivityIntent();
        assertNull(mTranslateManager.getSettingsIntent());
    }

    /**
     * Tests that {@link CellBroadcastTranslateManager#getSettingsIntent()} returns {@code null}
     * when the translation service is not available.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testGetSettingsIntentNotAvailable() throws Exception {
        doReturn(false).when(mMockWrapper).isAvailable();
        assertNull(mTranslateManager.getSettingsIntent());
    }

    /**
     * Tests that calling {@link CellBroadcastTranslateManager#startOnDeviceTranslation(String,
     * android.view.autofill.AutofillId)} when the translator is not ready results in an immediate
     * failure callback.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testStartOnDeviceTranslationNotReady() {
        doReturn(true).when(mMockWrapper).isAvailable();
        mTranslateManager.startOnDeviceTranslation("Test text", null);
        verify(mMockCallback).onTranslationCompleted(eq(null), eq(false));
    }

    /**
     * Tests that calling {@link CellBroadcastTranslateManager#startOnDeviceTranslation(String,
     * android.view.autofill.AutofillId)} with a null AutofillId results in an immediate
     * failure callback, even if the translator is ready.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testStartOnDeviceTranslationNullAutofillId() {
        doReturn(true).when(mMockWrapper).isAvailable();
        doAnswer(invocation -> {
            Executor executor = invocation.getArgument(1);
            Consumer<Translator> callback = invocation.getArgument(2);
            executor.execute(() -> callback.accept(mMockTranslator));
            return null;
        }).when(mMockWrapper).createOnDeviceTranslator(any(TranslationContext.class),
                any(Executor.class), any());
        mTranslateManager.initializeTranslator(ULocale.KOREAN, ULocale.ENGLISH);
        assertTrue(mTranslateManager.isTranslatorReady());

        mTranslateManager.startOnDeviceTranslation("Test text", null);
        verify(mMockCallback).onTranslationCompleted(eq(null), eq(false));
    }

    /**
     * Tests the successful language detection flow. Verifies that
     * {@link CellBroadcastTranslateManager.TranslateManagerCallback#onLanguageDetectionCompleted(
     *Optional)} is called with the correctly detected {@link ULocale}.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testDetectLanguageDetectionSuccess() {
        Optional<ULocale> detectedLocale = Optional.of(ULocale.forLanguageTag("es"));
        doReturn(detectedLocale).when(mMockTextClassifierWrapper).detectLanguage("Hola");

        mTranslateManager.detectLanguage("Hola");

        ArgumentCaptor<Optional<ULocale>> captor = ArgumentCaptor.forClass(Optional.class);
        verify(mMockCallback).onLanguageDetectionCompleted(captor.capture());

        Optional<ULocale> capturedOptional = captor.getValue();
        assertTrue("Optional should be present on success", capturedOptional.isPresent());
        assertEquals("Detected language should be Spanish", "es",
                capturedOptional.get().getLanguage());
    }

    /**
     * Tests the failed language detection flow. Verifies that
     * {@link CellBroadcastTranslateManager.TranslateManagerCallback#onLanguageDetectionCompleted(
     *Optional)} is called with an empty {@link Optional}.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testDetectLanguageDetectionFailed() {
        doReturn(Optional.empty()).when(mMockTextClassifierWrapper).detectLanguage(
                any(String.class));

        mTranslateManager.detectLanguage("...?");

        ArgumentCaptor<Optional<ULocale>> captor = ArgumentCaptor.forClass(Optional.class);
        verify(mMockCallback).onLanguageDetectionCompleted(captor.capture());

        assertFalse("Optional should be empty on failure", captor.getValue().isPresent());
    }

    /**
     * Tests that calling {@link CellBroadcastTranslateManager#destroyTranslator()} successfully
     * destroys the translator instance and nullifies the internal reference.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testDestroyTranslatorDestroysAndNullifiesTranslator() throws Exception {
        doReturn(true).when(mMockWrapper).isAvailable();
        doAnswer(invocation -> {
            Executor executor = invocation.getArgument(1);
            Consumer<Translator> callback = invocation.getArgument(2);
            executor.execute(() -> callback.accept(mMockTranslator));
            return null;
        }).when(mMockWrapper).createOnDeviceTranslator(any(TranslationContext.class),
                any(Executor.class), any(Consumer.class));

        mTranslateManager.initializeTranslator(ULocale.KOREAN, ULocale.ENGLISH);

        // Verify that the translator is ready.
        assertTrue("Precondition failed: Translator should be ready.",
                mTranslateManager.isTranslatorReady());
        verify(mMockCallback).onTranslatorCreated(eq(true));

        mTranslateManager.destroyTranslator();

        // Verify that the translator's destroy() method was called
        // and the translator instance within the manager is now null.
        verify(mMockTranslator, times(1)).destroy();
        assertFalse("Translator should be null after being destroyed.",
                mTranslateManager.isTranslatorReady());
    }

    /**
     * Verifies that onTranslatorCreated(false) is called when the TranslationManager
     * is unavailable during initialization.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testInitializeTranslatorWhenManagerUnavailable() {
        doReturn(false).when(mMockWrapper).isAvailable();

        mTranslateManager = new CellBroadcastTranslateManager(mDirectExecutor,
                mMockCallback, mMockWrapper, mMockTextClassifierWrapper, mDirectExecutor);

        mTranslateManager.initializeTranslator(ULocale.KOREAN, ULocale.ENGLISH);

        verify(mMockCallback).onTranslatorCreated(eq(false));
        verify(mMockWrapper, never()).createOnDeviceTranslator(any(), any(), any());
    }

    /**
     * Tests language detection behavior with a low-confidence score.
     * Verifies that it's treated as a detection failure.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testDetectLanguageLowConfidence() {
        doReturn(Optional.empty()).when(
                mMockTextClassifierWrapper).detectLanguage(anyString());

        mTranslateManager.detectLanguage("Hola");

        ArgumentCaptor<Optional<ULocale>> captor = ArgumentCaptor.forClass(Optional.class);
        verify(mMockCallback).onLanguageDetectionCompleted(captor.capture());

        assertFalse("Optional should be empty for low-confidence results",
                captor.getValue().isPresent());
    }

    /**
     * Tests language detection behavior when an exception occurs.
     * Verifies that the failure is handled gracefully.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testDetectLanguageExceptionHandling() {
        doThrow(new RuntimeException("Detection Exception")).when(
                mMockTextClassifierWrapper).detectLanguage(anyString());

        mTranslateManager.detectLanguage("Exception test");

        ArgumentCaptor<Optional<ULocale>> captor = ArgumentCaptor.forClass(Optional.class);
        verify(mMockCallback).onLanguageDetectionCompleted(captor.capture());
        assertFalse("Optional should be empty when an exception occurs",
                captor.getValue().isPresent());
    }


    /**
     * Tests the translation flow when the service returns a null response.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testStartOnDeviceTranslationNullResponse() {
        doReturn(true).when(mMockWrapper).isAvailable();
        doAnswer(invocation -> {
            Consumer<Translator> callback = invocation.getArgument(2);
            callback.accept(mMockTranslator);
            return null;
        }).when(mMockWrapper).createOnDeviceTranslator(any(), any(), any());
        mTranslateManager.initializeTranslator(ULocale.KOREAN, ULocale.ENGLISH);
        assertTrue(mTranslateManager.isTranslatorReady());

        doAnswer(invocation -> {
            Consumer<TranslationResponse> consumer = invocation.getArgument(3);
            consumer.accept(null);
            return null;
        }).when(mMockTranslator).translate(any(), any(), any(), any());

        AutofillId realAutofillId = new TextView(mContext).getAutofillId();

        mTranslateManager.startOnDeviceTranslation("Test text", realAutofillId);

        verify(mMockCallback).onTranslationCompleted(eq(null), eq(false));
    }

    /**
     * Tests the translation flow when the service returns a response with a failure status code.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testStartOnDeviceTranslationFailureStatusCode() {
        doReturn(true).when(mMockWrapper).isAvailable();
        doAnswer(invocation -> {
            Consumer<Translator> callback = invocation.getArgument(2);
            callback.accept(mMockTranslator);
            return null;
        }).when(mMockWrapper).createOnDeviceTranslator(any(), any(), any());
        mTranslateManager.initializeTranslator(ULocale.KOREAN, ULocale.ENGLISH);
        assertTrue(mTranslateManager.isTranslatorReady());

        AutofillId realAutofillId = new TextView(mContext).getAutofillId();

        TranslationResponse failureResponse = createMockTranslationResponse(null, false,
                realAutofillId);

        doAnswer(invocation -> {
            Consumer<TranslationResponse> consumer = invocation.getArgument(3);
            consumer.accept(failureResponse);
            return null;
        }).when(mMockTranslator).translate(any(), any(), any(), any());

        mTranslateManager.startOnDeviceTranslation("Test text", realAutofillId);

        verify(mMockCallback).onTranslationCompleted(eq(null), eq(false));
    }

    // The createMockTranslationResponse helper method is also modified to accept an AutofillId
    // as an argument.
    private TranslationResponse createMockTranslationResponse(String translatedText,
            boolean success, AutofillId autofillId) {
        ViewTranslationResponse viewResponse = new ViewTranslationResponse.Builder(autofillId)
                .setValue(ViewTranslationRequest.ID_TEXT,
                        new TranslationResponseValue.Builder(
                                success ? TranslationResponseValue.STATUS_SUCCESS
                                        : TranslationResponseValue.STATUS_ERROR
                        ).setText(translatedText).build())
                .build();

        SparseArray<ViewTranslationResponse> sparseArray = new SparseArray<>();
        sparseArray.put(0, viewResponse);
        return new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                .setViewTranslationResponses(sparseArray)
                .build();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testResolveTargetLanguageSystemZhTwNoCapReturnsSystemLocale() {
        Locale systemLocale = Locale.TAIWAN;
        doReturn(true).when(mMockWrapper).isAvailable();
        doReturn(Collections.emptySet()).when(mMockWrapper)
                .getOnDeviceTranslationCapabilities(anyInt(), anyInt());

        ULocale result = mTranslateManager.resolveTargetLanguage(systemLocale);

        assertEquals(systemLocale.getLanguage(), result.getName());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testResolveTargetLanguageSystemZhHkNoCapReturnsSystemLocale() {
        Locale systemLocale = new Locale("zh", "HK");
        doReturn(true).when(mMockWrapper).isAvailable();
        doReturn(Collections.emptySet()).when(mMockWrapper)
                .getOnDeviceTranslationCapabilities(anyInt(), anyInt());

        ULocale result = mTranslateManager.resolveTargetLanguage(systemLocale);

        assertEquals(systemLocale.getLanguage(), result.getName());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testResolveTargetLanguageSystemZhCnNoCapReturnsSystemLocale() {
        Locale systemLocale = Locale.CHINA;
        doReturn(true).when(mMockWrapper).isAvailable();
        doReturn(Collections.emptySet()).when(mMockWrapper)
                .getOnDeviceTranslationCapabilities(anyInt(), anyInt());
        ULocale result = mTranslateManager.resolveTargetLanguage(systemLocale);

        assertEquals(systemLocale.getLanguage(), result.getName());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testResolveTargetLanguageWithCapabilityReturnsSupportedLocale() {
        setupMockTranslationCapability("zh_Hant");

        ULocale result = mTranslateManager.resolveTargetLanguage(Locale.TAIWAN);

        assertEquals("zh_Hant", result.getName());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CELLBROADCAST_TRANSLATION)
    public void testResolveTargetLanguageWithCapabilityReturnsZhTw() {
        setupMockTranslationCapability("zh_TW");
        ULocale result = mTranslateManager.resolveTargetLanguage(Locale.TAIWAN);
        assertEquals("zh_TW", result.getName());
    }

    private void setupMockTranslationCapability(String targetLanguageTag) {
        TranslationSpec sourceSpec = new TranslationSpec(ULocale.ENGLISH,
                TranslationSpec.DATA_FORMAT_TEXT);
        TranslationSpec targetSpec = new TranslationSpec(new ULocale(targetLanguageTag),
                TranslationSpec.DATA_FORMAT_TEXT);

        TranslationCapability capability = new TranslationCapability(
                TranslationCapability.STATE_ON_DEVICE,
                sourceSpec,
                targetSpec,
                true, /* uiTranslationEnabled */
                0 /* supportedTranslationFlags */
        );
        Set<TranslationCapability> capabilities = new HashSet<>();
        capabilities.add(capability);

        doReturn(true).when(mMockWrapper).isAvailable();
        doReturn(capabilities).when(mMockWrapper)
                .getOnDeviceTranslationCapabilities(anyInt(), anyInt());
    }
}
