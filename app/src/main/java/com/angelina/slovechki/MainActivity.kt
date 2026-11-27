package com.angelina.slovechki

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private val Pine = Color(0xFF1E493C)
private val PineSoft = Color(0xFFE7EEE9)
private val Paper = Color(0xFFF5F2EB)
private val Ink = Color(0xFF1F2924)
private val Muted = Color(0xFF69736D)
private val Coral = Color(0xFFD87954)
private val Correct = Color(0xFF327454)
private val Incorrect = Color(0xFFB4463E)

private data class VocabWord(
    val id: String,
    val unit: Int,
    val term: String,
    val partOfSpeech: String,
    val translation: String,
)

private data class UnitInfo(val id: Int, val title: String, val count: Int)

private data class Vocabulary(val units: List<UnitInfo>, val words: List<VocabWord>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vocabulary = readVocabulary(this)
        val preferences = getSharedPreferences("study_progress", Context.MODE_PRIVATE)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Pine,
                    onPrimary = Color.White,
                    secondary = Coral,
                    onSecondary = Color.White,
                    background = Paper,
                    onBackground = Ink,
                    surface = Color.White,
                    onSurface = Ink,
                    error = Incorrect,
                ),
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
                    SlovechkiApp(vocabulary, preferences)
                }
            }
        }
    }
}

private fun readVocabulary(context: Context): Vocabulary {
    val root = JSONObject(context.assets.open("vocabulary.json").bufferedReader().use { it.readText() })
    val unitsJson = root.getJSONArray("units")
    val units = (0 until unitsJson.length()).map { index ->
        val unit = unitsJson.getJSONObject(index)
        UnitInfo(unit.getInt("id"), unit.getString("title"), unit.getInt("count"))
    }
    val wordsJson = root.getJSONArray("words")
    val words = (0 until wordsJson.length()).map { index ->
        val word = wordsJson.getJSONObject(index)
        VocabWord(
            id = word.getString("id"),
            unit = word.getInt("unit"),
            term = word.getString("term"),
            partOfSpeech = word.optString("partOfSpeech", ""),
            translation = word.optString("translation", "").trim(),
        )
    }
    return Vocabulary(units, words)
}

@Composable
private fun SlovechkiApp(vocabulary: Vocabulary, preferences: android.content.SharedPreferences) {
    var selectedUnits by remember {
        val saved = preferences.getStringSet("selected_units", null)
        val initial = saved?.mapNotNull { it.toIntOrNull() }?.toSet() ?: vocabulary.units.map { it.id }.toSet()
        mutableStateOf(initial)
    }
    var answered by remember { mutableIntStateOf(preferences.getInt("answered", 0)) }
    var correct by remember { mutableIntStateOf(preferences.getInt("correct", 0)) }
    var studying by remember { mutableStateOf(false) }

    val selectedWords = remember(vocabulary.words, selectedUnits) {
        vocabulary.words.filter { it.unit in selectedUnits && it.translation.isNotBlank() }
    }

    if (studying && selectedWords.isNotEmpty()) {
        StudyScreen(
            words = selectedWords,
            onBack = { studying = false },
            onAnswer = { wasCorrect ->
                answered += 1
                if (wasCorrect) correct += 1
                preferences.edit().putInt("answered", answered).putInt("correct", correct).apply()
            },
        )
    } else {
        HomeScreen(
            vocabulary = vocabulary,
            selectedUnits = selectedUnits,
            answered = answered,
            correct = correct,
            onToggleUnit = { unitId ->
                selectedUnits = if (unitId in selectedUnits) selectedUnits - unitId else selectedUnits + unitId
                preferences.edit().putStringSet("selected_units", selectedUnits.map { it.toString() }.toMutableSet()).apply()
            },
            onSelectAll = {
                selectedUnits = vocabulary.units.map { it.id }.toSet()
                preferences.edit().putStringSet("selected_units", selectedUnits.map { it.toString() }.toMutableSet()).apply()
            },
            onClear = {
                selectedUnits = emptySet()
                preferences.edit().putStringSet("selected_units", mutableSetOf()).apply()
            },
            onStart = { if (selectedWords.isNotEmpty()) studying = true },
        )
    }
}

