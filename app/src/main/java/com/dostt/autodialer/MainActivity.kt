package com.dostt.autodialer

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var config: DialerConfig
    private lateinit var swMasterToggle: SwitchMaterial
    private lateinit var tvServiceStatus: TextView
    private lateinit var etRetryDelayMs: EditText
    private lateinit var etMaxAttempts: EditText
    private lateinit var cbCoordFallback: CheckBox
    private lateinit var cbDisableOnConnect: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = DialerConfig(this)
        swMasterToggle = findViewById(R.id.swMasterToggle)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        etRetryDelayMs = findViewById(R.id.etRetryDelayMs)
        etMaxAttempts = findViewById(R.id.etMaxAttempts)
        cbCoordFallback = findViewById(R.id.cbCoordFallback)
        cbDisableOnConnect = findViewById(R.id.cbDisableOnConnect)

        swMasterToggle.isChecked = config.isMasterEnabled
        etRetryDelayMs.setText(config.retryDelayMs.toString())
        etMaxAttempts.setText(config.maxAttempts.toString())
        cbCoordFallback.isChecked = config.useCoordinateFallback
        cbDisableOnConnect.isChecked = config.disableServiceOnConnect

        swMasterToggle.setOnCheckedChangeListener { _, isChecked ->
            config.isMasterEnabled = isChecked
            DosttAutoDialService.instance?.setMasterEnabled(isChecked)
            if (!isChecked) {
                config.isArmed = false
                Toast.makeText(this, "Auto-Dialer switched OFF.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Auto-Dialer switched ON.", Toast.LENGTH_SHORT).show()
            }
            refreshServiceStatus()
        }

        findViewById<Button>(R.id.btnAccessibilitySettings).setOnClickListener {
            openAccessibilitySettings()
        }

        findViewById<Button>(R.id.btnStartAndOpenDostt).setOnClickListener {
            saveSettings()
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(
                    this,
                    "Please enable 'Dostt Audio Auto-Dialer' in Accessibility Settings first.",
                    Toast.LENGTH_LONG
                ).show()
                openAccessibilitySettings()
                return@setOnClickListener
            }

            config.isMasterEnabled = true
            swMasterToggle.isChecked = true
            config.isArmed = true
            DosttAutoDialService.instance?.startFromApp()

            val launchIntent = packageManager.getLaunchIntentForPackage(DialerConfig.DOSTT_PACKAGE)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(
                    this,
                    "Dostt app (live.friend.dostt) is not installed on this device.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        findViewById<Button>(R.id.btnStopDialer).setOnClickListener {
            config.isMasterEnabled = false
            config.isArmed = false
            swMasterToggle.isChecked = false
            DosttAutoDialService.instance?.stopDialing("Switched OFF")
            Toast.makeText(this, "Auto-Dialer switched OFF. Zero background activity.", Toast.LENGTH_SHORT).show()
            refreshServiceStatus()
        }

        findViewById<Button>(R.id.btnBankingSafeDisable).setOnClickListener {
            config.isMasterEnabled = false
            config.isArmed = false
            swMasterToggle.isChecked = false
            val service = DosttAutoDialService.instance
            if (service != null) {
                service.disableServiceCompletely()
                Toast.makeText(
                    this,
                    "Accessibility Service shut off completely. Safe to open banking apps.",
                    Toast.LENGTH_LONG
                ).show()
                refreshServiceStatus()
            } else {
                openAccessibilitySettings()
            }
        }

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        swMasterToggle.isChecked = config.isMasterEnabled
        refreshServiceStatus()
    }

    private fun saveSettings() {
        val delay = etRetryDelayMs.text.toString().trim().toLongOrNull() ?: 1500L
        val maxTries = etMaxAttempts.text.toString().trim().toIntOrNull() ?: 15
        config.retryDelayMs = delay
        config.maxAttempts = maxTries
        config.useCoordinateFallback = cbCoordFallback.isChecked
        config.disableServiceOnConnect = cbDisableOnConnect.isChecked
        etRetryDelayMs.setText(config.retryDelayMs.toString())
        etMaxAttempts.setText(config.maxAttempts.toString())
    }

    private fun refreshServiceStatus() {
        val osEnabled = isAccessibilityServiceEnabled()
        val masterOn = config.isMasterEnabled
        when {
            !osEnabled -> {
                tvServiceStatus.text = "Accessibility Service: OFF (Completely unbound)"
                tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            }
            !masterOn -> {
                tvServiceStatus.text = "Accessibility Service: Bound • Switch is OFF (Idle)"
                tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            }
            else -> {
                tvServiceStatus.text = "Accessibility Service: ON (Scoped strictly to Dostt)"
                tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, DosttAutoDialService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
