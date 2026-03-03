package com.projectz.cannyminute.ui.cooldown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectz.cannyminute.R
import com.projectz.cannyminute.ui.theme.CannyMinuteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CooldownActivity : ComponentActivity() {

    private val viewModel: CooldownViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        isVisible = true
    }

    override fun onStop() {
        isVisible = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CannyMinuteTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                Surface(modifier = Modifier.fillMaxSize()) {
                    CooldownScreen(
                        uiState = uiState,
                        onNeedsChanged = viewModel::updateNeedsChecked,
                        onAffordableChanged = viewModel::updateAffordableChecked,
                        onCheaperChanged = viewModel::updateCheaperChecked,
                        onClose = { finishAndRemoveTask() },
                        onSaveForLater = {
                            viewModel.suppressSourcePackageAfterContinue()
                            finishAndRemoveTask()
                        },
                        onContinue = {
                            viewModel.suppressSourcePackageAfterContinue()
                            finishAndRemoveTask()
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_CONFIDENCE = "extra_confidence"

        @Volatile
        var isVisible: Boolean = false
            private set
    }
}

@Composable
private fun CooldownScreen(
    uiState: CooldownUiState,
    onNeedsChanged: (Boolean) -> Unit,
    onAffordableChanged: (Boolean) -> Unit,
    onCheaperChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSaveForLater: () -> Unit,
    onContinue: () -> Unit
) {
    val background = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            Color(0xFFFFFFFF)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Image(
            painter = painterResource(id = R.drawable.cannyminute_logo),
            contentDescription = "CannyMinute logo",
            modifier = Modifier.size(96.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Quick check",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Take a minute, you can still buy it after.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Timer: ${uiState.remainingSeconds}s",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Detected app: ${uiState.sourcePackageName.ifBlank { "Unknown" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                QuestionRow(
                    text = "Do I need it?",
                    checked = uiState.needsChecked,
                    onCheckedChange = onNeedsChanged
                )
                QuestionRow(
                    text = "Can I afford it?",
                    checked = uiState.affordableChecked,
                    onCheckedChange = onAffordableChanged
                )
                QuestionRow(
                    text = "Have I checked if it is cheaper elsewhere?",
                    checked = uiState.cheaperChecked,
                    onCheckedChange = onCheaperChanged
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (uiState.remainingSeconds > 0) {
                        "Continue unlocks in ${uiState.remainingSeconds}s."
                    } else {
                        "You can continue whenever you are ready."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onContinue,
            enabled = uiState.canContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }

        OutlinedButton(
            onClick = onSaveForLater,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save for later")
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun QuestionRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

