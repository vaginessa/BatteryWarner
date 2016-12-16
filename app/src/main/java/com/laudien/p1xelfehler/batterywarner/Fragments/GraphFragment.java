package com.laudien.p1xelfehler.batterywarner.Fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.laudien.p1xelfehler.batterywarner.Contract;
import com.laudien.p1xelfehler.batterywarner.Database.GraphChargeDbHelper;
import com.laudien.p1xelfehler.batterywarner.R;
import com.laudien.p1xelfehler.batterywarner.Receiver.BatteryAlarmReceiver;

public class GraphFragment extends Fragment implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "GraphFragment";
    private SharedPreferences sharedPreferences;
    private GraphView graph_chargeCurve;
    private LineGraphSeries<DataPoint> series_chargeCurve, series_speed, series_temp;
    private Viewport viewport_chargeCurve;
    private TextView textView_chargingTime;
    private double lastTime;
    private int graphCounter;
    private CheckBox checkBox_percentage, checkBox_speed, checkBox_temp;
    private Context context;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_graph, container, false);
        sharedPreferences = getContext().getSharedPreferences(Contract.SHARED_PREFS, Context.MODE_PRIVATE);
        graph_chargeCurve = (GraphView) view.findViewById(R.id.graph_chargeCurve);
        viewport_chargeCurve = graph_chargeCurve.getViewport();
        textView_chargingTime = (TextView) view.findViewById(R.id.textView_chargingTime);
        lastTime = 1;
        context = getContext();

        // checkBoxes
        checkBox_percentage = (CheckBox) view.findViewById(R.id.checkbox_percentage);
        checkBox_speed = (CheckBox) view.findViewById(R.id.checkBox_speed);
        checkBox_temp = (CheckBox) view.findViewById(R.id.checkBox_temp);

        checkBox_percentage.setOnCheckedChangeListener(this);
        checkBox_speed.setOnCheckedChangeListener(this);
        checkBox_temp.setOnCheckedChangeListener(this);

        // y bounds
        viewport_chargeCurve.setYAxisBoundsManual(true);
        viewport_chargeCurve.setMinY(0);
        viewport_chargeCurve.setMaxY(100);

        // x bounds
        viewport_chargeCurve.setXAxisBoundsManual(true);
        viewport_chargeCurve.setMinX(0);
        viewport_chargeCurve.setMaxX(1);

        graph_chargeCurve.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) { // X-axis (time)
                    if (value == 0) {
                        graphCounter = 1;
                        return "0";
                    }
                    if (graphCounter++ % 2 != 0)
                        return super.formatLabel(value, isValueX) + " min";
                    return "";
                } else // Y-axis (percent)
                    return super.formatLabel(value, false) + "%";
            }
        });

        // line graphs (= series)
        series_chargeCurve = new LineGraphSeries<>();
        series_chargeCurve.setDrawBackground(true);
        series_speed = new LineGraphSeries<>();
        series_speed.setColor(Color.RED);
        series_temp = new LineGraphSeries<>();
        series_temp.setColor(Color.GREEN);

        graph_chargeCurve.addSeries(series_chargeCurve);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadChargeCurve();
    }

    public void reloadChargeCurve() {
        // 1. if not pro -> return
        if (!Contract.IS_PRO) {
            textView_chargingTime.setTextSize(20);
            textView_chargingTime.setText(getString(R.string.not_pro) + " (Coming soon!)");
            return;
        }
        // 2. if disabled in settings -> return
        sharedPreferences = context.getSharedPreferences(Contract.SHARED_PREFS, Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean(Contract.PREF_GRAPH_ENABLED, true)) {
            textView_chargingTime.setTextSize(18);
            textView_chargingTime.setText(getString(R.string.disabled_in_settings));
        }
        boolean charging = BatteryAlarmReceiver.isCharging(getContext()); // get the charging state
        // 3. load graph
        long time;
        int percentage, lastPercentage = 0;
        double temperature;
        GraphChargeDbHelper dbHelper = new GraphChargeDbHelper(getContext());
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String[] columns = {
                GraphChargeDbHelper.TABLE_COLUMN_TIME,
                GraphChargeDbHelper.TABLE_COLUMN_PERCENTAGE,
                GraphChargeDbHelper.TABLE_COLUMN_TEMP};
        Cursor cursor = database.query(GraphChargeDbHelper.TABLE_NAME, columns, null, null, null, null,
                "length(" + GraphChargeDbHelper.TABLE_COLUMN_TIME + "), " + GraphChargeDbHelper.TABLE_COLUMN_TIME);
        if (cursor.moveToFirst()) { // if the cursor has data
            do {
                time = cursor.getLong(0);
                lastTime = getDoubleTime(time);
                percentage = cursor.getInt(1);
                temperature = (double) cursor.getInt(2)/10;
                Log.i(TAG, "Data read: time = " + lastTime + "; percentage = " + percentage + "; temp = " + temperature);
                try {
                    series_chargeCurve.appendData(new DataPoint(lastTime, percentage), false, 1000);
                    series_temp.appendData(new DataPoint(lastTime, temperature), false, 1000);
                    if(lastTime != 0.0) {
                        series_speed.appendData(new DataPoint(lastTime, (percentage - lastPercentage)/(lastTime)*10), false, 1000);
                    }
                    lastPercentage = percentage;
                } catch (Exception e) { // if x has a lower value than the values on the graph -> reset graph
                    series_chargeCurve.resetData(new DataPoint[]{new DataPoint(lastTime, percentage)});
                    series_temp.resetData(new DataPoint[]{new DataPoint(lastTime, temperature)});
                    viewport_chargeCurve.setMaxX(1);
                    lastTime = 1;
                }
            } while (cursor.moveToNext()); // while the cursor has data
        } else { // empty database -> return
            if (charging) // "Charging... (0 min)"
                textView_chargingTime.setText(getString(R.string.charging) + " (0 min)");
            else // "Not data"
                textView_chargingTime.setText(getString(R.string.no_data));
            return;
        }
        cursor.close();
        dbHelper.close();
        // 4. Is there enough data?
        boolean enoughData = time != 0;
        if (!enoughData) { // not enough data
            lastTime = 1;
            textView_chargingTime.setText(getString(R.string.not_enough_data));
        } else { // enough data
            viewport_chargeCurve.setMaxX(lastTime); // set the viewport to the highest time
        }
        // 5. Is the phone charging and is it NOT full charged?
        String timeString = getTimeString(time);
        if (charging && percentage != 100) { // charging and not fully charged -> "Charging... (time)"
            textView_chargingTime.setText(getString(R.string.charging) + " (" + timeString + ")");
        } else if (enoughData) { // discharging + ENOUGH data
            textView_chargingTime.setText(getString(R.string.charging_time) + ": " + timeString);
        }
    }

    private String getTimeString(long timeInMillis) { // returns "hours h minutes min" or "minutes min"
        double minutes;
        if (timeInMillis > 3600000) { // over an hour
            long hours = timeInMillis / 3600000;
            minutes = (timeInMillis - hours * 3600000) / 60000;
            if ((int) minutes == minutes)
                return String.valueOf(hours) + " h " + String.valueOf((int) minutes) + " min";
            return String.valueOf(hours) + " h " + String.valueOf(minutes) + " min";
        } else { // under an hour
            minutes = getDoubleTime(timeInMillis);
            if ((int) minutes == minutes)
                return String.valueOf((int) minutes) + " min";
            return String.valueOf(minutes) + " min";
        }
    }

    private double getDoubleTime(long timeInMillis) { // returns minutes as double
        return (double) Math.round(2 * (double) timeInMillis / 60000) / 2;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        LineGraphSeries series = null;
        switch (compoundButton.getId()){
            case R.id.checkbox_percentage:
                series = series_chargeCurve;
                break;
            case R.id.checkBox_speed:
                series = series_speed;
                break;
            case R.id.checkBox_temp:
                series = series_temp;
                break;
        }
        if(series == null) return;
        if(b)
            graph_chargeCurve.addSeries(series);
        else
            graph_chargeCurve.removeSeries(series);
    }
}
