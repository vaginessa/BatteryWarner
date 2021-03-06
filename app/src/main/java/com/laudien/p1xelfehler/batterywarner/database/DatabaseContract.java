package com.laudien.p1xelfehler.batterywarner.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;

public class DatabaseContract {
    static final String TABLE_NAME = "ChargeCurve";
    static final String TABLE_COLUMN_TIME = "time";
    static final String TABLE_COLUMN_BATTERY_LEVEL = "percentage";
    static final String TABLE_COLUMN_TEMPERATURE = "temperature";
    static final String TABLE_COLUMN_VOLTAGE = "voltage";
    static final String TABLE_COLUMN_CURRENT = "current";
    static final String DATABASE_HISTORY_PATH = Environment.getExternalStorageDirectory() + "/BatteryWarner";

    public interface Model {

        Data readData(@Nullable File databaseFile, boolean useFahrenheit, boolean reverseCurrent);

        Cursor getCursor();

        Cursor getCursor(File databaseFile);

        Cursor getCursor(SQLiteDatabase database);

        SQLiteDatabase getReadableDatabase(File databaseFile);

        void addValue(@NonNull DatabaseValue value, @Nullable DatabaseValue lastValue);

        long getCreationTime();

        void resetTable();

        void closeAllExternalFiles();

        void registerDatabaseListener(DatabaseListener listener);

        void unregisterDatabaseListener(DatabaseListener listener);
    }

    /**
     * A DatabaseListener that listens for database changes in the app directory database.
     */
    public interface DatabaseListener {
        /**
         * Called when a graph point was added to the app directory database.
         *
         * @param value             The value that has been added to the database.
         * @param totalNumberOfRows The total length (rows) of the database.
         */
        void onValueAdded(DatabaseValue value, long totalNumberOfRows);

        /**
         * Called when the app directory database has been cleared.
         */
        void onTableReset();
    }
}
