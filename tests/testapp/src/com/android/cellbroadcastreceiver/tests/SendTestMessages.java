/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Telephony.Sms.Intents;
import android.telephony.CbGeoUtils;
import android.telephony.CbGeoUtils.Geometry;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.util.Log;

import com.android.cellbroadcastservice.SmsCbHeader;
import com.android.internal.telephony.CellBroadcastUtils;
import com.android.internal.telephony.gsm.SmsCbConstants;
import com.android.internal.telephony.uicc.IccUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Send test messages.
 */
public class SendTestMessages {

    private static String TAG = "SendTestMessages";

    private static final int DCS_7BIT_ENGLISH = 0x01;
    private static final int DCS_16BIT_UCS2 = 0x48;

    /* ETWS Test message including header */
    private static final byte[] etwsMessageNormal = IccUtils.hexStringToBytes("000011001101" +
            "EA305BAE57CE770C531790E85C716CBF3044573065B930675730" +
            "9707767A751F30025F37304463FA308C306B5099304830664E0B30553044FF086C178C615E81FF09" +
            "0000000000000000000000000000");

    private static final byte[] etwsMessageCancel = IccUtils.hexStringToBytes("000011001101" +
            "EA305148307B3069002800310030003A0035" +
            "00320029306E7DCA602557309707901F5831309253D66D883057307E3059FF086C178C615E81FF09" +
            "00000000000000000000000000000000000000000000");

    private static final byte[] etwsMessageTest = IccUtils.hexStringToBytes("000011031101" +
            "EA305BAE57CE770C531790E85C716CBF3044" +
            "573065B9306757309707300263FA308C306B5099304830664E0B30553044FF086C178C615E81FF09" +
            "00000000000000000000000000000000000000000000");

    private static final byte[] gsm7BitTest = {
            (byte)0xC0, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x40, (byte)0x11, (byte)0x41,
            (byte)0xD0, (byte)0x71, (byte)0xDA, (byte)0x04, (byte)0x91, (byte)0xCB, (byte)0xE6,
            (byte)0x70, (byte)0x9D, (byte)0x4D, (byte)0x07, (byte)0x85, (byte)0xD9, (byte)0x70,
            (byte)0x74, (byte)0x58, (byte)0x5C, (byte)0xA6, (byte)0x83, (byte)0xDA, (byte)0xE5,
            (byte)0xF9, (byte)0x3C, (byte)0x7C, (byte)0x2E, (byte)0x83, (byte)0xEE, (byte)0x69,
            (byte)0x3A, (byte)0x1A, (byte)0x34, (byte)0x0E, (byte)0xCB, (byte)0xE5, (byte)0xE9,
            (byte)0xF0, (byte)0xB9, (byte)0x0C, (byte)0x92, (byte)0x97, (byte)0xE9, (byte)0x75,
            (byte)0xB9, (byte)0x1B, (byte)0x04, (byte)0x0F, (byte)0x93, (byte)0xC9, (byte)0x69,
            (byte)0xF7, (byte)0xB9, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x00
    };

