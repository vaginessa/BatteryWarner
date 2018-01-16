package com.laudien.p1xelfehler.batterywarner.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

/**
 * The Model for the charging graph databases. Only the DatabaseController should communicate to
 * instances of this class.
 */
public class DatabaseModel extends SQLiteOpenHelper implements DatabaseContract.Model {
    /**
     * The name of the database.
     */
    static final String DATABASE_NAME = "ChargeCurveDB";
    private static final int DATABASE_VERSION = 5; // if the version is changed, a new database will be created!
    private static DatabaseModel instance;
    private HashMap<String, SQLiteDatabase> openedDatabases = new HashMap<>();
    private HashSet<DatabaseContract.DatabaseListener> listeners = new HashSet<>();

    DatabaseModel(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @NonNull
    public static DatabaseModel getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new DatabaseModel(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(
                String.format("CREATE TABLE %s (%s TEXT,%s INTEGER,%s INTEGER, %s INTEGER, %s INTEGER);",
                        DatabaseContract.TABLE_NAME,
                        DatabaseContract.TABLE_COLUMN_TIME,
                        DatabaseContract.TABLE_COLUMN_BATTERY_LEVEL,
                        DatabaseContract.TABLE_COLUMN_TEMPERATURE,
                        DatabaseContract.TABLE_COLUMN_VOLTAGE,
                        DatabaseContract.TABLE_COLUMN_CURRENT
                )
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        Log.d(getClass().getSimpleName(), "onUpgrade() -> oldVersion = " + oldVersion + ", newVersion = " + newVersion);
        if (oldVersion < 5) {
            Log.d(getClass().getSimpleName(), "Upgrading file: " + sqLiteDatabase.getPath());
            String statement = "ALTER TABLE %s ADD COLUMN %s INTEGER DEFAULT 0";
            try {
                sqLiteDatabase.execSQL(String.format(
                        statement, DatabaseContract.TABLE_NAME, DatabaseContract.TABLE_COLUMN_VOLTAGE));
                sqLiteDatabase.execSQL(String.format(
                        statement, DatabaseContract.TABLE_NAME, DatabaseContract.TABLE_COLUMN_CURRENT));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        try {
            return super.getReadableDatabase();
        } catch (SQLiteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void readData(boolean useFahrenheit, boolean reverseCurrent, @NonNull DatabaseContract.DataReceiver dataReceiver) {
        new ReadDataTask(null, useFahrenheit, reverseCurrent, dataReceiver).execute();
    }

    @Override
    public void readData(File databaseFile, boolean useFahrenheit, boolean reverseCurrent, @NonNull DatabaseContract.DataReceiver dataReceiver) {
        new ReadDataTask(databaseFile, useFahrenheit, reverseCurrent, dataReceiver).execute();
    }

    @Override
    public Cursor getCursor() {
        return getCursor(getReadableDatabase());
    }

    @Override
    @Nullable
    public Cursor getCursor(@NonNull File databaseFile) {
        SQLiteDatabase database = getReadableDatabase(databaseFile);
        if (database == null) {
            return null;
        }
        return getCursor(database);
    }

    @Override
    @Nullable
    public Cursor getCursor(@NonNull SQLiteDatabase database) {
        String[] columns = {
                DatabaseContract.TABLE_COLUMN_TIME,
                DatabaseContract.TABLE_COLUMN_BATTERY_LEVEL,
                DatabaseContract.TABLE_COLUMN_TEMPERATURE,
                DatabaseContract.TABLE_COLUMN_VOLTAGE,
                DatabaseContract.TABLE_COLUMN_CURRENT
        };
        return database.query(
                DatabaseContract.TABLE_NAME,
                columns,
                null,
                null,
                null,
                null,
                "length(" + DatabaseContract.TABLE_COLUMN_TIME + "), " + DatabaseContract.TABLE_COLUMN_TIME
        );
    }

    @Override
    @Nullable
    public SQLiteDatabase getReadableDatabase(@NonNull File databaseFile) {
        if (openedDatabases.containsKey(databaseFile.getPath())) {
            return openedDatabases.get(databaseFile.getPath());
        }
        try {
            SQLiteDatabase database = SQLiteDatabase.openDatabase(
                    databaseFile.getPath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );
            // upgrade database if necessary
            if (database.getVersion() < DATABASE_VERSION) {
                long lastModified = databaseFile.lastModified();
                database = SQLiteDatabase.openDatabase(
                        databaseFile.getPath(),
                        null,
                        SQLiteDatabase.OPEN_READWRITE
                );
                onUpgrade(database, database.getVersion(), DATABASE_VERSION);
                database.setVersion(DATABASE_VERSION);
                databaseFile.setLastModified(lastModified); // keep last modified date the same
                database = SQLiteDatabase.openDatabase(
                        databaseFile.getPath(),
                        null,
                        SQLiteDatabase.OPEN_READONLY
                );
            }
            openedDatabases.put(databaseFile.getPath(), database);
            return database;
        } catch (SQLiteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public SQLiteDatabase getWritableDatabase(@NonNull File databaseFile) {
        if (openedDatabases.containsKey(databaseFile.getPath())) {
            return openedDatabases.get(databaseFile.getPath());
        }
        try {
            SQLiteDatabase database = SQLiteDatabase.openDatabase(
                    databaseFile.getPath(),
                    null,
                    SQLiteDatabase.OPEN_READWRITE
            );
            openedDatabases.put(databaseFile.getPath(), database);
            return database;
        } catch (SQLiteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void addValue(@NonNull DatabaseValue value) {
        new AddValueTask().execute(value);
    }

    @Override
    public long getCreationTime() {
        long creationTime = 0;
        Cursor cursor = getCursor();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                creationTime = cursor.getLong(cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_TIME));
            }
        }
        return creationTime;
    }

    @Override
    public void resetTable() {
        new ResetTableTask().execute();
    }

    @Override
    public void closeAllExternalFiles() {
        for (SQLiteDatabase database : openedDatabases.values()) {
            if (database.isOpen()) {
                database.close();
            }
        }
        openedDatabases.clear();
    }

    @Override
    public void notifyValueAdded(DatabaseValue value, long totalNumberOfRows) {
        for (DatabaseContract.DatabaseListener listener : listeners) {
            listener.onValueAdded(value, totalNumberOfRows);
        }
    }

    @Override
    public void notifyTableReset() {
        for (DatabaseContract.DatabaseListener listener : listeners) {
            listener.onTableReset();
        }
    }

    @Override
    public void registerDatabaseListener(DatabaseContract.DatabaseListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregisterDatabaseListener(DatabaseContract.DatabaseListener listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            close();
        }
    }

    @Nullable
    private Data readData(Cursor cursor, boolean useFahrenheit, boolean reverseCurrent) {
        if (cursor == null || cursor.isClosed() || cursor.getCount() <= 0) {
            return null;
        }
        DatabaseValue[] databaseValues = new DatabaseValue[cursor.getCount()];
        DatabaseValue valueWithHighestTemp = null;
        DatabaseValue valueWithLowestTemp = null;
        DatabaseValue valueWithHighestBatteryLevel = null;
        DatabaseValue valueWithHighestCurrent = null;
        DatabaseValue valueWithLowestCurrent = null;
        DatabaseValue valueWithHighestVoltage = null;
        DatabaseValue valueWithLowestVoltage = null;
        cursor.moveToPosition(0);
        long startTime = cursor.getLong(cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_TIME));
        for (int i = 0; i < cursor.getCount(); i++) {
            cursor.moveToPosition(i);
            int batteryLevel = cursor.getInt(cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_BATTERY_LEVEL));
            int temperature = cursor.getInt(cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_TEMPERATURE));
            int voltage = cursor.getInt(cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_VOLTAGE));
            int current = cursor.getInt(cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_CURRENT));
            long time = cursor.getLong(cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_TIME));
            databaseValues[i] = new DatabaseValue(batteryLevel, temperature, voltage, current, time, startTime);
            if (valueWithHighestTemp == null || databaseValues[i].getTemperature() > valueWithHighestTemp.getTemperature()) {
                valueWithHighestTemp = databaseValues[i];
            }
            if (valueWithLowestTemp == null || databaseValues[i].getTemperature() < valueWithLowestTemp.getTemperature()) {
                valueWithLowestTemp = databaseValues[i];
            }
            if (valueWithHighestBatteryLevel == null || databaseValues[i].getBatteryLevel() > valueWithHighestBatteryLevel.getBatteryLevel()) {
                valueWithHighestBatteryLevel = databaseValues[i];
            }
            if (valueWithHighestCurrent == null || databaseValues[i].getCurrentInMilliAmperes(reverseCurrent) > valueWithHighestCurrent.getCurrentInMilliAmperes(reverseCurrent)) {
                valueWithHighestCurrent = databaseValues[i];
            }
            if (valueWithLowestCurrent == null || databaseValues[i].getCurrentInMilliAmperes(reverseCurrent) < valueWithLowestCurrent.getCurrentInMilliAmperes(reverseCurrent)) {
                valueWithLowestCurrent = databaseValues[i];
            }
            if (valueWithHighestVoltage == null || databaseValues[i].getVoltage() > valueWithHighestVoltage.getVoltage()) {
                valueWithHighestVoltage = databaseValues[i];
            }
            if (valueWithLowestVoltage == null || databaseValues[i].getVoltage() < valueWithLowestVoltage.getVoltage()) {
                valueWithLowestVoltage = databaseValues[i];
            }
        }
        long timeToMaxBatteryLvl = valueWithHighestBatteryLevel.getUtcTimeInMillis() - databaseValues[0].getUtcTimeInMillis();
        double timeInHours = (double) timeToMaxBatteryLvl / (double) (1000 * 60 * 60);
        int percentageDiff = valueWithHighestBatteryLevel.getBatteryLevel() - databaseValues[0].getBatteryLevel();

        GraphInfo graphInfo = new GraphInfo(
                startTime,
                databaseValues[databaseValues.length - 1].getUtcTimeInMillis(),
                databaseValues[databaseValues.length - 1].getTimeFromStartInMinutes(),
                useFahrenheit ? valueWithHighestTemp.getTemperatureInFahrenheit() : valueWithHighestTemp.getTemperatureInCelsius(),
                useFahrenheit ? valueWithLowestTemp.getTemperatureInFahrenheit() : valueWithLowestTemp.getTemperatureInCelsius(),
                percentageDiff / timeInHours,
                valueWithLowestCurrent.getCurrentInMilliAmperes(reverseCurrent),
                valueWithHighestCurrent.getCurrentInMilliAmperes(reverseCurrent),
                valueWithLowestVoltage.getVoltageInVolts(),
                valueWithHighestVoltage.getVoltageInVolts(),
                valueWithHighestBatteryLevel.getBatteryLevel(),
                databaseValues[0].getBatteryLevel(),
                useFahrenheit,
                reverseCurrent
        );

        return new Data(databaseValues, graphInfo);
    }

    private class ReadDataTask extends AsyncTask<Void, Void, Data> {
        private DatabaseContract.DataReceiver dataReceiver;
        private boolean useFahrenheit, reverseCurrent;
        private File file;

        private ReadDataTask(@Nullable File file, boolean useFahrenheit, boolean reverseCurrent, @NonNull DatabaseContract.DataReceiver dataReceiver) {
            this.file = file;
            this.useFahrenheit = useFahrenheit;
            this.reverseCurrent = reverseCurrent;
            this.dataReceiver = dataReceiver;
        }

        @Override
        protected Data doInBackground(Void... voids) {
            Cursor cursor;
            if (file == null) {
                cursor = getCursor();
            } else {
                cursor = getCursor(file);
            }
            return readData(cursor, useFahrenheit, reverseCurrent);
        }

        @Override
        protected void onPostExecute(Data data) {
            super.onPostExecute(data);
            if (data != null) {
                dataReceiver.onDataRead(data);
            }
        }
    }

    private class AddValueTask extends AsyncTask<DatabaseValue, Void, Long> {
        private DatabaseValue value;

        @Override
        protected Long doInBackground(DatabaseValue... databaseValues) {
            value = databaseValues[0];
            SQLiteDatabase database = getWritableDatabase();
            long totalNumberOfRows = 0;
            if (database != null) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(DatabaseContract.TABLE_COLUMN_TIME, value.getUtcTimeInMillis());
                contentValues.put(DatabaseContract.TABLE_COLUMN_BATTERY_LEVEL, value.getBatteryLevel());
                contentValues.put(DatabaseContract.TABLE_COLUMN_TEMPERATURE, value.getTemperature());
                contentValues.put(DatabaseContract.TABLE_COLUMN_VOLTAGE, value.getVoltage());
                contentValues.put(DatabaseContract.TABLE_COLUMN_CURRENT, value.getCurrent());
                database.insert(DatabaseContract.TABLE_NAME, null, contentValues);
                totalNumberOfRows = DatabaseUtils.queryNumEntries(database, DatabaseContract.TABLE_NAME);
                Log.d("DatabaseModel", "value added: " + value);
            }
            return totalNumberOfRows;
        }

        @Override
        protected void onPostExecute(Long totalNumberOfRows) {
            super.onPostExecute(totalNumberOfRows);
            notifyValueAdded(value, totalNumberOfRows);
        }
    }

    private class ResetTableTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            SQLiteDatabase database = getWritableDatabase();
            if (database == null) {
                return false;
            }
            database.execSQL("DELETE FROM " + DatabaseContract.TABLE_NAME);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (success != null && success) {
                Log.d("DatabaseModel", "The graph has been reset successfully!");
                notifyTableReset();
            } else {
                Log.d("DatabaseModel", "Graph reset failed!");
            }
        }
    }
}
