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
import static org.junit.Assert.assertTrue;

import android.telephony.CbGeoUtils.Circle;
import android.telephony.CbGeoUtils.Geometry;
import android.telephony.CbGeoUtils.LatLng;
import android.telephony.CbGeoUtils.Polygon;

import com.android.cellbroadcastreceiver.CbGeoUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CbGeoUtilsTest extends CellBroadcastTest {

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testEncodeGeometriesToString_EmptyList() {
        List<Geometry> geometries = new ArrayList<>();
        String result = CbGeoUtils.encodeGeometriesToString(geometries);
        assertTrue("Encoding an empty list should result in an empty string", result.isEmpty());
    }

    @Test
    public void testEncodeGeometriesToString_SingleCircle() {
        List<Geometry> geometries = new ArrayList<>();
        geometries.add(new Circle(new LatLng(37.422, -122.084), 1000.0));
        String result = CbGeoUtils.encodeGeometriesToString(geometries);
        assertEquals("circle|37.422,-122.084|1000.0", result);
    }

    @Test
    public void testEncodeGeometriesToString_SinglePolygon() {
        List<Geometry> geometries = new ArrayList<>();
        List<LatLng> vertices = Arrays.asList(
                new LatLng(37.1, -122.1),
                new LatLng(37.2, -122.1),
                new LatLng(37.2, -122.2),
                new LatLng(37.1, -122.2)
        );
        geometries.add(new Polygon(vertices));
        String result = CbGeoUtils.encodeGeometriesToString(geometries);
        assertEquals("polygon|37.1,-122.1|37.2,-122.1|37.2,-122.2|37.1,-122.2", result);
    }

    @Test
    public void testEncodeGeometriesToString_MultipleGeometries() {
        List<Geometry> geometries = new ArrayList<>();
        geometries.add(new Circle(new LatLng(10.0, 20.0), 500.0));
        List<LatLng> vertices = Arrays.asList(
                new LatLng(1.0, 1.0),
                new LatLng(1.0, 2.0),
                new LatLng(2.0, 2.0)
        );
        geometries.add(new Polygon(vertices));
        geometries.add(new Circle(new LatLng(-10.0, -20.0), 1500.0));

        String result = CbGeoUtils.encodeGeometriesToString(geometries);
        String expected = "circle|10.0,20.0|500.0;"
                + "polygon|1.0,1.0|1.0,2.0|2.0,2.0;"
                + "circle|-10.0,-20.0|1500.0";
        assertEquals(expected, result);
    }

    @Test
    public void testEncodeGeometriesToString_WithNonIntegerLatLng() {
        List<Geometry> geometries = new ArrayList<>();
        geometries.add(new Circle(new LatLng(37.422123, -122.084321), 1000.5));
        String result = CbGeoUtils.encodeGeometriesToString(geometries);
        assertEquals("circle|37.422123,-122.084321|1000.5", result);
    }
}
