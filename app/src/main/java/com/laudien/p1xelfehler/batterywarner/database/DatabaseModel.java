package com.laudien.p1xelfehler.batterywarner.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.laudien.p1xelfehler.batterywarner.database.DatabaseUtils.GRAPH_INDEX_CURRENT;
import static com.laudien.p1xelfehler.batterywarner.database.DatabaseUtils.NUMBER_OF_GRAPHS;

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
    private final HashMap<String, SQLiteDatabase> openedDatabases = new HashMap<>();
    private final HashSet<DatabaseContract.DatabaseListener> listeners = new HashSet<>();

    private DatabaseModel(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @NonNull
    public static DatabaseModel getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new DatabaseModel(context);
        }
        return instance;
    }

    static boolean resetTableTask(@NonNull SQLiteDatabase writableDatabase) {
        if (!writableDatabase.isOpen()) {
            return false;
        }
        writableDatabase.execSQL("DELETE FROM " + DatabaseContract.TABLE_NAME);
        return true;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        if (SDK_INT >= LOLLIPOP) {
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
        } else {
            sqLiteDatabase.execSQL(
                    String.format("CREATE TABLE %s (%s TEXT,%s INTEGER,%s INTEGER, %s INTEGER);",
                            DatabaseContract.TABLE_NAME,
                            DatabaseContract.TABLE_COLUMN_TIME,
                            DatabaseContract.TABLE_COLUMN_BATTERY_LEVEL,
                            DatabaseContract.TABLE_COLUMN_TEMPERATURE,
                            DatabaseContract.TABLE_COLUMN_VOLTAGE
                    )
            );
        }
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
    @Nullable
    public SQLiteDatabase getReadableDatabase() {
        try {
            return super.getReadableDatabase();
        } catch (SQLiteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    @Nullable
    public SQLiteDatabase getWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    @Nullable
    public Data readData(@Nullable File databaseFile, boolean useFahrenheit, boolean reverseCurrent) {
        Cursor cursor = databaseFile != null ? getCursor(databaseFile) : getCursor();
        if (cursor == null) {
            return null;
        }
        int numberOfValues = cursor.getCount();
        if (cursor.isClosed() || numberOfValues <= 0) {
            return null;
        }
        LineGraphSeries<DataPoint>[] graphs = new LineGraphSeries[NUMBER_OF_GRAPHS];
        for (int i = 0; i < NUMBER_OF_GRAPHS; i++) {
            if (i == GRAPH_INDEX_CURRENT && SDK_INT < LOLLIPOP) {
                continue;
            }
            graphs[i] = new LineGraphSeries<>();
        }
        int firstBatteryLvl = 0;
        int maxBatteryLvl = 0;
        int minTemp = 0;
        int maxTemp = 0;
        int minVoltage = 0;
        int maxVoltage = 0;
        Integer minCurrent = null;
        Integer maxCurrent = null;
        long startTime = 0;
        long time = 0;
        long timeOfValueWithMaxBatteryLvl = 0;
        DatabaseValue lastValue = null;
        int cursorIndexBatteryLevel = cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_BATTERY_LEVEL);
        int cursorIndexTemperature = cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_TEMPERATURE);
        int cursorIndexVoltage = cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_VOLTAGE);
        int cursorIndexCurrent = cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_CURRENT);
        int cursorIndexTime = cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_TIME);

        for (int valueIndex = 0; valueIndex < cursor.getCount(); valueIndex++) {
            cursor.moveToPosition(valueIndex);
            int batteryLevel = cursor.getInt(cursorIndexBatteryLevel);
            int temperature = cursor.getInt(cursorIndexTemperature);
            int voltage = cursor.getInt(cursorIndexVoltage);
            Integer current = cursorIndexCurrent != -1 ? cursor.getInt(cursorIndexCurrent) : null;
            time = cursor.getLong(cursorIndexTime);
            if (valueIndex == 0) {
                firstBatteryLvl = batteryLevel;
                maxBatteryLvl = batteryLevel;
                minTemp = temperature;
                maxTemp = temperature;
                minVoltage = voltage;
                maxVoltage = voltage;
                minCurrent = current;
                maxCurrent = current;
                startTime = time;
            }

            DatabaseValue value = SDK_INT >= LOLLIPOP ?
                    new DatabaseValue(batteryLevel, temperature, voltage, current, time, startTime) :
                    new DatabaseValue(batteryLevel, temperature, voltage, time, startTime);
            DiffValue diffValue;
            if (lastValue != null) {
                diffValue = lastValue.diff(value);
                if (diffValue == null) {
                    continue;
                }
            } else {
                diffValue = SDK_INT >= LOLLIPOP ?
                        new DiffValue(batteryLevel, temperature, voltage, current) :
                        new DiffValue(batteryLevel, temperature, voltage);
            }
            DataPoint[] dataPoints = value.toDataPoints(useFahrenheit, reverseCurrent);
            for (int graphIndex = 0; graphIndex < NUMBER_OF_GRAPHS; graphIndex++) {
                if (graphs[graphIndex] == null || dataPoints[graphIndex] == null) {
                    continue;
                }
                if (diffValue.get(graphIndex) != null || valueIndex == numberOfValues - 1) {
                    graphs[graphIndex].appendData(dataPoints[graphIndex], false, valueIndex + 1);
                }
            }
            lastValue = value;

            if (valueIndex == 0)
                continue;
            if (temperature > maxTemp)
                maxTemp = temperature;
            if (temperature < minTemp)
                minTemp = temperature;
            if (current != null) {
                if (DatabaseValue.convertToMilliAmperes(current, reverseCurrent) > DatabaseValue.convertToMilliAmperes(maxCurrent, reverseCurrent))
                    maxCurrent = current;
                if (DatabaseValue.convertToMilliAmperes(current, reverseCurrent) < DatabaseValue.convertToMilliAmperes(minCurrent, reverseCurrent))
                    minCurrent = current;
            }
            if (voltage > maxVoltage)
                maxVoltage = voltage;
            if (voltage < minVoltage)
                minVoltage = voltage;
            if (batteryLevel > maxBatteryLvl) {
                maxBatteryLvl = batteryLevel;
                timeOfValueWithMaxBatteryLvl = time;
            }
        }

        double chargingSpeed = numberOfValues < 2 ? Double.NaN :
                3600000.0 * ((double) (maxBatteryLvl - firstBatteryLvl) / (double) (timeOfValueWithMaxBatteryLvl - startTime));

        GraphInfo graphInfo;
        if (SDK_INT >= LOLLIPOP && minCurrent != null && maxCurrent != null) {
            graphInfo = new GraphInfo(
                    startTime,
                    time,
                    lastValue.getTimeFromStartInMinutes(),
                    maxTemp,
                    minTemp,
                    chargingSpeed,
                    minCurrent,
                    maxCurrent,
                    DatabaseValue.convertToVolts(minVoltage),
                    DatabaseValue.convertToVolts(maxVoltage),
                    maxBatteryLvl,
                    firstBatteryLvl,
                    useFahrenheit,
                    reverseCurrent
            );
        } else {
            graphInfo = new GraphInfo(
                    startTime,
                    time,
                    lastValue.getTimeFromStartInMinutes(),
                    maxTemp,
                    minTemp,
                    chargingSpeed,
                    DatabaseValue.convertToVolts(minVoltage),
                    DatabaseValue.convertToVolts(maxVoltage),
                    maxBatteryLvl,
                    firstBatteryLvl,
                    useFahrenheit,
                    reverseCurrent
            );
        }
        return new Data(graphs, graphInfo);
    }

    @Override
    @Nullable
    public Cursor getCursor() {
        SQLiteDatabase database = getReadableDatabase();
        if (database == null) {
            return null;
        }
        return getCursor(database);
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
        String[] columns;
        if (SDK_INT >= LOLLIPOP) {
            columns = new String[]{
                    DatabaseContract.TABLE_COLUMN_TIME,
                    DatabaseContract.TABLE_COLUMN_BATTERY_LEVEL,
                    DatabaseContract.TABLE_COLUMN_TEMPERATURE,
                    DatabaseContract.TABLE_COLUMN_VOLTAGE,
                    DatabaseContract.TABLE_COLUMN_CURRENT
            };
        } else {
            columns = new String[]{
                    DatabaseContract.TABLE_COLUMN_TIME,
                    DatabaseContract.TABLE_COLUMN_BATTERY_LEVEL,
                    DatabaseContract.TABLE_COLUMN_TEMPERATURE,
                    DatabaseContract.TABLE_COLUMN_VOLTAGE
            };
        }
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
                //noinspection ResultOfMethodCallIgnored
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
    public void addValue(@NonNull DatabaseValue value, @Nullable DatabaseValue lastValue) {
        SQLiteDatabase database = getWritableDatabase();
        if (database == null || !database.isOpen()) {
            return;
        }
        long totalNumberOfRows;
        if (lastValue != null && value.equals(lastValue)) {
            return;
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put(DatabaseContract.TABLE_COLUMN_TIME, value.getUtcTimeInMillis());
        contentValues.put(DatabaseContract.TABLE_COLUMN_BATTERY_LEVEL, value.getBatteryLevel());
        contentValues.put(DatabaseContract.TABLE_COLUMN_TEMPERATURE, value.getTemperature());
        contentValues.put(DatabaseContract.TABLE_COLUMN_VOLTAGE, value.getVoltage());
        if (SDK_INT >= LOLLIPOP) {
            contentValues.put(DatabaseContract.TABLE_COLUMN_CURRENT, value.getCurrent());
        }
        totalNumberOfRows = database.insert(DatabaseContract.TABLE_NAME, null, contentValues);
        if (totalNumberOfRows == -1) {
            Log.d("DatabaseModel", "An error occured trying to insert value: " + value);
            return;
        }
        Log.d("DatabaseModel", "value added: " + value);
        for (DatabaseContract.DatabaseListener listener : listeners) {
            listener.onValueAdded(value, totalNumberOfRows);
        }
    }

    @Override
    public long getCreationTime() {
        Cursor cursor = getCursor();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndex(DatabaseContract.TABLE_COLUMN_TIME));
            }
        }
        return 0;
    }

    @Override
    public void resetTable() {
        SQLiteDatabase database = getWritableDatabase();
        if (database == null) {
            return;
        }
        new ResetTableTask(database, listeners).execute();
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

    private static class ResetTableTask extends AsyncTask<Void, Void, Boolean> {
        private final HashSet<DatabaseContract.DatabaseListener> listeners;
        private final SQLiteDatabase database;

        private ResetTableTask(@NonNull SQLiteDatabase writableDatabase, @NonNull HashSet<DatabaseContract.DatabaseListener> listeners) {
            this.database = writableDatabase;
            this.listeners = listeners;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            return resetTableTask(database);
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

        private void notifyTableReset() {
            for (DatabaseContract.DatabaseListener listener : listeners) {
                listener.onTableReset();
            }
        }
    }
}
