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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.telephony.CbGeoUtils;
import android.telephony.CbGeoUtils.Circle;
import android.telephony.CbGeoUtils.LatLng;
import android.telephony.CbGeoUtils.Polygon;
import android.telephony.SmsCbMessage;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

/**
 * A helper class to build a geo URI from a cell broadcast message and launch the map activity.
 */
public class CellBroadcastMapLauncher {

    private static final String TAG = "CellBroadcastMapLauncher";
    @VisibleForTesting
    public static final String PERMISSION_ACCESS_CELL_BROADCAST =
            "android.permission.ACCESS_CELL_BROADCAST";
    @VisibleForTesting
    public static final String GEO_URI_SCHEME = "cellbroadcastgeo:";
    private static final String GEOMETRY_TYPE_POLYGON = "polygon";
    private static final String GEOMETRY_TYPE_CIRCLE = "circle";

    /**
     * Builds the URI string from all geometries in the message and launches the map activity.
     *
     * @param context The context from which to launch the activity.
     * @param message The SmsCbMessage containing the geographic information.
     */
    public static void launchMap(Context context, SmsCbMessage message) {
        if (message == null) {
            Log.e(TAG, "No message available to show on map.");
            return;
        }

        String geoUriString = buildGeoUriString(message);
        if (geoUriString == null) {
            Log.e(TAG, "Could not build geo URI from message.");
            return;
        }
        Log.d(TAG, "launchMap: geoUri=" + geoUriString);

        Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUriString));
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfoList = pm.queryIntentActivities(mapIntent,
                PackageManager.MATCH_SYSTEM_ONLY);

        ResolveInfo targetActivityInfo = null;

        // TODO : changed to SdkLevel.isAtLeastC
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.BAKLAVA) {
            for (ResolveInfo info : resolveInfoList) {
                if (info.activityInfo != null) {
                    try {
                        if (pm.checkPermission(PERMISSION_ACCESS_CELL_BROADCAST,
                                info.activityInfo.packageName)
                                == PackageManager.PERMISSION_GRANTED) {
                            targetActivityInfo = info;
                            Log.d(TAG, "Found secure system handler on C+: "
                                    + info.activityInfo.name);
                            break;
                        } else {
                            Log.w(TAG, "System activity " + info.activityInfo.name
                                    + " lacks permission on C+.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking permission for " + info.activityInfo.packageName,
                                e);
                    }
                }
            }
        } else {
            if (resolveInfoList != null && resolveInfoList.size() == 1 && resolveInfoList.get(
                    0).activityInfo != null) {
                targetActivityInfo = resolveInfoList.get(0);
                Log.d(TAG, "Found unique system handler on pre-C: "
                        + targetActivityInfo.activityInfo.name);
            } else if (resolveInfoList != null && resolveInfoList.isEmpty()) {
                Log.e(TAG, "No system activity found to handle the geo intent.");
            } else {
                Log.e(TAG, "More than one system activity found (" + resolveInfoList.size()
                        + ") on pre-C. Not launching.");
            }
        }

        if (targetActivityInfo != null && targetActivityInfo.activityInfo != null) {
            Intent explicitIntent = new Intent(mapIntent);
            explicitIntent.setClassName(targetActivityInfo.activityInfo.packageName,
                    targetActivityInfo.activityInfo.name);
            explicitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(explicitIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Target system activity not found: " + e);
            }
        } else {
            Log.e(TAG, "No secure and unique system activity found.");
        }
    }

    /**
     * Creates a geo URI string with encoded geometry data in the query parameter.
     *
     * @param message The SmsCbMessage containing the geographic information.
     * @return A String for the geo URI, or null if no valid geo data is found.
     */
    private static String buildGeoUriString(SmsCbMessage message) {
        if (message == null) {
            return null;
        }

        List<CbGeoUtils.Geometry> geometries = message.getGeometries();
        if (geometries == null || geometries.isEmpty()) {
            Log.d(TAG, "No geometries list found in the message.");
            return null;
        }

        StringBuilder dataBuilder = new StringBuilder();
        for (CbGeoUtils.Geometry geometry : geometries) {
            if (dataBuilder.length() > 0) {
                dataBuilder.append(";");
            }

            if (geometry instanceof Polygon) {
                Polygon polygon = (Polygon) geometry;
                List<LatLng> vertices = polygon.getVertices();
                if (vertices != null && !vertices.isEmpty()) {
                    dataBuilder.append(GEOMETRY_TYPE_POLYGON);
                    for (LatLng point : vertices) {
                        dataBuilder.append("|").append(
                                String.format(Locale.US, "%.7f,%.7f", point.lat, point.lng));
                    }
                }
            } else if (geometry instanceof Circle) {
                Circle circle = (Circle) geometry;
                LatLng center = circle.getCenter();
                double radius = circle.getRadius();
                if (center != null) {
                    dataBuilder.append(GEOMETRY_TYPE_CIRCLE + "|").append(
                            String.format(Locale.US, "%.7f,%.7f", center.lat, center.lng));
                    dataBuilder.append(String.format(Locale.US, "|%.1f", radius));
                }
            }
        }

        if (dataBuilder.length() > 0) {
            try {
                String encodedData = URLEncoder.encode(dataBuilder.toString(), "UTF-8");
                return GEO_URI_SCHEME + encodedData;
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Failed to encode geometry data", e);
                return null;
            }
        } else {
            Log.d(TAG, "No valid geometries could be encoded.");
            return null;
        }
    }
}
