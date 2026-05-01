package ro.cosminmihu.ktor.monitor.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.ktor.http.HttpMethod
import org.jetbrains.compose.resources.stringResource
import ro.cosminmihu.ktor.monitor.ui.Dimens
import ro.cosminmihu.ktor.monitor.ui.resources.Res
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_filter_method
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_filter_response_code
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_filter_sort_response_size
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_filter_sort_size_asc
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_filter_sort_size_desc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterChipsRow(
    filter: ListUiState.Filter,
    availableMethods: Set<String>,
    onToggleMethod: (String) -> Unit,
    onToggleResponseCodeRange: (ListUiState.Filter.ResponseCodeRange) -> Unit,
    onSetSizeSort: (ListUiState.Filter.SizeSort?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Small),
        horizontalArrangement = Arrangement.spacedBy(Dimens.ExtraSmall),
    ) {
        // Chip 1: Method
        if (availableMethods.isNotEmpty()) {
            var methodMenuExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = filter.methods.isNotEmpty(),
                    onClick = { methodMenuExpanded = true },
                    label = { Text(stringResource(Res.string.ktor_filter_method)) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenu(
                    expanded = methodMenuExpanded,
                    onDismissRequest = { methodMenuExpanded = false },
                ) {
                    val methodOrder = HttpMethod.DefaultMethods.map { it.value }
                    availableMethods.sortedWith(compareBy { methodOrder.indexOf(it.uppercase()).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }).forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method) },
                            leadingIcon = {
                                Checkbox(
                                    checked = method in filter.methods,
                                    onCheckedChange = null,
                                )
                            },
                            onClick = { onToggleMethod(method) },
                        )
                    }
                }
            }
        }

        // Chip 2: Response code range
        var codeMenuExpanded by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected = filter.responseCodeRanges.isNotEmpty(),
                onClick = { codeMenuExpanded = true },
                label = { Text(stringResource(Res.string.ktor_filter_response_code)) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenu(
                expanded = codeMenuExpanded,
                onDismissRequest = { codeMenuExpanded = false },
            ) {
                ListUiState.Filter.ResponseCodeRange.entries.forEach { range ->
                    DropdownMenuItem(
                        text = { Text(range.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        leadingIcon = {
                            Checkbox(
                                checked = range in filter.responseCodeRanges,
                                onCheckedChange = null,
                            )
                        },
                        onClick = { onToggleResponseCodeRange(range) },
                    )
                }
            }
        }

        // Chip 3: Sort by size
        var sortMenuExpanded by remember { mutableStateOf(false) }
        val sortLabel = when (filter.sizeSort) {
            ListUiState.Filter.SizeSort.ASC -> stringResource(Res.string.ktor_filter_sort_size_asc)
            ListUiState.Filter.SizeSort.DESC -> stringResource(Res.string.ktor_filter_sort_size_desc)
            null -> stringResource(Res.string.ktor_filter_sort_response_size)
        }
        Box {
            FilterChip(
                selected = filter.sizeSort != null,
                onClick = { sortMenuExpanded = true },
                label = { Text(sortLabel) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.ktor_filter_sort_size_asc), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    leadingIcon = {
                        RadioButton(
                            selected = filter.sizeSort == ListUiState.Filter.SizeSort.ASC,
                            onClick = null,
                        )
                    },
                    onClick = {
                        onSetSizeSort(if (filter.sizeSort == ListUiState.Filter.SizeSort.ASC) null else ListUiState.Filter.SizeSort.ASC)
                        sortMenuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.ktor_filter_sort_size_desc), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    leadingIcon = {
                        RadioButton(
                            selected = filter.sizeSort == ListUiState.Filter.SizeSort.DESC,
                            onClick = null,
                        )
                    },
                    onClick = {
                        onSetSizeSort(if (filter.sizeSort == ListUiState.Filter.SizeSort.DESC) null else ListUiState.Filter.SizeSort.DESC)
                        sortMenuExpanded = false
                    },
                )
            }
        }
    }
}
