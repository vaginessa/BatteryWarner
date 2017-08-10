package com.laudien.p1xelfehler.batterywarner.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LabelFormatter;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.Series;
import com.laudien.p1xelfehler.batterywarner.R;
import com.laudien.p1xelfehler.batterywarner.database.DatabaseController;
import com.laudien.p1xelfehler.batterywarner.helper.ToastHelper;

import java.util.Locale;

import static android.widget.Toast.LENGTH_SHORT;
import static com.laudien.p1xelfehler.batterywarner.helper.GraphDbHelper.TYPE_PERCENTAGE;
import static com.laudien.p1xelfehler.batterywarner.helper.GraphDbHelper.TYPE_TEMPERATURE;

/**
 * Super class of all Fragments that are using the charging curve.
 */
public abstract class BasicGraphFragment extends Fragment {
    /**
     * An instance of the {@link com.laudien.p1xelfehler.batterywarner.fragments.InfoObject} holding information about the charging curve.
     */
    protected InfoObject infoObject;
    /**
     * The GraphView where the graphs are shown
     */
    protected GraphView graphView;
    /**
     * Switch which turns the percentage graph on and off.
     */
    protected Switch switch_percentage;
    /**
     * Switch which turns the temperature graph on and off.
     */
    protected Switch switch_temp;
    /**
     * TextView that contains the title over the GraphView.
     */
    protected TextView textView_title;
    /**
     * TextView that contains the charging time.
     */
    protected TextView textView_chargingTime;
    /**
     * An array of both graphs that are displayed in the GraphView.
     */
    LineGraphSeries<DataPoint>[] series;

