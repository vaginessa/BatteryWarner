package com.laudien.p1xelfehler.batterywarner;

import android.content.Context;
import android.os.Looper;
import android.preference.PreferenceManager;

import eu.chainfire.libsuperuser.Shell;

public final class RootChecker {
    public static boolean isDeviceRooted() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new InMainThreadException();
        }
        return Shell.SU.available();
    }

    public static void enableCharging(Context context) throws NotRootedException {
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.pref_stop_charging), false)) {
            return;
        }
        if (!isDeviceRooted()) {
            throw new NotRootedException();
        }
        Shell.SU.run("echo 1 > /sys/class/power_supply/battery/charging_enabled");
    }

    public static void disableCharging(Context context) throws NotRootedException {
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.pref_stop_charging), false)) {
            return;
        }
        if (!isDeviceRooted()) {
            throw new NotRootedException();
        }
        Shell.SU.run("echo 0 > /sys/class/power_supply/battery/charging_enabled");
    }

    public static boolean isChargingEnabled() throws NotRootedException {
        if (!isDeviceRooted()) {
            throw new NotRootedException();
        }
        return Shell.SU.run("cat /sys/class/power_supply/battery/charging_enabled").get(0).equals("1");
    }

    public static class NotRootedException extends Exception {
        public NotRootedException() {
            super("The device is not rooted!");
        }
    }

    private static class InMainThreadException extends RuntimeException {
        private InMainThreadException() {
            super("Root calls must be done outside of the main thread!");
        }
    }
}
