package com.screenlock.floatbutton;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.Objects;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_DEVICE_ADMIN = 1002;
    private static final int REQUEST_PICK_IMAGE = 1003;

    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private Button btnToggle;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);

        btnToggle = findViewById(R.id.btnToggle);
        tvStatus = findViewById(R.id.tvStatus);

        Button btnOverlay = findViewById(R.id.btnOverlay);
        Button btnAdmin = findViewById(R.id.btnAdmin);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);

        btnOverlay.setOnClickListener(v -> requestOverlayPermission());
        btnAdmin.setOnClickListener(v -> requestAdminPermission());
        if (btnAccessibility != null) {
            btnAccessibility.setOnClickListener(v -> requestAccessibilityPermission());
        }

        Button btnPickIcon = findViewById(R.id.btnPickIcon);
        if (btnPickIcon != null) {
            btnPickIcon.setOnClickListener(v -> pickCustomIcon());
        }

        btnToggle.setOnClickListener(v -> toggleFloatingButton());

        setupSettings();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        boolean overlayGranted = Settings.canDrawOverlays(this);
        boolean adminGranted = devicePolicyManager.isAdminActive(adminComponent);
        boolean accessibilityGranted = isAccessibilityServiceEnabled();
        boolean serviceRunning = FloatingButtonService.isRunning;

        Button btnOverlay = findViewById(R.id.btnOverlay);
        Button btnAdmin = findViewById(R.id.btnAdmin);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);

        btnOverlay.setText(overlayGranted ? getString(R.string.btn_overlay_granted) : getString(R.string.btn_overlay));
        btnAdmin.setText(adminGranted ? getString(R.string.btn_admin_granted) : getString(R.string.btn_admin));
        if (btnAccessibility != null) {
            btnAccessibility.setText(accessibilityGranted ? getString(R.string.btn_accessibility_granted) : getString(R.string.btn_accessibility_hint));
        }

        // Allow starting if at least Overlay is granted and either Admin or Accessibility
        boolean allGranted = overlayGranted && (adminGranted || accessibilityGranted);
        btnToggle.setEnabled(allGranted);

        if (serviceRunning) {
            btnToggle.setText(R.string.btn_stop);
            tvStatus.setText(R.string.status_active);
        } else {
            btnToggle.setText(R.string.btn_start);
            tvStatus.setText(R.string.status_inactive);
        }
    }


private void setupSettings() {
    SharedPreferences prefs = getSharedPreferences("float_settings", MODE_PRIVATE);
    SeekBar size = findViewById(R.id.seekSize);
    SeekBar alpha = findViewById(R.id.seekTransparency);
    Spinner icon = findViewById(R.id.spinnerIcon);
    ColorWheelView colorWheel = findViewById(R.id.colorWheel);

    size.setProgress(prefs.getInt("size",100)-50);
    alpha.setProgress(prefs.getInt("alpha",80)-10);

    String[] icons = {" ", "o", "🔒", "⚡", "⬤", "■", "Custom"};
    ArrayAdapter<String> iconAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, icons);
    iconAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
    icon.setAdapter(iconAdapter);
    String savedIcon = prefs.getString("icon", "⬤");
    if ("CUSTOM".equals(savedIcon)) savedIcon = "Custom";
    for (int i = 0; i < icons.length; i++) {
        if (Objects.equals(icons[i], savedIcon)) {
            icon.setSelection(i);
            break;
        }
    }

    String savedColorStr = prefs.getString("color", "#00D4FF");
    int savedColor = Color.parseColor(savedColorStr);
    colorWheel.setSelectedColor(savedColor);

    size.setOnSeekBarChangeListener(new SimpleSeekBar("size",50));
    alpha.setOnSeekBarChangeListener(new SimpleSeekBar("alpha",10));
    icon.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            String selected = icons[pos];
            if ("Custom".equals(selected)) {
                prefs.edit().putString("icon", "CUSTOM").apply();
            } else {
                prefs.edit()
                        .putString("icon", selected)
                        .remove("custom_icon_uri")
                        .apply();
            }
            sendBroadcast(new Intent("UPDATE_FLOAT_BUTTON"));
        }
        public void onNothingSelected(AdapterView<?> p) {}
    });

    colorWheel.setOnColorSelectedListener(colorInt -> {
        String hexColor = String.format("#%06X", (0xFFFFFF & colorInt));
        prefs.edit().putString("color", hexColor).apply();
        // Use a lightweight broadcast or throttle updates if needed, 
        // but direct trigger works smoothly if layout is light
        sendBroadcast(new Intent("UPDATE_FLOAT_BUTTON"));
    });
}

private class SimpleSeekBar implements SeekBar.OnSeekBarChangeListener {
    String key; int offset;
    SimpleSeekBar(String k,int o){key=k;offset=o;}
    public void onProgressChanged(SeekBar s,int p,boolean f){
        if(f) getSharedPreferences("float_settings", MODE_PRIVATE).edit().putInt(key,p+offset).apply();
    }
    public void onStartTrackingTouch(SeekBar s){}
    public void onStopTrackingTouch(SeekBar s){
        sendBroadcast(new Intent("UPDATE_FLOAT_BUTTON"));
    }
}

    private boolean isAccessibilityServiceEnabled() {
        Context context = getApplicationContext();
        String service = getPackageName() + "/" + ScreenLockAccessibilityService.class.getName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException ignored) {}

        switch (accessibilityEnabled) {
            case 1:
                String settingValue = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (settingValue != null) {
                    return settingValue.contains(service);
                }
                break;
        }
        return false;
    }

    private void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Find 'Screen Lock Button Service' and turn it ON", Toast.LENGTH_LONG).show();
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            Toast.makeText(this, "Overlay permission already granted!", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestAdminPermission() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Required to lock your screen when you press the floating button.");
            startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
        } else {
            Toast.makeText(this, "Admin permission already granted!", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFloatingButton() {
        Intent serviceIntent = new Intent(this, FloatingButtonService.class);
        if (FloatingButtonService.isRunning) {
            stopService(serviceIntent);
        } else {
            startForegroundService(serviceIntent);
        }
        // Slight delay to let service update its state
        btnToggle.postDelayed(this::updateUI, 300);
    }

    private void pickCustomIcon() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getSharedPreferences("float_settings", MODE_PRIVATE).edit()
                            .putString("custom_icon_uri", uri.toString())
                            .putString("icon", "CUSTOM") // Flag for service
                            .apply();
                    sendBroadcast(new Intent("UPDATE_FLOAT_BUTTON"));
                    Toast.makeText(this, "Custom icon applied!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to get icon permission", Toast.LENGTH_SHORT).show();
                }
            }
        }
        updateUI();
    }
}