    /**
     * An {@link android.widget.CompoundButton.OnCheckedChangeListener} managing all switches.
     */
    private final CompoundButton.OnCheckedChangeListener onSwitchChangedListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
            Series s = null;
            if (compoundButton == switch_percentage) {
                if (series != null) {
                    s = series[TYPE_PERCENTAGE];
                }
            } else if (compoundButton == switch_temp) {
                if (series != null) {
                    s = series[TYPE_TEMPERATURE];
                }
            }
            if (s != null) {
                if (checked) {
                    graphView.addSeries(s);
                } else {
                    graphView.removeSeries(s);
                }
            }
        }
    };
    /**
     * A byte containing the number of graph labels.
     */
    private byte labelCounter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_graph, container, false);
        graphView = view.findViewById(R.id.graphView);
        switch_percentage = view.findViewById(R.id.switch_percentage);
        switch_percentage.setOnCheckedChangeListener(onSwitchChangedListener);
        switch_temp = view.findViewById(R.id.switch_temp);
        switch_temp.setOnCheckedChangeListener(onSwitchChangedListener);
        textView_title = view.findViewById(R.id.textView_title);
        textView_chargingTime = view.findViewById(R.id.textView_chargingTime);
        initGraphView();
        graphView.getGridLabelRenderer().setLabelFormatter(getLabelFormatter());
        loadSeries();
        return view;
    }

    /**
     * Method that provides an array of the graphs that should be displayed.
     *
     * @return Returns an array of graphs.
     */
    protected abstract LineGraphSeries<DataPoint>[] getSeries();

    /**
     * Method that provides the time the graph was created.
     *
     * @return Returns time the graph was created in milliseconds.
     */
    protected abstract long getEndTime();

    protected abstract long getStartTime();

    /**
     * Method that loads the graph into the GraphView and sets the text of the TextView that show the time.
     * You can override it to only do it under some conditions.
     */
    void loadSeries() {
        series = getSeries();
        if (series != null) {
            if (switch_percentage.isChecked() && series[DatabaseController.GRAPH_INDEX_BATTERY_LEVEL] != null) {
                graphView.addSeries(series[DatabaseController.GRAPH_INDEX_BATTERY_LEVEL]);
            }
            if (switch_temp.isChecked() && series[DatabaseController.GRAPH_INDEX_TEMPERATURE] != null) {
                graphView.addSeries(series[DatabaseController.GRAPH_INDEX_TEMPERATURE]);
            }
            createOrUpdateInfoObject();
            long endTime = getEndTime();
            if (endTime > 0) {
                graphView.getViewport().setMaxX(endTime);
            } else {
                graphView.getViewport().setMaxX(1);
            }
        } else {
            graphView.getViewport().setMaxX(1);
        }
        setTimeText();
    }

    /**
     * Creates a new or updates the existing instance of the
     * {@link com.laudien.p1xelfehler.batterywarner.fragments.InfoObject}.
     */
    private void createOrUpdateInfoObject() {
        if (series != null
                && series[DatabaseController.GRAPH_INDEX_BATTERY_LEVEL] != null
                && series[DatabaseController.GRAPH_INDEX_TEMPERATURE] != null) {
            if (infoObject == null) {
                infoObject = new InfoObject(
                        getStartTime(),
                        getEndTime(),
                        series[TYPE_PERCENTAGE].getHighestValueX(),
                        series[TYPE_TEMPERATURE].getHighestValueY(),
                        series[TYPE_TEMPERATURE].getLowestValueY(),
                        series[TYPE_PERCENTAGE].getHighestValueY() - series[TYPE_PERCENTAGE].getLowestValueY()
                );
            } else {
                infoObject.updateValues(
                        getStartTime(),
                        getEndTime(),
                        series[TYPE_PERCENTAGE].getHighestValueX(),
                        series[TYPE_TEMPERATURE].getHighestValueY(),
                        series[TYPE_TEMPERATURE].getLowestValueY(),
                        series[TYPE_PERCENTAGE].getHighestValueY() - series[TYPE_PERCENTAGE].getLowestValueY()
                );
            }
        } else { // any graph is null
            infoObject = null;
        }
    }

    /**
     * Sets the text of
     * {@link com.laudien.p1xelfehler.batterywarner.fragments.BasicGraphFragment#textView_chargingTime}
     * to the charging time.
     */
    void setTimeText() {
        if (infoObject != null) {
            textView_chargingTime.setText(String.format(
                    Locale.getDefault(),
                    "%s: %s",
                    getString(R.string.info_charging_time),
                    infoObject.getTimeString(getContext())
            ));
        }
    }

    /**
     * Reloads the graphs from the database.
     */
    void reload() {
        graphView.removeAllSeries();
        loadSeries();
    }

    /**
     * Provides the format of the text of the x and y axis of the graph.
     *
     * @return Returns a LabelFormatter that is used in the GraphView.
     */
    private LabelFormatter getLabelFormatter() {
        return new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) { // X-axis (time)
                    if (value == 0) {
                        labelCounter = 1;
                        return "0 min";
                    }
                    if (value < 0.1) {
                        return "";
                    }
                    if (labelCounter++ % 3 == 0)
                        return super.formatLabel(value, true) + " min";
                    return "";
                } else if (switch_percentage.isChecked() ^ switch_temp.isChecked()) { // Y-axis (percent)
                    if (switch_percentage.isChecked())
                        return super.formatLabel(value, false) + "%";
                    if (switch_temp.isChecked())
                        return super.formatLabel(value, false) + "°C";
                }
                return super.formatLabel(value, false);
            }
        };
    }

    /**
     * Initializes the ViewPort of the GraphView. Sets the part of the x and y axis that is shown.
     */
    private void initGraphView() {
        Viewport viewport = graphView.getViewport();
        viewport.setXAxisBoundsManual(true);
        viewport.setYAxisBoundsManual(true);
        viewport.setMinX(0);
        viewport.setMaxY(100);
        viewport.setMinY(0);
    }

    /**
     * Shows the info dialog defined in the {@link com.laudien.p1xelfehler.batterywarner.fragments.BasicGraphFragment#infoObject}.
     * Shows a toast if there are no graphs or if the
     * {@link com.laudien.p1xelfehler.batterywarner.fragments.BasicGraphFragment#infoObject} is null.
     */
    public void showInfo() {
        if (series != null && infoObject != null) {
            infoObject.showDialog(getContext());
        } else {
            ToastHelper.sendToast(getContext(), R.string.toast_no_data, LENGTH_SHORT);
        }
    }
}
