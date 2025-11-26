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

import static com.android.cellbroadcastreceiver.CellBroadcastMapLauncher.GEO_URI_SCHEME;
import static com.android.cellbroadcastreceiver.CellBroadcastMapLauncher.PERMISSION_ACCESS_CELL_BROADCAST;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.telephony.CbGeoUtils;
import android.telephony.CbGeoUtils.Circle;
import android.telephony.CbGeoUtils.LatLng;
import android.telephony.CbGeoUtils.Polygon;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;

import com.android.cellbroadcastreceiver.CellBroadcastMapLauncher;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CellBroadcastMapLauncherTest {
    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mMockPackageManager;

    private static final String GMS_PACKAGE = "com.google.android.gms";
    private static final String TEST_ACTIVITY_NAME = "MapActivity";

    private SmsCbMessage createSmsCbMessage(List<CbGeoUtils.Geometry> geometries) {
        return new SmsCbMessage(
                SmsCbMessage.MESSAGE_FORMAT_3GPP, 0, 123, new SmsCbLocation(), 4370, "en", 0,
                "Test message body", SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, null, 0,
                geometries, System.currentTimeMillis(), 0, 1);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
    }

    private ResolveInfo createMockResolveInfo(String packageName) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = packageName;
        resolveInfo.activityInfo.name = TEST_ACTIVITY_NAME;
        resolveInfo.activityInfo.applicationInfo = new ApplicationInfo();
        resolveInfo.activityInfo.applicationInfo.packageName = packageName;
        return resolveInfo;
    }

    private String buildExpectedGeoUri(String rawData) throws Exception {
        return GEO_URI_SCHEME + URLEncoder.encode(rawData, "UTF-8");
    }

    @Test
    public void testLaunchMapSuccessPreC() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT <= Build.VERSION_CODES.BAKLAVA);

        List<CbGeoUtils.Geometry> geometries = Collections.singletonList(
                new Circle(new CbGeoUtils.LatLng(37.4, -122.1), 1000.0));
        SmsCbMessage message = createSmsCbMessage(geometries);
        ResolveInfo mockResolveInfo = createMockResolveInfo(GMS_PACKAGE);

        doReturn(Collections.singletonList(mockResolveInfo)).when(mMockPackageManager)
                .queryIntentActivities(any(Intent.class), eq(PackageManager.MATCH_SYSTEM_ONLY));

        CellBroadcastMapLauncher.launchMap(mMockContext, message);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).startActivity(intentCaptor.capture());
        Intent capturedIntent = intentCaptor.getValue();

        assertEquals(Intent.ACTION_VIEW, capturedIntent.getAction());
        assertEquals(Uri.parse(buildExpectedGeoUri("circle|37.4000000,-122.1000000|1000.0")),
                capturedIntent.getData());
        assertEquals(GMS_PACKAGE, capturedIntent.getComponent().getPackageName());
    }

    @Test
    public void testLaunchMapSuccessCWithPermission() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.BAKLAVA);

        List<CbGeoUtils.Geometry> geometries = Collections.singletonList(
                new Polygon(Arrays.asList(new LatLng(1.0, 1.0), new LatLng(2.0, 2.0),
                        new LatLng(1.0, 2.0))));
        SmsCbMessage message = createSmsCbMessage(geometries);
        ResolveInfo mockResolveInfo = createMockResolveInfo(GMS_PACKAGE);

        doReturn(Collections.singletonList(mockResolveInfo)).when(mMockPackageManager)
                .queryIntentActivities(any(Intent.class), eq(PackageManager.MATCH_SYSTEM_ONLY));
        doReturn(PackageManager.PERMISSION_GRANTED).when(mMockPackageManager)
                .checkPermission(PERMISSION_ACCESS_CELL_BROADCAST, GMS_PACKAGE);

        CellBroadcastMapLauncher.launchMap(mMockContext, message);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).startActivity(intentCaptor.capture());
        Intent capturedIntent = intentCaptor.getValue();

        assertEquals(Intent.ACTION_VIEW, capturedIntent.getAction());
        assertEquals(Uri.parse(buildExpectedGeoUri(
                        "polygon|1.0000000,1.0000000|2.0000000,2.0000000|1.0000000,2.0000000")),
                capturedIntent.getData());
        assertEquals(GMS_PACKAGE, capturedIntent.getComponent().getPackageName());
    }

    @Test
    public void testLaunchMapFailCNoPermission() {
        assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.BAKLAVA);

        List<CbGeoUtils.Geometry> geometries = Collections.singletonList(
                new Circle(new CbGeoUtils.LatLng(37.4, -122.1), 1000.0));
        SmsCbMessage message = createSmsCbMessage(geometries);
        ResolveInfo mockResolveInfo = createMockResolveInfo(GMS_PACKAGE);

        doReturn(Collections.singletonList(mockResolveInfo)).when(mMockPackageManager)
                .queryIntentActivities(any(Intent.class), eq(PackageManager.MATCH_SYSTEM_ONLY));
        doReturn(PackageManager.PERMISSION_DENIED).when(mMockPackageManager)
                .checkPermission(PERMISSION_ACCESS_CELL_BROADCAST, GMS_PACKAGE);

        CellBroadcastMapLauncher.launchMap(mMockContext, message);
        verify(mMockContext, never()).startActivity(any(Intent.class));
    }

    @Test
    public void testLaunchMapFailCNoHandlerFound() {
        assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.BAKLAVA);

        List<CbGeoUtils.Geometry> geometries = Collections.singletonList(
                new Circle(new CbGeoUtils.LatLng(37.4, -122.1), 1000.0));
        SmsCbMessage message = createSmsCbMessage(geometries);

        doReturn(Collections.emptyList()).when(mMockPackageManager)
                .queryIntentActivities(any(Intent.class), eq(PackageManager.MATCH_SYSTEM_ONLY));

        CellBroadcastMapLauncher.launchMap(mMockContext, message);
        verify(mMockContext, never()).startActivity(any(Intent.class));
    }

    @Test
    public void testLaunchMapFailPreCMultipleActivities() {
        assumeTrue(Build.VERSION.SDK_INT <= Build.VERSION_CODES.BAKLAVA);

        List<CbGeoUtils.Geometry> geometries = Collections.singletonList(
                new Circle(new CbGeoUtils.LatLng(37.4, -122.1), 1000.0));
        SmsCbMessage message = createSmsCbMessage(geometries);

        doReturn(Arrays.asList(createMockResolveInfo(GMS_PACKAGE),
                createMockResolveInfo("com.other.app")))
                .when(mMockPackageManager).queryIntentActivities(any(Intent.class),
                        eq(PackageManager.MATCH_SYSTEM_ONLY));

        CellBroadcastMapLauncher.launchMap(mMockContext, message);
        verify(mMockContext, never()).startActivity(any(Intent.class));
    }

    @Test
    public void testLaunchMapNoGeometry() {
        SmsCbMessage message = createSmsCbMessage(null);
        CellBroadcastMapLauncher.launchMap(mMockContext, message);
        verify(mMockContext, never()).startActivity(any(Intent.class));
    }
}
