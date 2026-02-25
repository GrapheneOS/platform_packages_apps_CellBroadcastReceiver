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

package com.android.cellbroadcastreceiver.tests;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.CbGeoUtils;
import android.telephony.CbGeoUtils.Circle;
import android.telephony.CbGeoUtils.Geometry;
import android.telephony.CbGeoUtils.LatLng;
import android.telephony.CbGeoUtils.Polygon;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import androidx.core.app.ActivityCompat;

import com.android.internal.telephony.gsm.SmsCbConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Test activity for sending Cell Broadcast alerts with various languages and geographical scopes.
 */
public class TranslationMapTestActivity extends Activity {
    private static final String TAG = "TranslationMapTest";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final double GEO_RADIUS_METERS = 5000;

    private LocationManager mLocationManager;
    private Location mLastLocation;

    private RadioGroup mLanguageRadioGroup;
    private RadioGroup mGeometryRadioGroup;
    private Button mSendAlertButton;

    // Sample messages for different languages
    private static final String MSG_EN = "National Weather Service:\n"
            + "SEVERE THUNDERSTORM WARNING in effect for this area until 5:30 PM CDT for "
            + "DESTRUCTIVE 80 mph winds.\n"
            + "Take shelter in a sturdy building, away from windows. Flying debris may be deadly "
            + "to those caught without shelter.";
    private static final String MSG_KO = "기상청:\n"
            + "이 지역에 오후 5시 30분 CDT까지 파괴적인 시속 80마일의 바람에 대한 강한 뇌우 경보가 발효 중입니다.\n"
            + "창문에서 떨어진 튼튼한 건물로 대피하십시오. 날아다니는 파편은 대피하지 못한 사람들에게 치명적일 수 있습니다.";
    private static final String MSG_ZH = "国家气象局:\n"
            + "该地区已发布强烈雷暴警告，持续到中部夏令时间下午5:30，原因是存在破坏性的80英里/小时风速。\n"
            + "请在远离窗户的坚固建筑物中寻求庇护。飞溅的碎片可能对未避难的人造成致命伤害。";
    private static final String MSG_JA = "国立気象局:\n"
            + "この地域に、破壊的な時速80マイルの風のため、午後5時30分CDTまで激しい雷雨警報が発令されています。\n"
            + "窓から離れた頑丈な建物に避難してください。飛来する破片は、"
            + "避難していない人々にとって命にかかわる可能性があります。";
    private boolean mDelayBeforeSending;
    private static final int DELAY_BEFORE_SENDING_MSEC = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.translation_map_test_activity);

        EdgeToEdgeUtil.setupEdgeToEdge(this);

        mLanguageRadioGroup = findViewById(R.id.language_radio_group);
        mGeometryRadioGroup = findViewById(R.id.geometry_radio_group);
        mSendAlertButton = findViewById(R.id.send_alert_button);
        final CheckBox delayCheckbox = (CheckBox) findViewById(R.id.delay_checkbox);
        delayCheckbox.setOnClickListener(v -> mDelayBeforeSending = delayCheckbox.isChecked());
        mDelayBeforeSending = delayCheckbox.isChecked();

        mLocationManager = getSystemService(LocationManager.class);
        requestLocationPermission();

        mSendAlertButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDelayBeforeSending && v != null) {
                    Message msg = mDelayHandler.obtainMessage(0, this);
                    mDelayHandler.sendMessageDelayed(msg, DELAY_BEFORE_SENDING_MSEC);
                } else {
                    fetchLastLocation();
                    sendTestAlert();
                }
            }
        });
    }

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            fetchLastLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLastLocation();
            } else {
                Log.d(TAG, "Location permission denied");
            }
        }
    }

    private void fetchLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted.");
            mLastLocation = null;
            return;
        }
        try {
            // Get last known location from Network provider first, then GPS
            Location bestLocation = null;
            Location networkLocation = mLocationManager.getLastKnownLocation(
                    LocationManager.NETWORK_PROVIDER);
            Location gpsLocation = mLocationManager.getLastKnownLocation(
                    LocationManager.GPS_PROVIDER);

            if (gpsLocation != null && networkLocation != null) {
                bestLocation = gpsLocation.getTime() > networkLocation.getTime() ? gpsLocation
                        : networkLocation;
            } else if (gpsLocation != null) {
                bestLocation = gpsLocation;
            } else {
                bestLocation = networkLocation;
            }

            if (bestLocation != null) {
                mLastLocation = bestLocation;
                Log.d(TAG, "Last location fetched: " + mLastLocation.getLatitude() + ", "
                        + mLastLocation.getLongitude() + " from " + mLastLocation.getProvider());
            } else {
                mLastLocation = null;
                Log.w(TAG, "Failed to get last known location from any provider.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception in fetchLastLocation", e);
            mLastLocation = null;
        }
    }

    private String getSelectedLanguageCode() {
        int selectedId = mLanguageRadioGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.lang_ko) {
            return "ko";
        }
        if (selectedId == R.id.lang_zh) {
            return "zh";
        }
        if (selectedId == R.id.lang_ja) {
            return "ja";
        }
        return "en";
    }

    private String getMessageBody() {
        int selectedId = mLanguageRadioGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.lang_ko) {
            return MSG_KO;
        }
        if (selectedId == R.id.lang_zh) {
            return MSG_ZH;
        }
        if (selectedId == R.id.lang_ja) {
            return MSG_JA;
        }
        return MSG_EN;
    }

    private List<Geometry> getGeometries() {
        List<Geometry> geometries = new ArrayList<>();
        int selectedGeoId = mGeometryRadioGroup.getCheckedRadioButtonId();
        boolean locationAvailable = mLastLocation != null;

        if (selectedGeoId == R.id.geo_none) {
            return null;
        }

        if (locationAvailable) {
            Log.d(TAG, "Using current location for geometries.");
            LatLng center = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());

            if (selectedGeoId == R.id.geo_circle) {
                geometries.add(new Circle(center, GEO_RADIUS_METERS));
                Log.d(TAG, "Including Circle Geometry centered at current location");
            } else if (selectedGeoId == R.id.geo_polygon) {
                geometries.add(createHeptagon(center, GEO_RADIUS_METERS));
                Log.d(TAG, "Including Polygon Geometry centered at current location");
            } else if (selectedGeoId == R.id.geo_mixed) {
                geometries.add(new Circle(center, GEO_RADIUS_METERS));
                geometries.add(createHeptagon(center, GEO_RADIUS_METERS));
                Log.d(TAG, "Including Mixed Geometry centered at current location");
            }
        } else {
            Log.w(TAG, "Current location not available, falling back to sample geometries.");

            if (selectedGeoId == R.id.geo_circle) {
                try {
                    geometries.addAll(
                            CbGeoUtils.parseGeometriesFromString(SendTestMessages.GEO_DATA_6));
                    Log.d(TAG, "Including SAMPE Circle Geometry (GEO_DATA_6)");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse sample circle geometries", e);
                }
            } else if (selectedGeoId == R.id.geo_polygon) {
                try {
                    geometries.addAll(
                            CbGeoUtils.parseGeometriesFromString(SendTestMessages.GEO_DATA_5));
                    Log.d(TAG, "Including SAMPLE Polygon Geometry (GEO_DATA_5)");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse sample polygon geometries", e);
                }
            } else if (selectedGeoId == R.id.geo_mixed) {
                try {
                    geometries.addAll(
                            CbGeoUtils.parseGeometriesFromString(SendTestMessages.GEO_DATA_6));
                    geometries.addAll(
                            CbGeoUtils.parseGeometriesFromString(SendTestMessages.GEO_DATA_5));
                    Log.d(TAG, "Including SAMPLE Mixed Geometry (GEO_DATA_6 and GEO_DATA_5)");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse sample mixed geometries", e);
                }
            }
        }
        return geometries.isEmpty() ? null : geometries;
    }

    private Polygon createHeptagon(LatLng center, double radiusMeters) {
        List<LatLng> vertices = new ArrayList<>();
        int numVertices = 7;
        double centerLatRad = Math.toRadians(center.lat);
        double centerLngRad = Math.toRadians(center.lng);
        double angularDistance = radiusMeters / 6371000.0;

        for (int i = 0; i < numVertices; i++) {
            double bearing = Math.toRadians(i * (360.0 / numVertices));

            double latRad = Math.asin(Math.sin(centerLatRad) * Math.cos(angularDistance)
                    + Math.cos(centerLatRad) * Math.sin(angularDistance) * Math.cos(bearing));
            double lngRad = centerLngRad + Math.atan2(
                    Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(centerLatRad),
                    Math.cos(angularDistance) - Math.sin(centerLatRad) * Math.sin(latRad));

            vertices.add(new LatLng(Math.toDegrees(latRad), Math.toDegrees(lngRad)));
        }
        return new Polygon(vertices);
    }

    private int getSerialNumber() {
        return new Random().nextInt(65535) + 1;
    }

    private void sendTestAlert() {
        String languageCode = getSelectedLanguageCode();
        List<Geometry> geometries = getGeometries();

        String messageBody = getMessageBody();
        int serialNumber = getSerialNumber();
        int serviceCategory = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY;

        SendGsmCmasMessages.testSendCmasAlertWithServiceCategory(
                getApplicationContext(),
                serviceCategory,
                serialNumber,
                messageBody,
                languageCode,
                false,
                geometries);
        Log.d(TAG, "Sent alert: Lang=" + languageCode + ", Geo=" + (geometries != null)
                + ", SN=" + serialNumber);
    }

    private final Handler mDelayHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // call the onClick() method again, passing null View.
            // The callback will ignore mDelayBeforeSending when the View is null.
            View.OnClickListener pendingButtonClick = (View.OnClickListener) msg.obj;
            pendingButtonClick.onClick(null);
        }
    };
}
