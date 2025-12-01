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

import java.util.ArrayList;
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

    /**
     * Parse the geometries from the encoded string {@code str}. The string must follow the
     * geometry encoding specified by {@link android.provider.Telephony.CellBroadcasts#GEOMETRIES}.
     *
     * @param str the encoded string containing one or more geometric definitions.
     * @return the list of geometry objects parsed from the string.
     */
    @NonNull
    public static List<Geometry> parseGeometriesFromString(@NonNull String str) {
        List<Geometry> geometries = new ArrayList<>();
        for (String geometryStr : str.split("\\s*;\\s*")) {
            String[] geoParameters = geometryStr.split("\\s*\\|\\s*");
            switch (geoParameters[0]) {
                case CIRCLE_SYMBOL:
                    geometries.add(new Circle(parseLatLngFromString(geoParameters[1]),
                            Double.parseDouble(geoParameters[2])));
                    break;
                case POLYGON_SYMBOL:
                    List<LatLng> vertices = new ArrayList<>(geoParameters.length - 1);
                    for (int i = 1; i < geoParameters.length; i++) {
                        vertices.add(parseLatLngFromString(geoParameters[i]));
                    }
                    geometries.add(new Polygon(vertices));
                    break;
                default:
                    final String errorMessage = "Invalid geometry format " + geometryStr;
                    Log.e(TAG, errorMessage);
            }
        }
        return geometries;
    }

    /**
     * Parse {@link LatLng} from {@link String}. Latitude and longitude are separated by ",".
     * Example: "13.56,-55.447".
     *
     * @param str encoded lat/lng string.
     * @return {@link LatLng} object.
     */
    @NonNull
    private static LatLng parseLatLngFromString(@NonNull String str) {
        String[] latLng = str.split("\\s*,\\s*");
        return new LatLng(Double.parseDouble(latLng[0]), Double.parseDouble(latLng[1]));
    }
}
