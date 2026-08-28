package com.dk.tvplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

enum class FilterType {
    CATEGORY, FAVORITE, LANGUAGE, DURATION, QUALITY
}

data class FilterState(
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val showFavoritesOnly: Boolean = false,
    val selectedLanguage: String? = null,
    val minDuration: Int = 0,
    val maxDuration: Int = Int.MAX_VALUE,
    val selectedQuality: String? = null
)

@Composable
fun AdvancedSearchFilterBar(
    modifier: Modifier = Modifier,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onFiltersChanged: (FilterState) -> Unit,
    categories: List<String> = emptyList(),
    languages: List<String> = listOf("English", "Spanish", "French", "German", "Chinese"),
    qualities: List<String> = listOf("720p", "1080p", "2K", "4K"),
    showAdvancedOptions: Boolean = false
) {
    var expandAdvanced by remember { mutableStateOf(false) }
    var filterState by remember { mutableStateOf(FilterState(searchQuery = searchQuery)) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { newQuery ->
                    onSearchQueryChanged(newQuery)
                    filterState = filterState.copy(searchQuery = newQuery)
                    onFiltersChanged(filterState)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                placeholder = { Text("Search channels...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            "Clear",
                            modifier = Modifier.clickable { onSearchQueryChanged("") }
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.DarkGray,
                    focusedContainerColor = Color.DarkGray
                ),
                singleLine = true
            )

            if (showAdvancedOptions) {
                IconButton(onClick = { expandAdvanced = !expandAdvanced }) {
                    Icon(
                        if (expandAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Advanced Options"
                    )
                }
            }
        }

        if (expandAdvanced && showAdvancedOptions) {
            AdvancedFilterOptions(
                filterState = filterState,
                categories = categories,
                languages = languages,
                qualities = qualities,
                onFilterStateChanged = { newState ->
                    filterState = newState
                    onFiltersChanged(newState)
                }
            )
        }
    }
}

@Composable
fun AdvancedFilterOptions(
    modifier: Modifier = Modifier,
    filterState: FilterState,
    categories: List<String>,
    languages: List<String>,
    qualities: List<String>,
    onFilterStateChanged: (FilterState) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text("Filter Options", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))

        if (categories.isNotEmpty()) {
            Text("Category", style = MaterialTheme.typography.labelSmall)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = filterState.selectedCategory == category,
                        onClick = {
                            onFilterStateChanged(
                                filterState.copy(
                                    selectedCategory = if (filterState.selectedCategory == category) null else category
                                )
                            )
                        },
                        label = { Text(category) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterState.showFavoritesOnly,
                onClick = {
                    onFilterStateChanged(
                        filterState.copy(showFavoritesOnly = !filterState.showFavoritesOnly)
                    )
                },
                label = { Text("Favorites") },
                leadingIcon = { Icon(Icons.Default.Favorite, "Favorites") }
            )
        }
        Spacer(Modifier.height(12.dp))

        if (languages.isNotEmpty()) {
            Text("Language", style = MaterialTheme.typography.labelSmall)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(languages) { language ->
                    FilterChip(
                        selected = filterState.selectedLanguage == language,
                        onClick = {
                            onFilterStateChanged(
                                filterState.copy(
                                    selectedLanguage = if (filterState.selectedLanguage == language) null else language
                                )
                            )
                        },
                        label = { Text(language) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (qualities.isNotEmpty()) {
            Text("Quality", style = MaterialTheme.typography.labelSmall)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(qualities) { quality ->
                    FilterChip(
                        selected = filterState.selectedQuality == quality,
                        onClick = {
                            onFilterStateChanged(
                                filterState.copy(
                                    selectedQuality = if (filterState.selectedQuality == quality) null else quality
                                )
                            )
                        },
                        label = { Text(quality) }
                    )
                }
            }
        }
    }
}
