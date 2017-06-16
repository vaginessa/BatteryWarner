package com.laudien.p1xelfehler.batterywarner.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationManagerCompat;

import com.laudien.p1xelfehler.batterywarner.MainActivity;
import com.laudien.p1xelfehler.batterywarner.R;
import com.laudien.p1xelfehler.batterywarner.SettingsActivity;
import com.laudien.p1xelfehler.batterywarner.preferences.smartChargingActivity.SmartChargingActivity;
import com.laudien.p1xelfehler.batterywarner.services.EnableChargingService;
import com.laudien.p1xelfehler.batterywarner.services.GrantRootService;
import com.laudien.p1xelfehler.batterywarner.services.TogglePowerSavingService;

import java.util.Locale;

import static android.app.Notification.PRIORITY_HIGH;
import static android.app.Notification.PRIORITY_LOW;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.media.RingtoneManager.TYPE_NOTIFICATION;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;

/**
 * Helper class to show a notification with the given type. All notifications used in the app are listed here.
 */
public final class NotificationHelper {
    /**
     * Notification id of the notification that warns if silent/vibrate mode is turned on.
     */
    public static final int ID_SILENT_MODE = 1001;
    /**
     * Notification id of the notification that warns that the battery is above X%.
     */
    public static final int ID_WARNING_HIGH = 1002;
    /**
     * Notification id of the notification that warns that the battery is below Y%.
     */
    public static final int ID_WARNING_LOW = 1003;
    /**
     * Notification id of the notification that the user has to click/dismiss after the device is unplugged.
     * Only shown if the stop charging feature is enabled.
     */
    public static final int ID_STOP_CHARGING = 1004;
    /**
     * Notification id of the notification that asks the user for root again after the app was updated.
     */
    public static final int ID_GRANT_ROOT = 1005;
    /**
     * Notification id of the notification that tells the user that the stop charging feature is not working
     * on this device.
     */
    public static final int ID_STOP_CHARGING_NOT_WORKING = 1006;
    /**
     * Notification id of the notification that asks for root again if app has no root rights anymore.
     */
    public static final int ID_NOT_ROOTED = 1007;
    /**
     * Notification id of the notification that tells the user that no alarm was found in the alarm app
     **/
    public static final int ID_NO_ALARM_TIME_FOUND = 1008;

    private static final long[] VIBRATE_PATTERN = {0, 300, 300, 300};

    private NotificationHelper() {
    }

