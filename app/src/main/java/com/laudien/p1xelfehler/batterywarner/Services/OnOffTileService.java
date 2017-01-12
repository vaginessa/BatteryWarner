package com.laudien.p1xelfehler.batterywarner.Services;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import android.widget.Toast;

import com.laudien.p1xelfehler.batterywarner.Contract;
import com.laudien.p1xelfehler.batterywarner.R;
import com.laudien.p1xelfehler.batterywarner.Receivers.BatteryAlarmManager;

@RequiresApi(api = Build.VERSION_CODES.N)
public class OnOffTileService extends TileService {

    //private static final String TAG = "OnOffTileService";
    private Tile tile;
    private boolean firstStart;

    @Override
    public void onStartListening() {
        super.onStartListening();
        tile = getQsTile();
        if (Contract.IS_PRO) { // pro version
            SharedPreferences sharedPreferences = getSharedPreferences(Contract.SHARED_PREFS, MODE_PRIVATE);
            // check if the intro was finished first
            firstStart = sharedPreferences.getBoolean(Contract.PREF_FIRST_START, true);
            boolean isEnabled = sharedPreferences.getBoolean(Contract.PREF_IS_ENABLED, true);
            if (firstStart) {
                isEnabled = false;
            }
            // set state from shared preferences
            if (isEnabled)
                tile.setState(Tile.STATE_ACTIVE);
            else
                tile.setState(Tile.STATE_INACTIVE);

        } else { // free version
            tile.setState(Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!Contract.IS_PRO) { // not pro
            Toast.makeText(getApplicationContext(), getString(R.string.not_pro), Toast.LENGTH_SHORT).show();
            return;
        }
        if (firstStart) { // intro not finished
            Toast.makeText(getApplicationContext(), getString(R.string.please_finish_intro), Toast.LENGTH_SHORT).show();
            return;
        }
        boolean isActive = tile.getState() == Tile.STATE_ACTIVE;
        SharedPreferences sharedPreferences = getSharedPreferences(Contract.SHARED_PREFS, MODE_PRIVATE);
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) {
            return;
        }
        boolean isCharging = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, Contract.NO_STATE) != 0;
        BatteryAlarmManager batteryAlarmManager = BatteryAlarmManager.getInstance(this);
        batteryAlarmManager.checkAndNotify(this, batteryStatus);

        if (isActive) { // disable battery warnings
            tile.setState(Tile.STATE_INACTIVE);
            if (isCharging) { // charging
                stopService(new Intent(this, ChargingService.class));
            } else { // discharging
                batteryAlarmManager.cancelDischargingAlarm(this);
            }
            Toast.makeText(getApplicationContext(), getString(R.string.disabled_info), Toast.LENGTH_SHORT).show();
        } else { // enable battery warnings
            tile.setState(Tile.STATE_ACTIVE);
            sharedPreferences.edit().putBoolean(Contract.PREF_ALREADY_NOTIFIED, false).apply();
            if (isCharging) { // charging
                startService(new Intent(this, ChargingService.class));
            } else { // discharging
                batteryAlarmManager.setDischargingAlarm(this);
            }
            Toast.makeText(getApplicationContext(), getString(R.string.enabled_info), Toast.LENGTH_SHORT).show();
        }
        sharedPreferences.edit().putBoolean(Contract.PREF_IS_ENABLED, !isActive).apply();
        tile.updateTile();

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(Contract.BROADCAST_ON_OFF_CHANGED);
        sendBroadcast(broadcastIntent);
    }
}
