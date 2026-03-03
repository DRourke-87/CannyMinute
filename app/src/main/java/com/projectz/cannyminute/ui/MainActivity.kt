package com.projectz.cannyminute.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.projectz.cannyminute.R
import com.projectz.cannyminute.accessibility.AccessibilityServiceState
import com.projectz.cannyminute.data.settings.AppSettings
import com.projectz.cannyminute.ui.theme.CannyMinuteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CannyMinuteTheme {
                val settings by viewModel.settingsState.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current
                var accessibilityServiceEnabled by remember {
                    mutableStateOf(AccessibilityServiceState.isCheckoutServiceEnabled(this))
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            accessibilityServiceEnabled =
                                AccessibilityServiceState.isCheckoutServiceEnabled(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        settings = settings,
                        accessibilityServiceEnabled = accessibilityServiceEnabled,
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onProtectionToggle = { enabled ->
                            viewModel.setProtectionEnabled(enabled)
                            if (enabled && !accessibilityServiceEnabled) {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        },
                        onSaveCooldownDuration = viewModel::setCooldownDurationSeconds,
                        onSaveAllowList = viewModel::setAllowListFromCsv
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    settings: AppSettings,
    accessibilityServiceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onProtectionToggle: (Boolean) -> Unit,
    onSaveCooldownDuration: (String) -> Unit,
    onSaveAllowList: (String) -> Unit
) {
    var cooldownInput by rememberSaveable { mutableStateOf(settings.cooldownDurationSeconds.toString()) }
    var allowListInput by rememberSaveable { mutableStateOf(settings.allowListPackages.joinToString(", ")) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val protectionIsOn = settings.protectionEnabled && accessibilityServiceEnabled
    val statusLabel = if (protectionIsOn) "ON" else "OFF"
    val statusTone by animateColorAsState(
        targetValue = if (protectionIsOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "status_tone"
    )
    val statusBackground by animateColorAsState(
        targetValue = if (protectionIsOn) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "status_background"
    )

    LaunchedEffect(settings.cooldownDurationSeconds) {
        cooldownInput = settings.cooldownDurationSeconds.toString()
    }

    LaunchedEffect(settings.allowListPackages) {
        allowListInput = settings.allowListPackages.joinToString(", ")
    }

    val background = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            Color(0xFFFFFFFF)
        )
    )

    Column(
        modifier = Modifier
            .background(background)
            .fillMaxWidth()
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.cannyminute_logo),
            contentDescription = "CannyMinute logo",
            modifier = Modifier
                .size(132.dp)
                .aspectRatio(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A minute before you buy.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "No judgement. Quick check, then you can still buy it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = statusBackground,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusTone
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (accessibilityServiceEnabled) {
                        "Accessibility is enabled."
                    } else {
                        "Accessibility is off, so checkout pause is currently off."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accessibilityServiceEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Protection",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = settings.protectionEnabled,
                        onCheckedChange = onProtectionToggle
                    )
                }
            }
        }

        if (!accessibilityServiceEnabled) {
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Open Accessibility Settings")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = { advancedExpanded = !advancedExpanded }
        ) {
            Text(
                text = if (advancedExpanded) "Hide Advanced Settings" else "Advanced Settings"
            )
        }

        AnimatedVisibility(visible = advancedExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Cooldown duration (seconds)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = cooldownInput,
                        onValueChange = { cooldownInput = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Default: 10") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onSaveCooldownDuration(cooldownInput) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Save cooldown")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Allow list packages (comma-separated)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = allowListInput,
                        onValueChange = { allowListInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        label = { Text("com.example.shopping, com.android.chrome") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onSaveAllowList(allowListInput) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Save allow list")
                    }
                }
            }
        }

        Text(
            text = "On-device only. No data sold. Screen text is stored only if diagnostics is enabled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

