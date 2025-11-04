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

import android.annotation.NonNull;
import android.telephony.CbGeoUtils.Circle;
import android.telephony.CbGeoUtils.Geometry;
import android.telephony.CbGeoUtils.LatLng;
import android.telephony.CbGeoUtils.Polygon;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This utils class is specifically used for geo-targeting of CellBroadcast messages.
 * The coordinates used by this utils class are latitude and longitude, but some algorithms in this
 * class only use them as coordinates on plane, so the calculation will be inaccurate. So don't use
 * this class for anything other than geo-targeting of cellbroadcast messages.
 */
public class CbGeoUtils {

    private static final String TAG = "CbGeoUtils";

    /** The identifier of geometry in the encoded string. */
    private static final String CIRCLE_SYMBOL = "circle";
    private static final String POLYGON_SYMBOL = "polygon";

    /**
     * Encode a list of geometry objects to string. The encoding format is specified by
     * {@link android.provider.Telephony.CellBroadcasts#GEOMETRIES}.
     *
     * @param geometries the list of geometry objects needed to be encoded.
     * @return the encoded string.
     */
    @NonNull
    public static String encodeGeometriesToString(@NonNull List<Geometry> geometries) {
        if (geometries == null) {
            return "";
        }
        return geometries.stream()
                .map(geometry -> encodeGeometryToString(geometry))
                .filter(encodedStr -> !TextUtils.isEmpty(encodedStr))
                .collect(Collectors.joining(";"));
    }

    /**
     * Encode the geometry object to string. The encoding format is specified by
     * {@link android.provider.Telephony.CellBroadcasts#GEOMETRIES}.
     *
     * @param geometry the geometry object needs to be encoded.
     * @return the encoded string.
     */
    @NonNull
    private static String encodeGeometryToString(@NonNull Geometry geometry) {
        StringBuilder sb = new StringBuilder();
        if (geometry instanceof Polygon) {
            sb.append(POLYGON_SYMBOL);
            for (LatLng latLng : ((Polygon) geometry).getVertices()) {
                sb.append("|");
                sb.append(latLng.lat);
                sb.append(",");
                sb.append(latLng.lng);
            }
        } else if (geometry instanceof Circle) {
            sb.append(CIRCLE_SYMBOL);
            Circle circle = (Circle) geometry;

            // Center
            sb.append("|");
            sb.append(circle.getCenter().lat);
            sb.append(",");
            sb.append(circle.getCenter().lng);

            // Radius
            sb.append("|");
            sb.append(circle.getRadius());
        } else {
            Log.e(TAG, "Unsupported geometry object " + geometry);
            return null;
        }
        return sb.toString();
    }
}
