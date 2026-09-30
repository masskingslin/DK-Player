package com.dk.tvplayer.ui.parental

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.util.ParentalControl
import com.dk.tvplayer.util.PinResult

private enum class PinFlow { SetNew, ChangeVerifyOld, ChangeNew, DisableRestrict, DisableSafe, EnableSetPinRestrict, EnableSetPinSafe }

/** Settings → Parental control: change PIN, restrict Settings, Safe mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val hasPin by ParentalControl.hasPin.collectAsState()
    val restrict by ParentalControl.restrictSettings.collectAsState()
    val safeMode by ParentalControl.safeMode.collectAsState()
    var flow by remember { mutableStateOf<PinFlow?>(null) }

    fun toggleRestrict(on: Boolean) {
        if (on) {
            if (hasPin) ParentalControl.setRestrictSettings(true) else flow = PinFlow.EnableSetPinRestrict
        } else {
            flow = PinFlow.DisableRestrict
        }
    }

    fun toggleSafe(on: Boolean) {
        if (on) {
            if (hasPin) ParentalControl.setSafeMode(true) else flow = PinFlow.EnableSetPinSafe
        } else {
            flow = PinFlow.DisableSafe
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parental control") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { flow = if (hasPin) PinFlow.ChangeVerifyOld else PinFlow.SetNew }
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(if (hasPin) "Change your PIN code" else "Set a PIN code", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A ${ParentalControl.PIN_DIGITS}-digit code. It's stored only as a salted hash.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OptionRow(
                title = "Restrict settings access",
                subtitle = "Ask for the PIN to open Settings",
                checked = restrict,
                onToggle = { toggleRestrict(!restrict) }
            )
            OptionRow(
                title = "Safe mode",
                subtitle = "When activated, files cannot be deleted or playlists modified without entering the PIN",
                checked = safeMode,
                onToggle = { toggleSafe(!safeMode) }
            )
            Text(
                "If you forget the PIN, the only way back in is to clear DK Player's data from Android's app settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    when (flow) {
        PinFlow.SetNew, PinFlow.EnableSetPinRestrict, PinFlow.EnableSetPinSafe -> NewPinDialog(
            title = "Set a PIN code",
            onDismiss = { flow = null },
            onPin = { pin ->
                ParentalControl.setPin(pin)
                when (flow) {
                    PinFlow.EnableSetPinRestrict -> ParentalControl.setRestrictSettings(true)
                    PinFlow.EnableSetPinSafe -> ParentalControl.setSafeMode(true)
                    else -> Unit
                }
                flow = null
                Toast.makeText(context, "PIN saved", Toast.LENGTH_SHORT).show()
            }
        )
        PinFlow.ChangeVerifyOld -> PinEntryDialog(
            title = "Enter your current PIN",
            onDismiss = { flow = null },
            onVerified = { flow = PinFlow.ChangeNew }
        )
        PinFlow.ChangeNew -> NewPinDialog(
            title = "Enter a new PIN",
            onDismiss = { flow = null },
            onPin = { pin ->
                ParentalControl.setPin(pin)
                flow = null
                Toast.makeText(context, "PIN changed", Toast.LENGTH_SHORT).show()
            }
        )
        PinFlow.DisableRestrict -> PinEntryDialog(
            title = "Enter your PIN",
            onDismiss = { flow = null },
            onVerified = { ParentalControl.setRestrictSettings(false); flow = null }
        )
        PinFlow.DisableSafe -> PinEntryDialog(
            title = "Enter your PIN",
            onDismiss = { flow = null },
            onVerified = { ParentalControl.setSafeMode(false); flow = null }
        )
        null -> Unit
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun PinField(value: String, onValueChange: (String) -> Unit, label: String, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(ParentalControl.PIN_DIGITS)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Verifies the existing PIN, then calls [onVerified]. */
@Composable
fun PinEntryDialog(title: String, onDismiss: () -> Unit, onVerified: () -> Unit, message: String? = null) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        when (val r = ParentalControl.verifyPin(pin)) {
            PinResult.Ok -> onVerified()
            is PinResult.Wrong -> { error = "Wrong PIN (${r.triesLeft} tries left)"; pin = "" }
            is PinResult.Locked -> { error = "Too many tries. Wait ${r.seconds}s"; pin = "" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                PinField(pin, { pin = it; error = null }, "PIN", isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = pin.length == ParentalControl.PIN_DIGITS) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Asks for a new PIN twice and hands it to [onPin] when both match. */
@Composable
private fun NewPinDialog(title: String, onDismiss: () -> Unit, onPin: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val mismatch = second.length == ParentalControl.PIN_DIGITS && first != second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(first, { first = it }, "New PIN (${ParentalControl.PIN_DIGITS} digits)")
                PinField(second, { second = it }, "Repeat PIN", isError = mismatch)
                if (mismatch) Text("The PINs don't match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPin(first) },
                enabled = first.length == ParentalControl.PIN_DIGITS && first == second
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** App-wide prompt for Safe-mode actions; place once near the root. */
@Composable
fun PinPromptHost() {
    val pending by ParentalControl.pending.collectAsState()
    pending?.let { request ->
        PinEntryDialog(
            title = "Enter your PIN",
            message = request.reason,
            onDismiss = { ParentalControl.clearPending() },
            onVerified = {
                ParentalControl.clearPending()
                request.onSuccess()
            }
        )
    }
}

/** Full-screen PIN entry shown instead of Settings while "Restrict settings access" is on. */
@Composable
fun SettingsPinGate(onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        when (val r = ParentalControl.verifyPin(pin)) {
            PinResult.Ok -> onUnlocked()
            is PinResult.Wrong -> { error = "Wrong PIN (${r.triesLeft} tries left)"; pin = "" }
            is PinResult.Locked -> { error = "Too many tries. Wait ${r.seconds}s"; pin = "" }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Settings are locked", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("Enter your PIN to continue", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        PinField(pin, { pin = it; error = null }, "PIN", isError = error != null)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = ::submit, enabled = pin.length == ParentalControl.PIN_DIGITS) { Text("Unlock") }
    }
}
