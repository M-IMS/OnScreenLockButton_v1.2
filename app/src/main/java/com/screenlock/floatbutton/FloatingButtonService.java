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
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.Uri;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
        createFloatingButton();
        ContextCompat.registerReceiver(this, updateReceiver, new IntentFilter("UPDATE_FLOAT_BUTTON"), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void createFloatingButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Create the floating button view
        floatingView = createButtonView();

        int layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

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
        FrameLayout frame = new FrameLayout(this);

        int size = getButtonSize();

        // Outer circle (button body)
        View circle = new View(this);
        circle.setTag("circle_body");
        FrameLayout.LayoutParams circleParams =
                new FrameLayout.LayoutParams(size, size);
        circleParams.gravity = Gravity.CENTER;

        // Use a GradientDrawable for the circle shape
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        
        SharedPreferences prefs = getSharedPreferences("float_settings", MODE_PRIVATE);
        String colorHex = prefs.getString("color", "#00D4FF");
        int baseColor = Color.parseColor(colorHex);
        int colorWithAlpha = (baseColor & 0x00FFFFFF) | 0xCC000000;
        
        gd.setColor(colorWithAlpha);
        gd.setStroke(4, Color.WHITE & 0x80FFFFFF); // semi-transparent white border
        circle.setBackground(gd);
        circle.setLayoutParams(circleParams);

        String customIconUri = prefs.getString("custom_icon_uri", null);
        String iconText = prefs.getString("icon", "⬤");

        View iconView;
        if ("CUSTOM".equals(iconText) && customIconUri != null) {
            ImageView img = new ImageView(this);
            try {
                Uri uri = Uri.parse(customIconUri);
                img.setImageURI(uri);
                img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                int padding = size / 4;
                img.setPadding(padding, padding, padding, padding);
                iconView = img;
            } catch (Exception e) {
                // Fallback to text if image fails
                TextView tv = new TextView(this);
                tv.setText("⬤");
                tv.setTextColor(Color.WHITE);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size / 2f);
                iconView = tv;
            }
        } else {
            TextView tv = new TextView(this);
            tv.setText(iconText);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size / 2f);
            tv.setTextColor(Color.WHITE);
            tv.setGravity(Gravity.CENTER);
            iconView = tv;
        }

        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
        iconView.setLayoutParams(iconParams);

        frame.addView(circle);
        frame.addView(iconView);
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
                        v.performClick();
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
            vibrator.vibrate(VibrationEffect.createOneShot(
                    80, VibrationEffect.DEFAULT_AMPLITUDE));
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
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Screen Lock Button",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps the floating screen lock button running");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
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
