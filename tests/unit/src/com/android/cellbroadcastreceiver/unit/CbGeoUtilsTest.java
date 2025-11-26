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
import static org.junit.Assert.fail;

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
    public void testEncodeGeometriesToStringEmptyList() {
        List<Geometry> geometries = new ArrayList<>();
        String result = CbGeoUtils.encodeGeometriesToString(geometries);
        assertTrue("Encoding an empty list should result in an empty string", result.isEmpty());
    }

    @Test
    public void testEncodeGeometriesToStringSingleCircle() {
        List<Geometry> geometries = new ArrayList<>();
        geometries.add(new Circle(new LatLng(37.422, -122.084), 1000.0));
        String result = CbGeoUtils.encodeGeometriesToString(geometries);
        assertEquals("circle|37.422,-122.084|1000.0", result);
    }

    @Test
    public void testEncodeGeometriesToStringSinglePolygon() {
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
    public void testEncodeGeometriesToStringMultipleGeometries() {
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
    public void testEncodeGeometriesToStringWithNonIntegerLatLng() {
        List<Geometry> geometries = new ArrayList<>();
        geometries.add(new Circle(new LatLng(37.422123, -122.084321), 1000.5));
        String result = CbGeoUtils.encodeGeometriesToString(geometries);
        assertEquals("circle|37.422123,-122.084321|1000.5", result);
    }

    @Test
    public void testParseGeometriesFromStringEmptyString() {
        List<Geometry> result = CbGeoUtils.parseGeometriesFromString("");
        assertTrue("Parsing an empty string should result in an empty list", result.isEmpty());
    }

    @Test
    public void testParseGeometriesFromStringSingleCircle() {
        String input = "circle|37.422,-122.084|1000.0";
        List<Geometry> result = CbGeoUtils.parseGeometriesFromString(input);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof Circle);
        Circle circle = (Circle) result.get(0);
        assertEquals(new LatLng(37.422, -122.084), circle.getCenter());
        assertEquals(1000.0, circle.getRadius(), 0.0);
    }

    @Test
    public void testParseGeometriesFromStringSinglePolygon() {
        String input = "polygon|37.1,-122.1|37.2,-122.1|37.2,-122.2|37.1,-122.2";
        List<Geometry> result = CbGeoUtils.parseGeometriesFromString(input);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof Polygon);
        Polygon polygon = (Polygon) result.get(0);
        List<LatLng> expectedVertices = Arrays.asList(
                new LatLng(37.1, -122.1),
                new LatLng(37.2, -122.1),
                new LatLng(37.2, -122.2),
                new LatLng(37.1, -122.2)
        );
        assertEquals(expectedVertices, polygon.getVertices());
    }

    @Test
    public void testParseGeometriesFromStringMultipleGeometries() {
        String input =
                "circle|10.0,20.0|500.0;polygon|1.0,1.0|1.0,2.0|2.0,2.0;circle|-10.0,-20.0|1500.0";
        List<Geometry> result = CbGeoUtils.parseGeometriesFromString(input);
        assertEquals(3, result.size());

        assertTrue(result.get(0) instanceof Circle);
        assertEquals(new LatLng(10.0, 20.0), ((Circle) result.get(0)).getCenter());
        assertEquals(500.0, ((Circle) result.get(0)).getRadius(), 0.0);

        assertTrue(result.get(1) instanceof Polygon);
        assertEquals(
                Arrays.asList(new LatLng(1.0, 1.0), new LatLng(1.0, 2.0), new LatLng(2.0, 2.0)),
                ((Polygon) result.get(1)).getVertices());

        assertTrue(result.get(2) instanceof Circle);
        assertEquals(new LatLng(-10.0, -20.0), ((Circle) result.get(2)).getCenter());
        assertEquals(1500.0, ((Circle) result.get(2)).getRadius(), 0.0);
    }

    @Test
    public void testParseGeometriesFromStringWithSpaces() {
        String input = "circle | 37.422 , -122.084 | 1000.0 ; polygon | 37.1,-122.1 | 37.2,-122.1 ";
        List<Geometry> result = CbGeoUtils.parseGeometriesFromString(input);
        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof Circle);
        assertTrue(result.get(1) instanceof Polygon);
    }

    @Test
    public void testParseGeometriesFromStringInvalidFormatNoType() {
        String input = "|37.422,-122.084|1000.0";
        List<Geometry> result = CbGeoUtils.parseGeometriesFromString(input);
        // Expecting to log an error and skip this geometry
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseGeometriesFromStringInvalidFormatUnknownType() {
        String input = "triangle|0,0|1,1|0,1";
        List<Geometry> result = CbGeoUtils.parseGeometriesFromString(input);
        // Expecting to log an error and skip this geometry
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseGeometriesFromStringInvalidFormatCircleMissingRadius() {
        String input = "circle|37.422,-122.084";
        try {
            CbGeoUtils.parseGeometriesFromString(input);
            fail("Expected ArrayIndexOutOfBoundsException for missing circle radius");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Expected
        }
    }

    @Test
    public void testParseGeometriesFromStringInvalidFormatCircleNotANumber() {
        String input = "circle|37.422,-122.084|radius";
        try {
            CbGeoUtils.parseGeometriesFromString(input);
            fail("Expected NumberFormatException for invalid radius");
        } catch (NumberFormatException e) {
            // Expected
        }
    }

    @Test
    public void testParseGeometriesFromStringInvalidFormatLatLngMissingCoordinate() {
        String input = "circle|37.422|1000.0";
        try {
            CbGeoUtils.parseGeometriesFromString(input);
            fail("Expected ArrayIndexOutOfBoundsException for malformed LatLng");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Expected
        }
    }

    @Test
    public void testParseGeometriesFromStringInvalidFormatLatLngNotANumber() {
        String input = "circle|37.422,lng|1000.0";
        try {
            CbGeoUtils.parseGeometriesFromString(input);
            fail("Expected NumberFormatException for invalid coordinate");
        } catch (NumberFormatException e) {
            // Expected
        }
    }

    @Test
    public void testParseGeometriesFromStringPolygonNotEnoughVertices() {
        String input = "polygon|1.0,1.0";
        List<Geometry> result = CbGeoUtils.parseGeometriesFromString(input);
        // While a polygon with one vertex is strange, the code doesn't prevent it.
        // The Polygon constructor might have a check, but the parsing logic itself is tested here.
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof Polygon);
        assertEquals(1, ((Polygon) result.get(0)).getVertices().size());
    }
}