    /**
     * Shows the notification with the given id.
     *
     * @param context        An instance of the Context class.
     * @param notificationID The id of the notification - usually one of the id constants.
     */
    public static void showNotification(final Context context, final int notificationID) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_is_enabled), context.getResources().getBoolean(R.bool.pref_is_enabled_default));
        if (isEnabled) {
            switch (notificationID) {
                case ID_WARNING_HIGH:
                    showWarningHighNotification(context, sharedPreferences);
                    break;
                case ID_WARNING_LOW:
                    showWarningLowNotification(context, sharedPreferences);
                    break;
                case ID_SILENT_MODE:
                    showSilentModeNotification(context, sharedPreferences);
                    break;
                case ID_STOP_CHARGING:
                    showStopChargingNotification(context, sharedPreferences);
                    break;
                case ID_STOP_CHARGING_NOT_WORKING:
                    showStopChargingNotWorkingNotification(context, sharedPreferences);
                    break;
                case ID_GRANT_ROOT:
                    showGrantRootNotification(context, sharedPreferences);
                    break;
                case ID_NOT_ROOTED:
                    showNotRootedNotification(context);
                    break;
                case ID_NO_ALARM_TIME_FOUND:
                    showNoAlarmTimeFoundNotification(context);
                    break;
                default:
                    throw new IdNotFoundException();
            }
        }
    }

    /**
     * Cancels the notification with the given notification id.
     *
     * @param context        An instance of the Context class.
     * @param notificationID The id of the notification - usually one of the id constants.
     */
    public static void cancelNotification(Context context, int... notificationID) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        for (int id : notificationID) {
            notificationManager.cancel(id);
        }
    }

    private static void showWarningHighNotification(final Context context, SharedPreferences defaultPrefs) {
        boolean warningHighEnabled = defaultPrefs.getBoolean(context.getString(R.string.pref_warning_high_enabled), context.getResources().getBoolean(R.bool.pref_warning_high_enabled_default));
        boolean resetBatteryStats = defaultPrefs.getBoolean(context.getString(R.string.pref_reset_battery_stats), context.getResources().getBoolean(R.bool.pref_reset_battery_stats_default));
        // show notification
        if (warningHighEnabled) {
            int warningHigh = defaultPrefs.getInt(context.getString(R.string.pref_warning_high), context.getResources().getInteger(R.integer.pref_warning_high_default));
            String messageText = String.format(Locale.getDefault(), "%s %d%%!", context.getString(R.string.notification_warning_high), warningHigh);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder builder = new Notification.Builder(context)
                    .setSmallIcon(getSmallIconRes())
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(messageText)
                    .setStyle(getBigTextStyle(messageText))
                    .setContentIntent(getDefaultClickIntent(context))
                    .setAutoCancel(true)
                    .setSound(getWarningSound(context, defaultPrefs, true))
                    .setVibrate(VIBRATE_PATTERN);
            if (Build.VERSION.SDK_INT >= O) {
                builder.setChannelId(context.getString(R.string.channel_battery_warnings));
            } else {
                builder.setPriority(PRIORITY_HIGH);
            }
            notificationManager.notify(ID_WARNING_HIGH, builder.build());
            // reset the android internal battery stats
            if (resetBatteryStats) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            RootHelper.resetBatteryStats();
                        } catch (RootHelper.NotRootedException e) {
                            e.printStackTrace();
                            showNotRootedNotification(context);
                        }
                    }
                });
            }
        }
    }

    private static void showWarningLowNotification(final Context context, SharedPreferences sharedPreferences) {
        boolean warningLowEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_warning_low_enabled), context.getResources().getBoolean(R.bool.pref_warning_low_enabled_default));
        boolean prefPowerSavingModeEnabled = SDK_INT >= LOLLIPOP && sharedPreferences.getBoolean(context.getString(R.string.pref_power_saving_mode), context.getResources().getBoolean(R.bool.pref_power_saving_mode_default));
        if (warningLowEnabled) {
            int warningLow = sharedPreferences.getInt(context.getString(R.string.pref_warning_low), context.getResources().getInteger(R.integer.pref_warning_low_default));
            String messageText = String.format(Locale.getDefault(), "%s %d%%!", context.getString(R.string.notification_warning_low), warningLow);
            Notification.Builder builder = new Notification.Builder(context)
                    .setSmallIcon(getSmallIconRes())
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(messageText)
                    .setStyle(getBigTextStyle(messageText))
                    .setContentIntent(getDefaultClickIntent(context))
                    .setAutoCancel(true)
                    .setSound(getWarningSound(context, sharedPreferences, false))
                    .setVibrate(VIBRATE_PATTERN);
            if (Build.VERSION.SDK_INT >= O) {
                builder.setChannelId(context.getString(R.string.channel_battery_warnings));
            } else {
                builder.setPriority(PRIORITY_HIGH);
            }
            // enable power saving mode
            if (SDK_INT >= LOLLIPOP && prefPowerSavingModeEnabled) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            RootHelper.togglePowerSavingMode(true);
                        } catch (RootHelper.NotRootedException e) {
                            e.printStackTrace();
                            showNotRootedNotification(context);
                        }
                    }
                });
                Intent exitPowerSaveIntent = new Intent(context, TogglePowerSavingService.class);
                PendingIntent pendingIntent = PendingIntent.getService(context, 0, exitPowerSaveIntent, 0);
                builder.addAction(R.drawable.ic_battery_charging_full_white_24dp, context.getString(R.string.notification_button_toggle_power_saving), pendingIntent);
            }
            // build and show notification
            NotificationManager notificationManager = (NotificationManager)
                    context.getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(ID_WARNING_LOW, builder.build());
        }
    }

    private static void showSilentModeNotification(Context context, SharedPreferences sharedPreferences) {
        boolean silentNotificationEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_notifications_off_warning), context.getResources().getBoolean(R.bool.pref_notifications_off_warning_default));
        boolean isSoundEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_enable_sound), context.getResources().getBoolean(R.bool.pref_enable_sound_default));
        if (silentNotificationEnabled && isSoundEnabled) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int ringerMode = audioManager.getRingerMode();
            boolean areNotificationsEnabled;
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            if (SDK_INT >= N) {
                areNotificationsEnabled = notificationManager.areNotificationsEnabled();
            } else {
                NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
                areNotificationsEnabled = notificationManagerCompat.areNotificationsEnabled();
            }
            if (!areNotificationsEnabled || ringerMode == AudioManager.RINGER_MODE_SILENT || ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                String messageText = context.getString(R.string.notification_sound_disabled);
                Notification.Builder builder = new Notification.Builder(context)
                        .setSmallIcon(getSmallIconRes())
                        .setContentTitle(context.getString(R.string.app_name))
                        .setContentText(messageText)
                        .setStyle(getBigTextStyle(messageText))
                        .setContentIntent(getDefaultClickIntent(context))
                        .setAutoCancel(true)
                        .setSound(getDefaultSound())
                        .setVibrate(VIBRATE_PATTERN);
                if (SDK_INT >= O) {
                    builder.setChannelId(context.getString(R.string.channel_other_warnings));
                } else {
                    builder.setPriority(PRIORITY_HIGH);
                }
                notificationManager.notify(ID_SILENT_MODE, builder.build());
            }
        }
    }

    private static void showStopChargingNotification(final Context context, SharedPreferences sharedPreferences) {
        boolean stopChargingEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_stop_charging), context.getResources().getBoolean(R.bool.pref_stop_charging_default));
        boolean usbChargingDisabled = sharedPreferences.getBoolean(context.getString(R.string.pref_usb_charging_disabled), context.getResources().getBoolean(R.bool.pref_usb_charging_disabled_default));
        if (stopChargingEnabled || usbChargingDisabled) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!RootHelper.isChargingEnabled()) {
                            PendingIntent pendingIntent = PendingIntent.getService(context, ID_STOP_CHARGING,
                                    new Intent(context, EnableChargingService.class), PendingIntent.FLAG_CANCEL_CURRENT);
                            String messageText = context.getString(R.string.notification_charging_disabled);
                            Notification.Builder builder = new Notification.Builder(context)
                                    .setSmallIcon(getSmallIconRes())
                                    .setContentTitle(context.getString(R.string.app_name))
                                    .setContentText(messageText)
                                    .setStyle(getBigTextStyle(messageText))
                                    .setContentIntent(pendingIntent)
                                    .addAction(R.drawable.ic_battery_charging_full_white_24dp, context.getString(R.string.notification_button_enable_charging), pendingIntent)
                                    .setOngoing(true);
                            if (SDK_INT >= O) {
                                builder.setChannelId(context.getString(R.string.channel_battery_warnings));
                            } else {
                                builder.setPriority(PRIORITY_LOW);
                            }
                            NotificationManager notificationManager = (NotificationManager)
                                    context.getSystemService(NOTIFICATION_SERVICE);
                            notificationManager.notify(ID_STOP_CHARGING, builder.build());
                        }
                    } catch (RootHelper.NotRootedException e) {
                        e.printStackTrace();
                        showNotification(context, ID_NOT_ROOTED);
                    } catch (RootHelper.NoBatteryFileFoundException e) {
                        e.printStackTrace();
                        showNotification(context, ID_STOP_CHARGING_NOT_WORKING);
                    }
                }
            });
        }
    }

    private static void showStopChargingNotWorkingNotification(Context context, SharedPreferences sharedPreferences) {
        boolean stopChargingEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_stop_charging), context.getResources().getBoolean(R.bool.pref_stop_charging_default));
        boolean usbChargingDisabled = sharedPreferences.getBoolean(context.getString(R.string.pref_usb_charging_disabled), context.getResources().getBoolean(R.bool.pref_usb_charging_disabled_default));
        if (stopChargingEnabled || usbChargingDisabled) {
            String messageText = context.getString(R.string.notification_stop_charging_not_working);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                    new Intent(context, SettingsActivity.class), FLAG_UPDATE_CURRENT);
            Notification.Builder builder = new Notification.Builder(context)
                    .setSmallIcon(getSmallIconRes())
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(messageText)
                    .setStyle(getBigTextStyle(messageText))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setSound(getDefaultSound())
                    .setVibrate(VIBRATE_PATTERN);
            if (SDK_INT >= O) {
                builder.setChannelId(context.getString(R.string.channel_other_warnings));
            } else {
                builder.setPriority(PRIORITY_HIGH);
            }
            NotificationManager notificationManager = (NotificationManager)
                    context.getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(ID_STOP_CHARGING_NOT_WORKING, builder.build());
        }
    }

    private static void showGrantRootNotification(Context context, SharedPreferences sharedPreferences) {
        boolean stopChargingEnabled = sharedPreferences.getBoolean(context.getString(R.string.pref_stop_charging), context.getResources().getBoolean(R.bool.pref_stop_charging_default));
        boolean usbChargingDisabled = sharedPreferences.getBoolean(context.getString(R.string.pref_usb_charging_disabled), context.getResources().getBoolean(R.bool.pref_usb_charging_disabled_default));
        if (stopChargingEnabled || usbChargingDisabled) {
            String messageText = context.getString(R.string.notification_grant_root);
            PendingIntent clickIntent = PendingIntent.getService(context, 0, new Intent(context, GrantRootService.class), FLAG_UPDATE_CURRENT);
            Notification.Builder builder = new Notification.Builder(context)
                    .setOngoing(true)
                    .setSmallIcon(getSmallIconRes())
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(messageText)
                    .setStyle(getBigTextStyle(messageText))
                    .setContentIntent(clickIntent)
                    .addAction(R.drawable.ic_done_white_24dp, context.getString(R.string.notification_button_grant_root), clickIntent)
                    .setAutoCancel(true)
                    .setSound(getDefaultSound())
                    .setVibrate(VIBRATE_PATTERN);
            if (SDK_INT >= O) {
                builder.setChannelId(context.getString(R.string.channel_other_warnings));
            } else {
                builder.setPriority(PRIORITY_HIGH);
            }
            NotificationManager notificationManager = (NotificationManager)
                    context.getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(ID_GRANT_ROOT, builder.build());
        }
    }

    private static void showNotRootedNotification(Context context) {
        String messageText = context.getString(R.string.notification_not_rooted);
        PendingIntent clickIntent = PendingIntent.getService(context, 0, new Intent(context, GrantRootService.class), FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(context)
                .setOngoing(true)
                .setSmallIcon(getSmallIconRes())
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(messageText)
                .setStyle(getBigTextStyle(messageText))
                .setContentIntent(clickIntent)
                .addAction(R.drawable.ic_done_white_24dp, context.getString(R.string.notification_button_grant_root), clickIntent)
                .setAutoCancel(true)
                .setSound(getDefaultSound())
                .setVibrate(VIBRATE_PATTERN);
        if (SDK_INT >= O) {
            builder.setChannelId(context.getString(R.string.channel_other_warnings));
        } else {
            builder.setPriority(PRIORITY_HIGH);
        }
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(ID_NOT_ROOTED, builder.build());
    }

    private static void showNoAlarmTimeFoundNotification(Context context) {
        String messageText = context.getString(R.string.notification_no_alarm_time_found);
        PendingIntent clickIntent = PendingIntent.getActivity(context, 0, new Intent(context, SmartChargingActivity.class), FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(getSmallIconRes())
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(messageText)
                .setStyle(getBigTextStyle(messageText))
                .setContentIntent(clickIntent)
                .setAutoCancel(true)
                .setSound(getDefaultSound())
                .setVibrate(VIBRATE_PATTERN);
        if (SDK_INT >= O) {
            builder.setChannelId(context.getString(R.string.channel_other_warnings));
        } else {
            builder.setPriority(PRIORITY_HIGH);
        }
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(ID_NO_ALARM_TIME_FOUND, builder.build());
    }

    private static Uri getWarningSound(Context context, SharedPreferences sharedPreferences, boolean warningHigh) {
        int pref_id = warningHigh ? R.string.pref_sound_uri_high : R.string.pref_sound_uri_low;
        String uri = sharedPreferences.getString(context.getString(pref_id), "");
        if (uri.equals("")) {
            return getDefaultSound();
        } else {
            return Uri.parse(uri);
        }
    }

    private static Uri getDefaultSound() {
        return RingtoneManager.getDefaultUri(TYPE_NOTIFICATION);
    }

    private static Notification.BigTextStyle getBigTextStyle(String messageText) {
        Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle();
        bigTextStyle.bigText(messageText);
        return bigTextStyle;
    }

    private static PendingIntent getDefaultClickIntent(Context context) {
        return PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), FLAG_UPDATE_CURRENT);
    }

    private static int getSmallIconRes() {
        return R.mipmap.ic_launcher;
    }

    @RequiresApi(api = O)
    public static void createNotificationChannels(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        // battery warnings
        NotificationChannel channel = new NotificationChannel(
                context.getString(R.string.channel_battery_warnings),
                context.getString(R.string.channel_title_battery_warnings),
                NotificationManager.IMPORTANCE_MAX
        );
        channel.setDescription(context.getString(R.string.channel_description_battery_warnings));
        channel.enableLights(true);
        channel.enableVibration(true);
        channel.setVibrationPattern(VIBRATE_PATTERN);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setShowBadge(true);
        notificationManager.createNotificationChannel(channel);
        // info notification
        channel = new NotificationChannel(
                context.getString(R.string.channel_battery_info),
                context.getString(R.string.channel_title_battery_info),
                NotificationManager.IMPORTANCE_MIN
        );
        channel.setDescription(context.getString(R.string.channel_description_battery_info));
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
        // other warnings
        channel = new NotificationChannel(
                context.getString(R.string.channel_other_warnings),
                context.getString(R.string.channel_title_other_warnings),
                NotificationManager.IMPORTANCE_MAX
        );
        channel.setDescription(context.getString(R.string.channel_description_other_warnings));
        channel.enableLights(true);
        channel.enableVibration(true);
        channel.setVibrationPattern(VIBRATE_PATTERN);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setShowBadge(true);
        notificationManager.createNotificationChannel(channel);
    }

    private static class IdNotFoundException extends RuntimeException {
        private IdNotFoundException() {
            super("The given notification id does not exist!");
        }
    }
}
