package com.screenlock.floatbutton;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.content.SharedPreferences;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_DEVICE_ADMIN = 1002;

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

        btnOverlay.setText(overlayGranted ? "✅ Overlay Permission Granted" : "1. Grant Overlay Permission");
        btnAdmin.setText(adminGranted ? "✅ Admin Permission Granted" : "2. Grant Device Admin");
        if (btnAccessibility != null) {
            btnAccessibility.setText(accessibilityGranted ? "✅ Accessibility Granted (Smart Lock)" : "3. Grant Accessibility (Enable Fingerprint)");
        }

        // Allow starting if at least Overlay is granted and either Admin or Accessibility
        boolean allGranted = overlayGranted && (adminGranted || accessibilityGranted);
        btnToggle.setEnabled(allGranted);

        if (serviceRunning) {
            btnToggle.setText("STOP Floating Button");
            tvStatus.setText("● Floating button is ACTIVE");
        } else {
            btnToggle.setText("START Floating Button");
            tvStatus.setText("○ Floating button is INACTIVE");
        }
    }


private void setupSettings() {
    SharedPreferences prefs = getSharedPreferences("float_settings", MODE_PRIVATE);
    SeekBar size = findViewById(R.id.seekSize);
    SeekBar alpha = findViewById(R.id.seekTransparency);
    Spinner icon = findViewById(R.id.spinnerIcon);

    size.setProgress(prefs.getInt("size",100)-50);
    alpha.setProgress(prefs.getInt("alpha",80)-10);

    String[] icons = {" ","o","🔒","⚡","⬤","■"};
    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, icons);
    adapter.setDropDownViewResource(R.layout.spinner_item);
    icon.setAdapter(adapter);
    String saved = prefs.getString("icon","⬤");
    for(int i=0;i<icons.length;i++){ if(icons[i].equals(saved)) icon.setSelection(i);}

    size.setOnSeekBarChangeListener(new SimpleSeekBar("size",50));
    alpha.setOnSeekBarChangeListener(new SimpleSeekBar("alpha",10));
    icon.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
        public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
            prefs.edit().putString("icon", icons[pos]).apply();
            sendBroadcast(new Intent("UPDATE_FLOAT_BUTTON"));
        }
        public void onNothingSelected(android.widget.AdapterView<?> p) {}
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

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                return settingValue.contains(service);
            }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
        // Slight delay to let service update its state
        btnToggle.postDelayed(this::updateUI, 300);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        updateUI();
    }
}