@Composable
private fun HomeScreen(
    vocabulary: Vocabulary,
    selectedUnits: Set<Int>,
    answered: Int,
    correct: Int,
    onToggleUnit: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onStart: () -> Unit,
) {
    val translatedTotal = vocabulary.words.count { it.translation.isNotBlank() }
    val available = vocabulary.words.count { it.unit in selectedUnits && it.translation.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("СЛОВЕЧКИ", color = Pine, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Повторяй слова\nв своём темпе", color = Ink, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
        Text("Выбери юниты для тренировки. Слова будут идти вперемешку с вариантами перевода.", color = Muted, fontSize = 16.sp, lineHeight = 23.sp)

        Card(
            colors = CardDefaults.cardColors(containerColor = Pine),
            shape = RoundedCornerShape(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("КАРТОЧЕК В ПРИЛОЖЕНИИ", color = Color.White.copy(alpha = 0.76f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("${vocabulary.words.size}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("$translatedTotal с переводом", color = Color.White.copy(alpha = 0.84f), fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ТВОЯ ПРАКТИКА", color = Color.White.copy(alpha = 0.76f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("$correct / $answered", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("верно / отвечено", color = Color.White.copy(alpha = 0.84f), fontSize = 13.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ЮНИТЫ", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Все", color = Pine, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onSelectAll).padding(6.dp))
                Text("Снять", color = Muted, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onClear).padding(6.dp))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vocabulary.units.forEach { unit ->
                val selected = unit.id in selectedUnits
                val translated = vocabulary.words.count { it.unit == unit.id && it.translation.isNotBlank() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = selected, role = Role.Checkbox) { onToggleUnit(unit.id) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = selected, onCheckedChange = null)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Unit ${unit.id}: ${unit.title.lowercase().replaceFirstChar { it.uppercase() }}", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("$translated из ${unit.count} с переводом", color = Muted, fontSize = 12.sp)
                    }
                    if (selected) {
                        Surface(color = PineSoft, shape = RoundedCornerShape(50)) {
                            Text("${unit.id.toString().padStart(2, '0')}", color = Pine, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Text(
                text = if (available == 0) "В выбранных юнитах пока нет слов с переводом. Когда переводы будут добавлены, тренировка станет доступна." else "В тренировке: $available слов из выбранных юнитов.",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(16.dp),
            )
        }

        Button(
            onClick = onStart,
            enabled = available > 0,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Pine, disabledContainerColor = Color(0xFFB8C3BC)),
        ) {
            Text(if (available > 0) "Начать повторение" else "Жду переводы", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text("Повторять можно сколько угодно: после последнего слова круг начнётся снова.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StudyScreen(words: List<VocabWord>, onBack: () -> Unit, onAnswer: (Boolean) -> Unit) {
    val deck = remember(words) { words.shuffled() }
    var questionIndex by remember { mutableIntStateOf(0) }
    var reverse by remember { mutableStateOf(false) }
    var selectedAnswerId by remember { mutableStateOf<String?>(null) }
    val current = deck[questionIndex % deck.size]
    val choices = remember(words, questionIndex, reverse, current.id) {
        fun answerText(word: VocabWord) = if (reverse) word.term else word.translation
        val alternatives = deck
            .filter { it.id != current.id }
            .shuffled()
            .filter { answerText(it) != answerText(current) }
            .distinctBy(::answerText)
            .take(3)
        (listOf(current) + alternatives).shuffled()
    }
    val prompt = if (reverse) current.translation else current.term
    val correctText = if (reverse) current.term else current.translation
    val progress = ((questionIndex % deck.size) + 1).toFloat() / deck.size

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.size(44.dp).clickable(onClick = onBack),
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Pine)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("ТРЕНИРОВКА", color = Pine, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Вопрос ${questionIndex + 1} из ${deck.size}", color = Muted, fontSize = 13.sp)
            }
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Coral, trackColor = Color(0xFFE6E2D9))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !reverse,
                onClick = { if (reverse) { reverse = false; selectedAnswerId = null } },
                label = { Text("Английский → русский", fontSize = 12.sp) },
            )
            FilterChip(
                selected = reverse,
                onClick = { if (!reverse) { reverse = true; selectedAnswerId = null } },
                label = { Text("Русский → английский", fontSize = 12.sp) },
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(26.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("СОПОСТАВЬ С ПЕРЕВОДОМ", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text(prompt, color = Ink, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                if (!reverse && current.partOfSpeech.isNotBlank()) {
                    Text("${current.partOfSpeech} · Unit ${current.unit}", color = Muted, fontSize = 13.sp)
                } else if (reverse) {
                    Text("Unit ${current.unit}", color = Muted, fontSize = 13.sp)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.forEach { option ->
                val optionText = if (reverse) option.term else option.translation
                val isSelected = selectedAnswerId == option.id
                val isCorrectOption = option.id == current.id
                val background = when {
                    selectedAnswerId == null -> Color.White
                    isCorrectOption -> Color(0xFFE3F0E7)
                    isSelected -> Color(0xFFF8E6E3)
                    else -> Color.White
                }
                val borderColor = when {
                    selectedAnswerId == null -> Color(0xFFE5E1D8)
                    isCorrectOption -> Correct
                    isSelected -> Incorrect
                    else -> Color(0xFFE5E1D8)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = selectedAnswerId == null) {
                        selectedAnswerId = option.id
                        onAnswer(option.id == current.id)
                    },
                    color = background,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, borderColor),
                ) {
                    Text(optionText, color = Ink, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp))
                }
            }
        }

        if (selectedAnswerId != null) {
            val wasCorrect = selectedAnswerId == current.id
            Text(
                if (wasCorrect) "Верно!" else "Правильный ответ: $correctText",
                color = if (wasCorrect) Correct else Incorrect,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = { questionIndex += 1; selectedAnswerId = null },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pine),
            ) { Text("Следующее слово", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}
