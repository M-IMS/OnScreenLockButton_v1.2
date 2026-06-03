package com.screenlock.floatbutton;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import androidx.core.app.NotificationCompat;

public class FloatingButtonService extends Service {

    public static boolean isRunning = false;

    private static final String CHANNEL_ID = "FloatButtonChannel";
    private static final int NOTIFICATION_ID = 101;

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;

    // For drag tracking
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private long touchStartTime;
    private boolean isDragging = false;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        createFloatingButton();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, new IntentFilter("UPDATE_FLOAT_BUTTON"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiver, new IntentFilter("UPDATE_FLOAT_BUTTON"));
        }
    }

    private void createFloatingButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Create the floating button view
        floatingView = createButtonView();

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                getButtonSize(),
                getButtonSize(),
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences prefs = getSharedPreferences("float_settings", MODE_PRIVATE);
        params.x = prefs.getInt("pos_x", 50);
        params.y = prefs.getInt("pos_y", 400);

        windowManager.addView(floatingView, params);
        setupTouchListener();
    }

    private View createButtonView() {
        // Build a circular button programmatically
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);

        int size = getButtonSize();

        // Outer circle (button body)
        View circle = new View(this);
        android.widget.FrameLayout.LayoutParams circleParams =
                new android.widget.FrameLayout.LayoutParams(size, size);
        circleParams.gravity = Gravity.CENTER;

        // Use a GradientDrawable for the circle shape
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        gd.setColor(0xCC1A1A2E);         // dark navy, semi-transparent
        gd.setStroke(4, 0xCC36365F);    // cyan border
        circle.setBackground(gd);
        circle.setLayoutParams(circleParams);

        // Power icon label
        android.widget.TextView icon = new android.widget.TextView(this);
        icon.setText(getSharedPreferences("float_settings", MODE_PRIVATE).getString("icon","o"));
        icon.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size / 2f);
        icon.setTextColor(Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        android.widget.FrameLayout.LayoutParams iconParams =
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        icon.setLayoutParams(iconParams);

        frame.addView(circle);
        frame.addView(icon);
        frame.setAlpha(getButtonAlpha());
        return frame;
    }


private int getButtonSize(){
    int dp = getSharedPreferences("float_settings", MODE_PRIVATE).getInt("size",100);
    return (int)(dp * getResources().getDisplayMetrics().density);
}
private float getButtonAlpha(){
    return getSharedPreferences("float_settings", MODE_PRIVATE).getInt("alpha",80)/100f;
}
private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
    public void onReceive(Context c, Intent i){
        if(floatingView!=null){
            windowManager.removeView(floatingView);
            createFloatingButton();
        }
    }
};

    private void setupTouchListener() {
        floatingView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    touchStartTime = System.currentTimeMillis();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - initialTouchX;
                    float dy = event.getRawY() - initialTouchY;
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        params.x = initialX + (int) dx;
                        params.y = initialY + (int) dy;
                        windowManager.updateViewLayout(floatingView, params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    long touchDuration = System.currentTimeMillis() - touchStartTime;
                    if (!isDragging && touchDuration < 400) {
                        // It's a tap — lock the screen!
                        lockScreen();
                    } else if (isDragging) {
                        // Save position
                        getSharedPreferences("float_settings", MODE_PRIVATE).edit()
                                .putInt("pos_x", params.x)
                                .putInt("pos_y", params.y)
                                .apply();
                    }
                    return true;
            }
            return false;
        });
    }

    private void lockScreen() {
        // Vibrate briefly for feedback
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                        80, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(80);
            }
        }

        // 1. Try Accessibility Service (Allows Fingerprint/Face Unlock)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ScreenLockAccessibilityService service = ScreenLockAccessibilityService.getInstance();
            if (service != null) {
                service.lock();
                return;
            }
        }

        // 2. Fallback to Device Admin (Requires PIN/Password)
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            devicePolicyManager.lockNow();
        } else {
            Toast.makeText(this, "Please enable Accessibility or Device Admin permission!", Toast.LENGTH_SHORT).show();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Lock Button",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the floating screen lock button running");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Lock Button")
                .setContentText("Floating button is active. Tap to open settings.")
                .setSmallIcon(android.R.drawable.ic_lock_power_off)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Restart if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception e) {
            // Already unregistered or not registered
        }
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
