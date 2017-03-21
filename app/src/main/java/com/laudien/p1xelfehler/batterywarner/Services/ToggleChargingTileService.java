package com.laudien.p1xelfehler.batterywarner.Services;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import android.widget.Toast;

import com.laudien.p1xelfehler.batterywarner.NotificationBuilder;
import com.laudien.p1xelfehler.batterywarner.R;
import com.laudien.p1xelfehler.batterywarner.RootHelper;

import static android.service.quicksettings.Tile.STATE_ACTIVE;
import static android.service.quicksettings.Tile.STATE_INACTIVE;
import static android.widget.Toast.LENGTH_LONG;
import static com.laudien.p1xelfehler.batterywarner.Contract.IS_PRO;
import static com.laudien.p1xelfehler.batterywarner.NotificationBuilder.ID_STOP_CHARGING_NOT_WORKING;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ToggleChargingTileService extends TileService {
    private Tile tile;
    private SharedPreferences sharedPreferences;

    @Override
    public void onStartListening() {
        super.onStartListening();
        tile = getQsTile();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean stopChargingEnabled = sharedPreferences.getBoolean(getString(R.string.pref_stop_charging), getResources().getBoolean(R.bool.pref_stop_charging_default));
        if (stopChargingEnabled) {
            tile.setState(STATE_INACTIVE);
        } else {
            tile.setState(STATE_ACTIVE);
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean isActive = tile.getState() == STATE_ACTIVE;
        if (isActive) { // deactivating the tile
            new AsyncTask<Void, Void, Boolean[]>() {
                @Override
                protected Boolean[] doInBackground(Void... voids) {
                    Boolean[] booleans = new Boolean[2];
                    try {
                        RootHelper.isChargingEnabled();
                        booleans[0] = true;
                        booleans[1] = true;
                    } catch (RootHelper.NotRootedException e) {
                        booleans[0] = false;
                        booleans[1] = false;
                    } catch (RootHelper.BatteryFileNotFoundException e) {
                        NotificationBuilder.showNotification(ToggleChargingTileService.this, ID_STOP_CHARGING_NOT_WORKING);
                        booleans[0] = true;
                        booleans[1] = false;
                    }
                    return booleans;
                }

                @Override
                protected void onPostExecute(Boolean[] booleans) {
                    super.onPostExecute(booleans);
                    boolean isRooted = booleans[0];
                    boolean updateToast = booleans[1];
                    if (updateToast) {
                        tile.setState(STATE_INACTIVE);
                        tile.updateTile();
                        sharedPreferences.edit().putBoolean(getString(R.string.pref_stop_charging), true).apply();
                    }
                    if (!isRooted) {
                        Toast.makeText(ToggleChargingTileService.this, R.string.toast_not_rooted, LENGTH_LONG).show();
                    }
                }
            }.execute();
        } else { // activating the tile
            if (IS_PRO) {
                tile.setState(STATE_ACTIVE);
                sharedPreferences.edit().putBoolean(getString(R.string.pref_stop_charging), false).apply();
            } else {
                Toast.makeText(getApplicationContext(), R.string.not_pro, Toast.LENGTH_SHORT).show();
            }
        }
        tile.updateTile();
    }
}
