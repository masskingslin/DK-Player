package com.dk.tvplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.util.ThemeConfig

data class ThemePreset(
    val name: String,
    val config: ThemeConfig
)

@Composable
fun AdvancedThemeCustomizer(
    modifier: Modifier = Modifier,
    currentTheme: ThemeConfig,
    onThemeChanged: (ThemeConfig) -> Unit,
    onReset: () -> Unit
) {
    var themeState by remember { mutableStateOf(currentTheme) }
    var showColorPickers by remember { mutableStateOf(false) }
    var expandAdvanced by remember { mutableStateOf(false) }

    val presets = listOf(
        ThemePreset("Dark", ThemeConfig()),
        ThemePreset("Purple", ThemeConfig(
            primaryColor = "#BB86FC",
            secondaryColor = "#03DAC6",
            isDarkMode = true
        )),
        ThemePreset("Blue", ThemeConfig(
            primaryColor = "#2196F3",
            secondaryColor = "#64B5F6",
            isDarkMode = true
        )),
        ThemePreset("Light", ThemeConfig(
            primaryColor = "#6200EE",
            secondaryColor = "#03DAC6",
            isDarkMode = false
        ))
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("Theme Customization", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Presets", style = MaterialTheme.typography.labelSmall)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets) { preset ->
                PresetCard(
                    preset = preset,
                    isSelected = themeState == preset.config,
                    onClick = {
                        themeState = preset.config
                        onThemeChanged(preset.config)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { showColorPickers = !showColorPickers },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Custom Colors")
            }
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Reset")
            }
        }

        if (showColorPickers) {
            Spacer(modifier = Modifier.height(16.dp))
            ColorPickerSection(
                themeConfig = themeState,
                onThemeChanged = { newTheme ->
                    themeState = newTheme
                    onThemeChanged(newTheme)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { expandAdvanced = !expandAdvanced },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
        ) {
            Text(if (expandAdvanced) "Hide Advanced" else "Show Advanced")
        }

        if (expandAdvanced) {
            Spacer(modifier = Modifier.height(16.dp))
            AdvancedThemeOptions(
                themeConfig = themeState,
                onThemeChanged = { newTheme ->
                    themeState = newTheme
                    onThemeChanged(newTheme)
                }
            )
        }
    }
}

@Composable
fun PresetCard(
    modifier: Modifier = Modifier,
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .width(100.dp)
            .clickable(onClick = onClick)
            .background(Color.Gray, RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        android.graphics.Color.parseColor(preset.config.primaryColor).let {
                            Color(it)
                        },
                        CircleShape
                    )
            )
            Text(preset.name, style = MaterialTheme.typography.labelSmall)
            if (isSelected) {
                Icon(Icons.Default.Check, "Selected", tint = Color.Green)
            }
        }
    }
}

@Composable
fun ColorPickerSection(
    modifier: Modifier = Modifier,
    themeConfig: ThemeConfig,
    onThemeChanged: (ThemeConfig) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ColorOption(
            label = "Primary Color",
            color = themeConfig.primaryColor,
            onColorSelected = { newColor ->
                onThemeChanged(themeConfig.copy(primaryColor = newColor))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        ColorOption(
            label = "Secondary Color",
            color = themeConfig.secondaryColor,
            onColorSelected = { newColor ->
                onThemeChanged(themeConfig.copy(secondaryColor = newColor))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        ColorOption(
            label = "Tertiary Color",
            color = themeConfig.tertiaryColor,
            onColorSelected = { newColor ->
                onThemeChanged(themeConfig.copy(tertiaryColor = newColor))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        ColorOption(
            label = "Background Color",
            color = themeConfig.backgroundColor,
            onColorSelected = { newColor ->
                onThemeChanged(themeConfig.copy(backgroundColor = newColor))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        ColorOption(
            label = "Surface Color",
            color = themeConfig.surfaceColor,
            onColorSelected = { newColor ->
                onThemeChanged(themeConfig.copy(surfaceColor = newColor))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        ColorOption(
            label = "Error Color",
            color = themeConfig.errorColor,
            onColorSelected = { newColor ->
                onThemeChanged(themeConfig.copy(errorColor = newColor))
            }
        )
    }
}

@Composable
fun ColorOption(
    modifier: Modifier = Modifier,
    label: String,
    color: String,
    onColorSelected: (String) -> Unit
) {
    var showColorInput by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showColorInput = !showColorInput }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    try {
                        Color(android.graphics.Color.parseColor(color))
                    } catch (e: Exception) {
                        Color.Gray
                    },
                    CircleShape
                )
        )
    }

    if (showColorInput) {
        AlertDialog(
            onDismissRequest = { showColorInput = false },
            title = { Text("Select $label") },
            text = {
                TextField(
                    value = color,
                    onValueChange = onColorSelected,
                    placeholder = { Text("#FF000000") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { showColorInput = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun AdvancedThemeOptions(
    modifier: Modifier = Modifier,
    themeConfig: ThemeConfig,
    onThemeChanged: (ThemeConfig) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Theme Mode", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onThemeChanged(themeConfig.copy(isDarkMode = true)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (themeConfig.isDarkMode) Color.Green else Color.Gray
                )
            ) {
                Text("Dark")
            }
            Button(
                onClick = { onThemeChanged(themeConfig.copy(isDarkMode = false)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!themeConfig.isDarkMode) Color.Green else Color.Gray
                )
            ) {
                Text("Light")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Theme Preview", style = MaterialTheme.typography.labelSmall)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    try {
                        Color(android.graphics.Color.parseColor(themeConfig.backgroundColor))
                    } catch (e: Exception) {
                        Color.Gray
                    },
                    RoundedCornerShape(8.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            try {
                                Color(android.graphics.Color.parseColor(themeConfig.primaryColor))
                            } catch (e: Exception) {
                                Color.Gray
                            },
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            try {
                                Color(android.graphics.Color.parseColor(themeConfig.secondaryColor))
                            } catch (e: Exception) {
                                Color.Gray
                            },
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            try {
                                Color(android.graphics.Color.parseColor(themeConfig.errorColor))
                            } catch (e: Exception) {
                                Color.Gray
                            },
                            CircleShape
                        )
                )
            }
        }
    }
}
