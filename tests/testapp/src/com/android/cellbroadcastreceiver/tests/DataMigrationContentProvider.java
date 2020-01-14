package com.android.cellbroadcastreceiver.tests;

import android.annotation.NonNull;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;

import java.util.Arrays;

/**
 * This is a sample content provider for OEM data migration, including
 * both sharedPreference and message history migration.
 * authority: content://cellbroadcast-legacy
 */
public class DataMigrationContentProvider extends ContentProvider {
    private static final String TAG = DataMigrationContentProvider.class.getSimpleName();
    /** URI matcher for ContentProvider queries. */
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    /** The database for this content provider. */
    private SQLiteOpenHelper mOpenHelper;

    /** Authority string for content URIs. */
    static final String CB_AUTHORITY = "cellbroadcast-legacy";

    /** Content URI for notifying observers. */
    static final Uri CONTENT_URI = Uri.parse("content://cellbroadcast-legacy/");

    /** URI matcher type to get all cell broadcasts. */
    private static final int CB_ALL = 0;

    static {
        sUriMatcher.addURI(CB_AUTHORITY, null, CB_ALL);
    }

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate");
        mOpenHelper = new CellBroadcastMigrationDatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projectionIn, String selection,
            String[] selectionArgs, String sortOrder) {
        Log.d(TAG, "query:"
                + " uri=" + uri
                + " values=" + Arrays.toString(projectionIn)
                + " selection=" + selection
                + " selectionArgs=" + Arrays.toString(selectionArgs));

        final int match = sUriMatcher.match(uri);
        if (match == CB_ALL) {
            SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            qb.setTables(CellBroadcastMigrationDatabaseHelper.TABLE_NAME);

            SQLiteDatabase db = mOpenHelper.getReadableDatabase();
            Cursor c = qb.query(db, projectionIn, selection, selectionArgs, null, null, sortOrder);
            Log.d(TAG, "query from legacy cellbroadcast, returned " + c.getCount() + " messges");
            return c;
        } else {
            Log.e(TAG, "unsupported URI: " + uri);
        }

        return null;
    }

    @Override
    public Bundle call(String method, String name, Bundle args) {
        Log.d(TAG, "call:"
                + " method=" + method
                + " name=" + name
                + " args=" + args);

         if (Telephony.CellBroadcasts.CALL_METHOD_GET_PREFERENCE.equals(method)) {
                switch (name) {
                    case  Telephony.CellBroadcasts.Preference.ENABLE_AREA_UPDATE_INFO_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_AREA_UPDATE_INFO_PREF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_CMAS_AMBER_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_CMAS_AMBER_PREF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_TEST_ALERT_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_TEST_ALERT_PREF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_STATE_LOCAL_TEST_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_STATE_LOCAL_TEST_PREF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_PUBLIC_SAFETY_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_PUBLIC_SAFETY_PREF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_CMAS_EXTREME_THREAT_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_CMAS_EXTREME_THREAT_PREF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_CMAS_PRESIDENTIAL_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_CMAS_PRESIDENTIAL_PREF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_FULL_VOLUME_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_FULL_VOLUME_PREF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_EMERGENCY_PERF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_EMERGENCY_PERF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_CMAS_SEVERE_THREAT_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_CMAS_SEVERE_THREAT_PREF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_ALERT_VIBRATION_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_ALERT_VIBRATION_PREF, false);
                    case Telephony.CellBroadcasts.Preference.ENABLE_CMAS_IN_SECOND_LANGUAGE_PREF:
                        return packageValueForCallResult(Telephony.CellBroadcasts.Preference
                                .ENABLE_CMAS_IN_SECOND_LANGUAGE_PREF, false);
                    default:
                        Log.e(TAG, "unsupported preference name" + name);
            }
        }
        Log.e(TAG, "unsuppprted call method: " + method);
        return null;
    }

    private static @NonNull Bundle packageValueForCallResult(String pref, Boolean val) {
        Bundle result = new Bundle();
        result.putBoolean(pref, val);
        return result;
    }


    @Override
    public String getType(Uri uri) {
        Log.d(TAG, "getType");
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("update not supported");
    }

    // create in-memory database
    private static class CellBroadcastMigrationDatabaseHelper extends SQLiteOpenHelper {

        private static final String TAG =
                CellBroadcastMigrationDatabaseHelper.class.getSimpleName();
        private static final String TABLE_NAME = "broadcasts_legacy";

        CellBroadcastMigrationDatabaseHelper(Context context) {
            super(null,      // no context is needed for in-memory db
                    null,      // db file name is null for in-memory db
                    null,      // CursorFactory is null by default
                    1);        // db version is no-op for tests
            Log.d(TAG, "creating in-memory database");
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(TAG, "onCreate creating the cellbroadcast legacy table");
            db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + Telephony.CellBroadcasts._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + Telephony.CellBroadcasts.SLOT_INDEX + " INTEGER DEFAULT 0,"
                    + Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE + " INTEGER,"
                    + Telephony.CellBroadcasts.PLMN + " TEXT,"
                    + Telephony.CellBroadcasts.LAC + " INTEGER,"
                    + Telephony.CellBroadcasts.CID + " INTEGER,"
                    + Telephony.CellBroadcasts.SERIAL_NUMBER + " INTEGER,"
                    + Telephony.CellBroadcasts.SERVICE_CATEGORY + " INTEGER,"
                    + Telephony.CellBroadcasts.LANGUAGE_CODE + " TEXT,"
                    + Telephony.CellBroadcasts.MESSAGE_BODY + " TEXT,"
                    + Telephony.CellBroadcasts.DELIVERY_TIME + " INTEGER,"
                    + Telephony.CellBroadcasts.MESSAGE_READ + " INTEGER,"
                    + Telephony.CellBroadcasts.MESSAGE_FORMAT + " INTEGER,"
                    + Telephony.CellBroadcasts.MESSAGE_PRIORITY + " INTEGER,"
                    + Telephony.CellBroadcasts.ETWS_WARNING_TYPE + " INTEGER,"
                    + Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS + " INTEGER,"
                    + Telephony.CellBroadcasts.CMAS_CATEGORY + " INTEGER,"
                    + Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE + " INTEGER,"
                    + Telephony.CellBroadcasts.CMAS_SEVERITY + " INTEGER,"
                    + Telephony.CellBroadcasts.CMAS_URGENCY + " INTEGER,"
                    + Telephony.CellBroadcasts.CMAS_CERTAINTY + " INTEGER);");

            db.execSQL("CREATE INDEX IF NOT EXISTS deliveryTimeIndex ON " + TABLE_NAME
                    + " (" + Telephony.CellBroadcasts.DELIVERY_TIME + ");");
            // insert a fake message
            ContentValues contentVal = new ContentValues();
            contentVal.put(Telephony.CellBroadcasts.SLOT_INDEX, 0);
            contentVal.put(Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE, 1);
            contentVal.put(Telephony.CellBroadcasts.PLMN, "311480");
            contentVal.put(Telephony.CellBroadcasts.SERIAL_NUMBER, 19724);
            contentVal.put(Telephony.CellBroadcasts.SERVICE_CATEGORY, 4379);
            contentVal.put(Telephony.CellBroadcasts.LANGUAGE_CODE, "en");
            contentVal.put(Telephony.CellBroadcasts.MESSAGE_BODY, "migration test");
            contentVal.put(Telephony.CellBroadcasts.MESSAGE_PRIORITY, 3);
            contentVal.put(Telephony.CellBroadcasts.MESSAGE_FORMAT, 1);
            if (db.insertWithOnConflict(TABLE_NAME, null, contentVal,
                    SQLiteDatabase.CONFLICT_IGNORE) > 0) {
                Log.d(TAG, "insertion succeed");
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d(TAG, "onUpgrade doing nothing");
            return;
        }
    }
}