    private static final byte[] gsm7BitTestUmts = {
            (byte)0x01, (byte)0x00, (byte)0x01, (byte)0xC0, (byte)0x00, (byte)0x40,

            (byte)0x01,

            (byte)0x41, (byte)0xD0, (byte)0x71, (byte)0xDA, (byte)0x04, (byte)0x91,
            (byte)0xCB, (byte)0xE6, (byte)0x70, (byte)0x9D, (byte)0x4D, (byte)0x07,
            (byte)0x85, (byte)0xD9, (byte)0x70, (byte)0x74, (byte)0x58, (byte)0x5C,
            (byte)0xA6, (byte)0x83, (byte)0xDA, (byte)0xE5, (byte)0xF9, (byte)0x3C,
            (byte)0x7C, (byte)0x2E, (byte)0x83, (byte)0xEE, (byte)0x69, (byte)0x3A,
            (byte)0x1A, (byte)0x34, (byte)0x0E, (byte)0xCB, (byte)0xE5, (byte)0xE9,
            (byte)0xF0, (byte)0xB9, (byte)0x0C, (byte)0x92, (byte)0x97, (byte)0xE9,
            (byte)0x75, (byte)0xB9, (byte)0x1B, (byte)0x04, (byte)0x0F, (byte)0x93,
            (byte)0xC9, (byte)0x69, (byte)0xF7, (byte)0xB9, (byte)0xD1, (byte)0x68,
            (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1,
            (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3,
            (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46,
            (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x00,

            (byte)0x34
    };

    private static final byte[] gsm7BitTestMultipageUmts = {
            (byte)0x01, (byte)0x00, (byte)0x01, (byte)0xC0, (byte)0x00, (byte)0x40,

            (byte)0x02,

            (byte)0xC6, (byte)0xB4, (byte)0x7C, (byte)0x4E, (byte)0x07, (byte)0xC1,
            (byte)0xC3, (byte)0xE7, (byte)0xF2, (byte)0xAA, (byte)0xD1, (byte)0x68,
            (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1,
            (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3,
            (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46,
            (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A,
            (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34,
            (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68,
            (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1,
            (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3,
            (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46,
            (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x00,

            (byte)0x0A,

            (byte)0xD3, (byte)0xF2, (byte)0xF8, (byte)0xED, (byte)0x26, (byte)0x83,
            (byte)0xE0, (byte)0xE1, (byte)0x73, (byte)0xB9, (byte)0xD1, (byte)0x68,
            (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1,
            (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3,
            (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46,
            (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A,
            (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34,
            (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68,
            (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1,
            (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3,
            (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46,
            (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x00,

            (byte)0x0A
    };

    private static final byte[] gsm7BitTestMultipage1 = {
            (byte)0x01, (byte)0x00, (byte)0x01, (byte)0xC0, (byte)0x00, (byte)0x40,
            (byte)0xC6, (byte)0xB4, (byte)0x7C, (byte)0x4E, (byte)0x07, (byte)0xC1,
            (byte)0xC3, (byte)0xE7, (byte)0xF2, (byte)0xAA, (byte)0xD1, (byte)0x68,
            (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1,
            (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3,
            (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46,
            (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A,
            (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34,
            (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68,
            (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1,
            (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3,
            (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46,
            (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x00
    };

    private static final byte[] gsm7BitTestMultipage2 = {
            (byte)0x01, (byte)0x00, (byte)0x01, (byte)0xC0, (byte)0x00, (byte)0x40,
            (byte)0xD3, (byte)0xF2, (byte)0xF8, (byte)0xED, (byte)0x26, (byte)0x83,
            (byte)0xE0, (byte)0xE1, (byte)0x73, (byte)0xB9, (byte)0xD1, (byte)0x68,
            (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1,
            (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3,
            (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46,
            (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A,
            (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34,
            (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68,
            (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1,
            (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3,
            (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46,
            (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x00
    };

    private static final byte[] gsm7BitTestNoPadding = {
            (byte)0xC0, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x40, (byte)0x11, (byte)0x41,
            (byte)0xD0, (byte)0x71, (byte)0xDA, (byte)0x04, (byte)0x91, (byte)0xCB, (byte)0xE6,
            (byte)0x70, (byte)0x9D, (byte)0x4D, (byte)0x07, (byte)0x85, (byte)0xD9, (byte)0x70,
            (byte)0x74, (byte)0x58, (byte)0x5C, (byte)0xA6, (byte)0x83, (byte)0xDA, (byte)0xE5,
            (byte)0xF9, (byte)0x3C, (byte)0x7C, (byte)0x2E, (byte)0x83, (byte)0xC4, (byte)0xE5,
            (byte)0xB4, (byte)0xFB, (byte)0x0C, (byte)0x2A, (byte)0xE3, (byte)0xC3, (byte)0x63,
            (byte)0x3A, (byte)0x3B, (byte)0x0F, (byte)0xCA, (byte)0xCD, (byte)0x40, (byte)0x63,
            (byte)0x74, (byte)0x58, (byte)0x1E, (byte)0x1E, (byte)0xD3, (byte)0xCB, (byte)0xF2,
            (byte)0x39, (byte)0x88, (byte)0xFD, (byte)0x76, (byte)0x9F, (byte)0x59, (byte)0xA0,
            (byte)0x76, (byte)0x39, (byte)0xEC, (byte)0x4E, (byte)0xBB, (byte)0xCF, (byte)0x20,
            (byte)0x3A, (byte)0xBA, (byte)0x2C, (byte)0x2F, (byte)0x83, (byte)0xD2, (byte)0x73,
            (byte)0x90, (byte)0xFB, (byte)0x0D, (byte)0x82, (byte)0x87, (byte)0xC9, (byte)0xE4,
            (byte)0xB4, (byte)0xFB, (byte)0x1C, (byte)0x02
    };

    private static final byte[] gsm7BitTestNoPaddingUmts = {
            (byte)0x01, (byte)0x00, (byte)0x01, (byte)0xC0, (byte)0x00, (byte)0x40,

            (byte)0x01,

            (byte)0x41, (byte)0xD0, (byte)0x71, (byte)0xDA, (byte)0x04, (byte)0x91,
            (byte)0xCB, (byte)0xE6, (byte)0x70, (byte)0x9D, (byte)0x4D, (byte)0x07,
            (byte)0x85, (byte)0xD9, (byte)0x70, (byte)0x74, (byte)0x58, (byte)0x5C,
            (byte)0xA6, (byte)0x83, (byte)0xDA, (byte)0xE5, (byte)0xF9, (byte)0x3C,
            (byte)0x7C, (byte)0x2E, (byte)0x83, (byte)0xC4, (byte)0xE5, (byte)0xB4,
            (byte)0xFB, (byte)0x0C, (byte)0x2A, (byte)0xE3, (byte)0xC3, (byte)0x63,
            (byte)0x3A, (byte)0x3B, (byte)0x0F, (byte)0xCA, (byte)0xCD, (byte)0x40,
            (byte)0x63, (byte)0x74, (byte)0x58, (byte)0x1E, (byte)0x1E, (byte)0xD3,
            (byte)0xCB, (byte)0xF2, (byte)0x39, (byte)0x88, (byte)0xFD, (byte)0x76,
            (byte)0x9F, (byte)0x59, (byte)0xA0, (byte)0x76, (byte)0x39, (byte)0xEC,
            (byte)0x4E, (byte)0xBB, (byte)0xCF, (byte)0x20, (byte)0x3A, (byte)0xBA,
            (byte)0x2C, (byte)0x2F, (byte)0x83, (byte)0xD2, (byte)0x73, (byte)0x90,
            (byte)0xFB, (byte)0x0D, (byte)0x82, (byte)0x87, (byte)0xC9, (byte)0xE4,
            (byte)0xB4, (byte)0xFB, (byte)0x1C, (byte)0x02,

            (byte)0x52
    };

    private static final byte[] gsm7BitTestWithLanguage = {
            (byte)0xC0, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x04, (byte)0x11, (byte)0x41,
            (byte)0xD0, (byte)0x71, (byte)0xDA, (byte)0x04, (byte)0x91, (byte)0xCB, (byte)0xE6,
            (byte)0x70, (byte)0x9D, (byte)0x4D, (byte)0x07, (byte)0x85, (byte)0xD9, (byte)0x70,
            (byte)0x74, (byte)0x58, (byte)0x5C, (byte)0xA6, (byte)0x83, (byte)0xDA, (byte)0xE5,
            (byte)0xF9, (byte)0x3C, (byte)0x7C, (byte)0x2E, (byte)0x83, (byte)0xEE, (byte)0x69,
            (byte)0x3A, (byte)0x1A, (byte)0x34, (byte)0x0E, (byte)0xCB, (byte)0xE5, (byte)0xE9,
            (byte)0xF0, (byte)0xB9, (byte)0x0C, (byte)0x92, (byte)0x97, (byte)0xE9, (byte)0x75,
            (byte)0xB9, (byte)0x1B, (byte)0x04, (byte)0x0F, (byte)0x93, (byte)0xC9, (byte)0x69,
            (byte)0xF7, (byte)0xB9, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x00
    };

    private static final byte[] gsm7BitTestWithLanguageInBody = {
            (byte)0xC0, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x10, (byte)0x11, (byte)0x73,
            (byte)0x7B, (byte)0x23, (byte)0x08, (byte)0x3A, (byte)0x4E, (byte)0x9B, (byte)0x20,
            (byte)0x72, (byte)0xD9, (byte)0x1C, (byte)0xAE, (byte)0xB3, (byte)0xE9, (byte)0xA0,
            (byte)0x30, (byte)0x1B, (byte)0x8E, (byte)0x0E, (byte)0x8B, (byte)0xCB, (byte)0x74,
            (byte)0x50, (byte)0xBB, (byte)0x3C, (byte)0x9F, (byte)0x87, (byte)0xCF, (byte)0x65,
            (byte)0xD0, (byte)0x3D, (byte)0x4D, (byte)0x47, (byte)0x83, (byte)0xC6, (byte)0x61,
            (byte)0xB9, (byte)0x3C, (byte)0x1D, (byte)0x3E, (byte)0x97, (byte)0x41, (byte)0xF2,
            (byte)0x32, (byte)0xBD, (byte)0x2E, (byte)0x77, (byte)0x83, (byte)0xE0, (byte)0x61,
            (byte)0x32, (byte)0x39, (byte)0xED, (byte)0x3E, (byte)0x37, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x00
    };

    private static final byte[] gsm7BitTestWithLanguageInBodyUmts = {
            (byte)0x01, (byte)0x00, (byte)0x01, (byte)0xC0, (byte)0x00, (byte)0x10,

            (byte)0x01,

            (byte)0x73, (byte)0x7B, (byte)0x23, (byte)0x08, (byte)0x3A, (byte)0x4E,
            (byte)0x9B, (byte)0x20, (byte)0x72, (byte)0xD9, (byte)0x1C, (byte)0xAE,
            (byte)0xB3, (byte)0xE9, (byte)0xA0, (byte)0x30, (byte)0x1B, (byte)0x8E,
            (byte)0x0E, (byte)0x8B, (byte)0xCB, (byte)0x74, (byte)0x50, (byte)0xBB,
            (byte)0x3C, (byte)0x9F, (byte)0x87, (byte)0xCF, (byte)0x65, (byte)0xD0,
            (byte)0x3D, (byte)0x4D, (byte)0x47, (byte)0x83, (byte)0xC6, (byte)0x61,
            (byte)0xB9, (byte)0x3C, (byte)0x1D, (byte)0x3E, (byte)0x97, (byte)0x41,
            (byte)0xF2, (byte)0x32, (byte)0xBD, (byte)0x2E, (byte)0x77, (byte)0x83,
            (byte)0xE0, (byte)0x61, (byte)0x32, (byte)0x39, (byte)0xED, (byte)0x3E,
            (byte)0x37, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3, (byte)0xD1,
            (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46, (byte)0xA3,
            (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D, (byte)0x46,
            (byte)0xA3, (byte)0xD1, (byte)0x68, (byte)0x34, (byte)0x1A, (byte)0x8D,
            (byte)0x46, (byte)0xA3, (byte)0xD1, (byte)0x00,

            (byte)0x37
    };

    private static final byte[] gsmUcs2Test = {
            (byte)0xC0, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x48, (byte)0x11, (byte)0x00,
            (byte)0x41, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x55, (byte)0x00, (byte)0x43,
            (byte)0x00, (byte)0x53, (byte)0x00, (byte)0x32, (byte)0x00, (byte)0x20, (byte)0x00,
            (byte)0x6D, (byte)0x00, (byte)0x65, (byte)0x00, (byte)0x73, (byte)0x00, (byte)0x73,
            (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x67, (byte)0x00, (byte)0x65, (byte)0x00,
            (byte)0x20, (byte)0x00, (byte)0x63, (byte)0x00, (byte)0x6F, (byte)0x00, (byte)0x6E,
            (byte)0x00, (byte)0x74, (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x69, (byte)0x00,
            (byte)0x6E, (byte)0x00, (byte)0x69, (byte)0x00, (byte)0x6E, (byte)0x00, (byte)0x67,
            (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x20, (byte)0x04,
            (byte)0x34, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x63, (byte)0x00, (byte)0x68,
            (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x72, (byte)0x00, (byte)0x61, (byte)0x00,
            (byte)0x63, (byte)0x00, (byte)0x74, (byte)0x00, (byte)0x65, (byte)0x00, (byte)0x72,
            (byte)0x00, (byte)0x0D, (byte)0x00, (byte)0x0D
    };

    private static final byte[] gsmUcs2TestUmts = {
            (byte)0x01, (byte)0x00, (byte)0x01, (byte)0xC0, (byte)0x00, (byte)0x48,

            (byte)0x01,

            (byte)0x00, (byte)0x41, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x55,
            (byte)0x00, (byte)0x43, (byte)0x00, (byte)0x53, (byte)0x00, (byte)0x32,
            (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x6D, (byte)0x00, (byte)0x65,
            (byte)0x00, (byte)0x73, (byte)0x00, (byte)0x73, (byte)0x00, (byte)0x61,
            (byte)0x00, (byte)0x67, (byte)0x00, (byte)0x65, (byte)0x00, (byte)0x20,
            (byte)0x00, (byte)0x63, (byte)0x00, (byte)0x6F, (byte)0x00, (byte)0x6E,
            (byte)0x00, (byte)0x74, (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x69,
            (byte)0x00, (byte)0x6E, (byte)0x00, (byte)0x69, (byte)0x00, (byte)0x6E,
            (byte)0x00, (byte)0x67, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x61,
            (byte)0x00, (byte)0x20, (byte)0x04, (byte)0x34, (byte)0x00, (byte)0x20,
            (byte)0x00, (byte)0x63, (byte)0x00, (byte)0x68, (byte)0x00, (byte)0x61,
            (byte)0x00, (byte)0x72, (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x63,
            (byte)0x00, (byte)0x74, (byte)0x00, (byte)0x65, (byte)0x00, (byte)0x72,
            (byte)0x00, (byte)0x0D, (byte)0x00, (byte)0x0D,

            (byte)0x4E
    };

    private static final byte[] gsmUcs2TestMultipageUmts = {
            (byte)0x01, (byte)0x00, (byte)0x01, (byte)0xC0, (byte)0x00, (byte)0x48,

            (byte)0x02,

            (byte)0x00, (byte)0x41, (byte)0x00, (byte)0x41, (byte)0x00, (byte)0x41,
            (byte)0x00, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,

            (byte)0x06,

            (byte)0x00, (byte)0x42, (byte)0x00, (byte)0x42, (byte)0x00, (byte)0x42,
            (byte)0x00, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,
            (byte)0x0D, (byte)0x0D, (byte)0x0D, (byte)0x0D,

            (byte)0x06
    };

    private static final byte[] gsmUcs2TestWithLanguageInBody = {
            (byte)0xC0, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x11, (byte)0x11, (byte)0x78,
            (byte)0x3C, (byte)0x00, (byte)0x41, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x55,
            (byte)0x00, (byte)0x43, (byte)0x00, (byte)0x53, (byte)0x00, (byte)0x32, (byte)0x00,
            (byte)0x20, (byte)0x00, (byte)0x6D, (byte)0x00, (byte)0x65, (byte)0x00, (byte)0x73,
            (byte)0x00, (byte)0x73, (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x67, (byte)0x00,
            (byte)0x65, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x63, (byte)0x00, (byte)0x6F,
            (byte)0x00, (byte)0x6E, (byte)0x00, (byte)0x74, (byte)0x00, (byte)0x61, (byte)0x00,
            (byte)0x69, (byte)0x00, (byte)0x6E, (byte)0x00, (byte)0x69, (byte)0x00, (byte)0x6E,
            (byte)0x00, (byte)0x67, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x61, (byte)0x00,
            (byte)0x20, (byte)0x04, (byte)0x34, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x63,
            (byte)0x00, (byte)0x68, (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x72, (byte)0x00,
            (byte)0x61, (byte)0x00, (byte)0x63, (byte)0x00, (byte)0x74, (byte)0x00, (byte)0x65,
            (byte)0x00, (byte)0x72, (byte)0x00, (byte)0x0D
    };

    private static final byte[] gsmUcs2TestWithLanguageInBodyUmts = {
            (byte)0x01, (byte)0x00, (byte)0x01, (byte)0xC0, (byte)0x00, (byte)0x11,

            (byte)0x01,

            (byte)0x78, (byte)0x3C, (byte)0x00, (byte)0x41, (byte)0x00, (byte)0x20,
            (byte)0x00, (byte)0x55, (byte)0x00, (byte)0x43, (byte)0x00, (byte)0x53,
            (byte)0x00, (byte)0x32, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x6D,
            (byte)0x00, (byte)0x65, (byte)0x00, (byte)0x73, (byte)0x00, (byte)0x73,
            (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x67, (byte)0x00, (byte)0x65,
            (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x63, (byte)0x00, (byte)0x6F,
            (byte)0x00, (byte)0x6E, (byte)0x00, (byte)0x74, (byte)0x00, (byte)0x61,
            (byte)0x00, (byte)0x69, (byte)0x00, (byte)0x6E, (byte)0x00, (byte)0x69,
            (byte)0x00, (byte)0x6E, (byte)0x00, (byte)0x67, (byte)0x00, (byte)0x20,
            (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x20, (byte)0x04, (byte)0x34,
            (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x63, (byte)0x00, (byte)0x68,
            (byte)0x00, (byte)0x61, (byte)0x00, (byte)0x72, (byte)0x00, (byte)0x61,
            (byte)0x00, (byte)0x63, (byte)0x00, (byte)0x74, (byte)0x00, (byte)0x65,
            (byte)0x00, (byte)0x72, (byte)0x00, (byte)0x0D,

            (byte)0x50
    };

    private static final SmsCbLocation sEmptyLocation = new SmsCbLocation();

    private static final String GEO_DATA_1 =
            "polygon|41.30807876586914,-72.93763160705566|41.17422580718994,-71.57730102539062|41"
                    + ".82087421417236,-71.42031669616699|42.25908279418945,-71.81977272033691|42"
                    + ".14582920074463,-72.62134552001953|41.84387683868408,-73.34704399108887;"
                    + "polygon|43.16202163696289,-77.61368751525879|42.88736343383789,-78"
                    + ".87531280517578|42.12389945983887,-80.10406494140625|40.79678535461426,-77"
                    + ".8630256652832|41.40515327453613,-75.65717697143555|43.048553466796875,-76"
                    + ".15413665771484;"
                    + "circle|40.941925048828125,-74.83792304992676|37484.375;circle|39"
                    + ".74312782287598,-75.55057525634766|80000.0;circle|47.60620594024658,-122"
                    + ".33207702636719|100000.0";
    private static final String GEO_DATA_2 =
            "polygon|37.42119312286377,-122.0852279663086|37.418532371520996,-122"
                    + ".08539962768555|37.41840362548828,-122.07690238952637|37.421536445617676,"
                    + "-122.0775032043457|37.42119312286377,-122.0852279663086";
    private static final String GEO_DATA_3 =
            "polygon|38.685007095336914,-121.98969841003418|38.68191719055176,-121"
                    + ".96806907653809|38.682003021240234,-121.96721076965332|38.68178844451904,"
                    + "-121.96686744689941|38.681702613830566,-121.96592330932617|38"
                    + ".68161678314209,-121.96592330932617|38.67959976196289,-121"
                    + ".95193290710449|38.66359233856201,-121.95158958435059|38.66359233856201,"
                    + "-121.95279121398926|38.66191864013672,-121.95236206054688|38"
                    + ".660287857055664,-121.95236206054688|38.66041660308838,-121"
                    + ".97090148925781|38.6638069152832,-121.97081565856934|38.66389274597168,"
                    + "-121.9716739654541|38.665995597839355,-121.98248863220215|38"
                    + ".677711486816406,-122.04257011413574|38.69230270385742,-122"
                    + ".03913688659668|38.685007095336914,-121.98969841003418";
    private static final String GEO_DATA_4 =
            "polygon|28.510007,77.079013|28.510695,77.079685|28.511569,77.080505|28.512118,77"
                    + ".08102|28.512179,77.081077|28.513,77.08186|28.51314,77.081992|28.513203,77"
                    + ".082051|28.51252,77.082997|28.5122,77.083441|28.512176,77.083475|28"
                    + ".511861,77.083899|28.511115,77.084905|28.510635,77.085553|28.510203,77"
                    + ".086135|28.510043,77.086358|28.509365,77.087301|28.50912,77.087094|28"
                    + ".508713,77.086709|28.508629,77.086629|28.508363,77.086377|28.5077,77"
                    + ".085749|28.507604,77.08566|28.507512,77.085574|28.507165,77.085251|28"
                    + ".507046,77.085135|28.506883,77.084977|28.506328,77.084439|28.506148,77"
                    + ".084263|28.506491,77.083798|28.506588,77.083666|28.506813,77.083361|28"
                    + ".5074,77.082565|28.508452,77.081137|28.508607,77.080926|28.508629,77"
                    + ".080896|28.509306,77.07997|28.509394,77.079851|28.509923,77.079124|28"
                    + ".510007,77.079013";
    public static final String GEO_DATA_5 =
            "polygon|38.7916,-77.1199|38.9960,-77.1199|38.9960,-76.9090|38.7916,-76.9090|38.7916,"
                    + "-77.1199";
    public static final String GEO_DATA_6 = "circle|29.76,-95.37|80000.0";
    private static final String GEO_DATA_7 =
            "polygon|37.00426,-114.81651|37.00426,-109.045223|32.5,-109.045|31.332177,-109"
                    + ".045223|31.332177,-111.0|34.0,-114.81651|37.00426,-114.81651;"
                    + "polygon|49.001109,-116.049153|49.001109,-104.039694|46.0,-104.039|44"
                    + ".357915,-104.039694|44.357915,-112.0|46.0,-116.049153|48.0,-116.049153|49"
                    + ".001109,-116.049153";

    public static final List<String> TEST_GEO_DATA_LIST = Arrays.asList(
            GEO_DATA_1, GEO_DATA_2, GEO_DATA_3, GEO_DATA_4, GEO_DATA_5, GEO_DATA_6, GEO_DATA_7
    );

    private static final List<Geometry> CIRCLE_GEOMETRIES = getSampleGeometries(true);
    private static final List<Geometry> POLYGON_GEOMETRIES = getSampleGeometries(false);

    private static List<Geometry> getSampleGeometries(boolean isCircle) {
        List<Geometry> geometries = new ArrayList<>();
        try {
            if (isCircle) {
                geometries.addAll(
                        CbGeoUtils.parseGeometriesFromString(GEO_DATA_6));
            } else {
                geometries.addAll(CbGeoUtils.parseGeometriesFromString(GEO_DATA_5));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse sample geometries: " + e);
        }
        return geometries;
    }

    private static SmsCbMessage createFromPdu(Context context, byte[] pdu, int serialNumber,
                                              int category) {
        byte[][] pdus = new byte[1][];
        pdus[0] = pdu;
        return createFromPdus(context, pdus, serialNumber, category);
    }

    private static SmsCbMessage createFromPdus(Context context, byte[][] pdus, int serialNumber,
                                               int category) {
        try {
            for (byte[] pdu : pdus) {
                if (pdu.length <= 88) {
                    // GSM format cell broadcast
                    Log.d(TAG, "setting GSM serial number to " + serialNumber);
                    pdu[0] = (byte) ((serialNumber >>> 8) & 0xff);
                    pdu[1] = (byte) (serialNumber & 0xff);
                    if (category != 0) {
                        Log.d(TAG, "setting GSM message identifier to " + category);
                        pdu[2] = (byte) ((category >>> 8) & 0xff);
                        pdu[3] = (byte) (category & 0xff);
                    }
                } else {
                    // UMTS format cell broadcast
                    Log.d(TAG, "setting UMTS serial number to " + serialNumber);
                    pdu[3] = (byte) ((serialNumber >>> 8) & 0xff);
                    pdu[4] = (byte) (serialNumber & 0xff);
                    if (category != 0) {
                        Log.d(TAG, "setting UMTS message identifier to " + category);
                        pdu[1] = (byte) ((category >>> 8) & 0xff);
                        pdu[2] = (byte) (category & 0xff);
                    }
                }
            }
            return GsmSmsCbMessage.createSmsCbMessage(context, new SmsCbHeader(pdus[0]),
                    sEmptyLocation, pdus, 0 /* slotIndex */);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void sendBroadcast(Context context, int serialNumber, int category,
                                      byte[] pdu) {
        Intent intent = new Intent(Intents.SMS_CB_RECEIVED_ACTION);
        intent.putExtra("message", createFromPdu(context, pdu, serialNumber, category));
        intent.setPackage(CellBroadcastUtils.getDefaultCellBroadcastReceiverPackageName(context));
        context.sendOrderedBroadcastAsUser(intent, UserHandle.ALL, Manifest.permission.RECEIVE_SMS,
                AppOpsManager.OP_RECEIVE_SMS, null, null, Activity.RESULT_OK, null, null);
    }

    public static void testSendMessage7bit(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsm7BitTest);
    }

    public static void testSendMessage7bitUmts(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsm7BitTestUmts);
    }

    public static void testSendMessage7bitNoPadding(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsm7BitTestNoPadding);
    }

    public static void testSendMessage7bitNoPaddingUmts(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsm7BitTestNoPaddingUmts);
    }

    public static void testSendMessage7bitMultipageGsm(Context context, int serialNumber,
            int category) {
        Intent intent = new Intent(Intents.SMS_CB_RECEIVED_ACTION);
        byte[][] pdus = new byte[2][];
        pdus[0] = gsm7BitTestMultipage1;
        pdus[1] = gsm7BitTestMultipage2;
        intent.putExtra("message", createFromPdus(context, pdus, serialNumber, category));
        intent.setPackage(CellBroadcastUtils.getDefaultCellBroadcastReceiverPackageName(context));
        context.sendOrderedBroadcastAsUser(intent, UserHandle.ALL, Manifest.permission.RECEIVE_SMS,
                AppOpsManager.OP_RECEIVE_SMS, null, null, Activity.RESULT_OK, null, null);
    }

    public static void testSendMessage7bitMultipageUmts(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsm7BitTestMultipageUmts);
    }

    public static void testSendMessage7bitWithLanguage(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsm7BitTestWithLanguage);
    }

    public static void testSendMessage7bitWithLanguageInBody(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsm7BitTestWithLanguageInBody);
    }

    public static void testSendMessage7bitWithLanguageInBodyUmts(Context context,
            int serialNumber, int category) {
        sendBroadcast(context, serialNumber, category, gsm7BitTestWithLanguageInBodyUmts);
    }

    public static void testSendMessageUcs2(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsmUcs2Test);
    }

    public static void testSendMessageUcs2Umts(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsmUcs2TestUmts);
    }

    public static void testSendMessageUcs2MultipageUmts(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsmUcs2TestMultipageUmts);
    }

    public static void testSendMessageUcs2WithLanguageInBody(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsmUcs2TestWithLanguageInBody);
    }

    public static void testSendMessageUcs2WithLanguageUmts(Context context, int serialNumber,
            int category) {
        sendBroadcast(context, serialNumber, category, gsmUcs2TestWithLanguageInBodyUmts);
    }

    public static void testSendEtwsMessageEarthquake(Context context, int serialNumber) {
        sendBroadcast(context, serialNumber, SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                etwsMessageNormal);
    }

    public static void testSendEtwsMessageTsunami(Context context, int serialNumber) {
        sendBroadcast(context, serialNumber, SmsCbConstants.MESSAGE_ID_ETWS_TSUNAMI_WARNING,
                etwsMessageNormal);
    }

    public static void testSendEtwsMessageEarthquakeTsunami(Context context, int serialNumber) {
        sendBroadcast(context, serialNumber,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING, etwsMessageNormal);
    }

    public static void testSendEtwsMessageOther(Context context, int serialNumber) {
        sendBroadcast(context, serialNumber, SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                etwsMessageNormal);
    }

    public static void testSendEtwsMessageCancel(Context context, int serialNumber) {
        sendBroadcast(context, serialNumber, 0, etwsMessageCancel);
    }

    public static void testSendEtwsMessageTest(Context context, int serialNumber) {
        sendBroadcast(context, serialNumber, SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                etwsMessageTest);
    }

    /**
     * Sends a CMAS test alert with predefined circle or polygon geo-fencing data.
     * @param context The context.
     * @param category The service category (channel ID).
     * @param serialNumber The message serial number.
     * @param isCircle True to send circle geometries, false to send polygon geometries.
     */
    public static void testSendCmasAlertWithGeo(Context context, int category, int serialNumber,
            boolean isCircle) {
        List<Geometry> geometries = isCircle ? CIRCLE_GEOMETRIES : POLYGON_GEOMETRIES;
        Log.d(TAG,
                "testSendCmasAlertWithGeo: category=" + category + ", serialNumber=" + serialNumber
                        + ", isCircle=" + isCircle + ", geometries=" + geometries);

        SendGsmCmasMessages.testSendCmasAlertWithServiceCategory(
                context,
                category,
                serialNumber,
                "Test message with geo-info",
                "en",
                false,
                geometries
        );
    }
}
