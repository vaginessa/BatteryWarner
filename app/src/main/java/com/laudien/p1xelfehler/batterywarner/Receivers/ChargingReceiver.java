package com.laudien.p1xelfehler.batterywarner.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationManagerCompat;

import com.laudien.p1xelfehler.batterywarner.Contract;
import com.laudien.p1xelfehler.batterywarner.NotificationBuilder;
import com.laudien.p1xelfehler.batterywarner.R;
import com.laudien.p1xelfehler.batterywarner.RootChecker;
import com.laudien.p1xelfehler.batterywarner.Services.ChargingService;

import static com.laudien.p1xelfehler.batterywarner.NotificationBuilder.ID_NOT_ROOTED;

public class ChargingReceiver extends BroadcastReceiver {
    //private final String TAG = getClass().getSimpleName();

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (!intent.getAction().equals("android.intent.action.ACTION_POWER_CONNECTED")) return;
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences.getBoolean(context.getString(R.string.pref_first_start), context.getResources().getBoolean(R.bool.pref_first_start_default)))
            return; // return if intro was not finished

        DischargingAlarmReceiver.cancelDischargingAlarm(context); // cancel discharging alarm

        // cancel warning low notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(NotificationBuilder.ID_WARNING_LOW);

        // reset already notified
        sharedPreferences.edit().putBoolean(context.getString(R.string.pref_already_notified), false).apply();

        // check if the charging notification for the current method is enabled
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) {
            return;
        }
        boolean usbDisabled = sharedPreferences.getBoolean(context.getString(R.string.pref_usb_charging_disabled), context.getResources().getBoolean(R.bool.pref_usb_charging_disabled_default));
        boolean stopChargingEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_stop_charging), context.getResources().getBoolean(R.bool.pref_stop_charging_default));
        if (usbDisabled || stopChargingEnabled) { // if any root feature is enabled
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    if (!RootChecker.isDeviceRooted()) { // show notification if the app has no root anymore!
                        NotificationBuilder.showNotification(context, ID_NOT_ROOTED);
                    }
                }
            });
        }
        if (ChargingService.isChargingTypeEnabled(context, batteryStatus) || usbDisabled) {
            // start service
            ChargingService.startService(context);
            // notify if silent/vibrate mode
            NotificationBuilder.showNotification(context, NotificationBuilder.ID_SILENT_MODE);
        } else {
            // if not check again in 10s to make sure it is correct
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    if (batteryStatus == null) {
                        return;
                    }
                    boolean usbDisabled = sharedPreferences.getBoolean(context.getString(R.string.pref_usb_charging_disabled), context.getResources().getBoolean(R.bool.pref_usb_charging_disabled_default));
                    boolean isCharging = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, Contract.NO_STATE) != 0;
                    if ((isCharging && ChargingService.isChargingTypeEnabled(context, batteryStatus)) || usbDisabled) {
                        ChargingService.startService(context);
                        NotificationBuilder.showNotification(context, NotificationBuilder.ID_SILENT_MODE);
                    }
                }
            }, 10000);
        }
    }
}
