package com.screenlock.floatbutton;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Objects;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            // Auto-start the floating button service after reboot
            Intent serviceIntent = new Intent(context, FloatingButtonService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}
