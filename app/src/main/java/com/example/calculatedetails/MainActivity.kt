package com.example.calculatedetails

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.calculatedetails.ui.theme.CalculateDetailsTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val DEFAULT_SHEET_WIDTH = 300
private val SHEET_WIDTH_KEY = intPreferencesKey("sheet_width")
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dataStore = applicationContext.settingsDataStore
        setContent {
            CalculateDetailsTheme {
                SheetCalculatorApp(dataStore = dataStore)
            }
        }
    }
}

@Composable
fun SheetCalculatorApp(dataStore: DataStore<Preferences>) {
    val scope = rememberCoroutineScope()
    val sheetWidthFlow = remember(dataStore) {
        dataStore.data.map { prefs -> prefs[SHEET_WIDTH_KEY] ?: DEFAULT_SHEET_WIDTH }
    }
    val sheetWidth by sheetWidthFlow.collectAsState(initial = DEFAULT_SHEET_WIDTH)

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        SheetCalculatorScreen(
            sheetWidth = sheetWidth,
            onSheetWidthUpdate = { newWidth ->
                scope.launch {
                    dataStore.edit { prefs ->
                        prefs[SHEET_WIDTH_KEY] = newWidth
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
fun SheetCalculatorScreen(
    sheetWidth: Int,
    onSheetWidthUpdate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthInput by rememberSaveable { mutableStateOf(sheetWidth.toString()) }
    var lengthInput by rememberSaveable { mutableStateOf("") }
    var countInput by rememberSaveable { mutableStateOf("") }
    val details = remember { mutableStateListOf<PartDetail>() }
    var calculationResult by remember { mutableStateOf<List<SheetAllocation>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sheetWidth) {
        widthInput = sheetWidth.toString()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Ширина листа, см",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = widthInput,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } && newValue.length <= 5) {
                    widthInput = newValue
                } else if (newValue.isEmpty()) {
                    widthInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Например, 280") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Button(
            onClick = {
                val parsedWidth = widthInput.toIntOrNull()
                if (parsedWidth == null || parsedWidth <= 0) {
                    errorMessage = "Введите корректную ширину листа."
                    return@Button
                }
                errorMessage = null
                calculationResult = emptyList()
                onSheetWidthUpdate(parsedWidth)
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Сохранить ширину")
        }

        Divider()

        Text(
            text = "Добавление деталей",
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = lengthInput,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() }) {
                        lengthInput = newValue
                    } else if (newValue.isEmpty()) {
                        lengthInput = ""
                    }
                },
                modifier = Modifier
                    .weight(1f),
                label = { Text("Длина, см") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = countInput,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() }) {
                        countInput = newValue
                    } else if (newValue.isEmpty()) {
                        countInput = ""
                    }
                },
                modifier = Modifier
                    .weight(1f),
                label = { Text("Количество") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        Button(
            onClick = {
                val length = lengthInput.toIntOrNull()
                val count = countInput.toIntOrNull()
                when {
                    length == null || length <= 0 -> errorMessage = "Введите длину детали больше нуля."
                    count == null || count <= 0 -> errorMessage = "Введите количество деталей больше нуля."
                    else -> {
                        errorMessage = null
                        val existingIndex = details.indexOfFirst { it.length == length }
                        if (existingIndex >= 0) {
                            val existing = details[existingIndex]
                            details[existingIndex] = existing.copy(count = existing.count + count)
                        } else {
                            details.add(PartDetail(length = length, count = count))
                        }
                        lengthInput = ""
                        countInput = ""
                        calculationResult = emptyList()
                    }
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Добавить деталь")
        }

        if (details.isEmpty()) {
            Text(
                text = "Пока нет добавленных деталей.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details.forEachIndexed { index, detail ->
                    DetailRow(
                        detail = detail,
                        onRemove = {
                            details.removeAt(index)
                            calculationResult = emptyList()
                        }
                    )
                }
                OutlinedButton(
                    onClick = {
                        details.clear()
                        calculationResult = emptyList()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Очистить список")
                }
            }
        }

        Button(
            onClick = {
                when {
                    sheetWidth <= 0 -> errorMessage = "Сначала задайте ширину листа."
                    details.isEmpty() -> errorMessage = "Добавьте хотя бы одну деталь."
                    else -> {
                        val invalidDetail = details.firstOrNull { it.length > sheetWidth }
                        if (invalidDetail != null) {
                            errorMessage =
                                "Деталь ${invalidDetail.length} см не помещается в лист шириной $sheetWidth см."
                            calculationResult = emptyList()
                        } else {
                            calculationResult = calculateSheetAllocations(
                                sheetWidth = sheetWidth,
                                parts = details.toList()
                            )
                            errorMessage = null
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Рассчитать раскрой")
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (calculationResult.isNotEmpty()) {
            Text(
                text = "Требуемое количество листов: ${calculationResult.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                calculationResult.forEach { sheet ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Лист ${sheet.sheetNumber}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            sheet.details.forEach { detail ->
                                Text("${detail.length} см — ${detail.count} шт.")
                            }
                            Text(
                                text = "Остаток: ${sheet.remaining} см",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(detail: PartDetail, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${detail.length} см",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${detail.count} шт.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(onClick = onRemove) {
                Text("Удалить")
            }
        }
    }
}

data class PartDetail(
    val length: Int,
    val count: Int
)

data class DetailGroup(
    val length: Int,
    val count: Int
)

data class SheetAllocation(
    val sheetNumber: Int,
    val remaining: Int,
    val details: List<DetailGroup>
)

fun calculateSheetAllocations(
    sheetWidth: Int,
    parts: List<PartDetail>
): List<SheetAllocation> {
    if (sheetWidth <= 0 || parts.isEmpty()) return emptyList()
    data class SheetState(
        var remaining: Int,
        val placements: MutableList<Int>
    )
    val items = parts
        .flatMap { detail -> List(detail.count) { detail.length } }
        .sortedDescending()
    val sheets = mutableListOf<SheetState>()

    items.forEach { length ->
        var placed = false
        for (sheet in sheets) {
            if (length <= sheet.remaining) {
                sheet.placements.add(length)
                sheet.remaining -= length
                placed = true
                break
            }
        }
        if (!placed) {
            sheets.add(
                SheetState(
                    remaining = sheetWidth - length,
                    placements = mutableListOf(length)
                )
            )
        }
    }

    return sheets.mapIndexed { index, sheet ->
        val detailGroups = sheet.placements
            .groupingBy { it }
            .eachCount()
            .map { (length, count) -> DetailGroup(length = length, count = count) }
            .sortedByDescending { it.length }
        SheetAllocation(
            sheetNumber = index + 1,
            remaining = sheet.remaining.coerceAtLeast(0),
            details = detailGroups
        )
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
fun SheetCalculatorPreview() {
    CalculateDetailsTheme {
        SheetCalculatorScreen(
            sheetWidth = 275,
            onSheetWidthUpdate = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
