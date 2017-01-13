package com.laudien.p1xelfehler.batterywarner.Activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.laudien.p1xelfehler.batterywarner.Activities.IntroActivity.IntroActivity;
import com.laudien.p1xelfehler.batterywarner.Activities.MainActivity.MainActivity;
import com.laudien.p1xelfehler.batterywarner.R;

public class StartActivity extends BaseActivity {
    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean firstStart = sharedPreferences.getBoolean(getString(R.string.pref_first_start), true);
        if (firstStart) {
            startActivity(new Intent(this, IntroActivity.class));
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }
}
