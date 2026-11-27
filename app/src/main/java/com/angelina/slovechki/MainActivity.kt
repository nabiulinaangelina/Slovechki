package com.angelina.slovechki

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.delay
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
    val isPhrase: Boolean,
)

private enum class WordFilter(val label: String) {
    ALL("Все"),
    NOUN("Существительные"),
    VERB("Глаголы"),
    ADJECTIVE("Прилагательные"),
    PHRASE("Фразы");

    fun includes(word: VocabWord): Boolean = when (this) {
        ALL -> true
        NOUN -> word.partOfSpeech == "n"
        VERB -> word.partOfSpeech == "v"
        ADJECTIVE -> word.partOfSpeech == "adj"
        PHRASE -> word.isPhrase
    }
}

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
        val term = word.getString("term")
        VocabWord(
            id = word.getString("id"),
            unit = word.getInt("unit"),
            term = word.getString("term"),
            term = term,
            partOfSpeech = word.optString("partOfSpeech", ""),
            translation = word.optString("translation", "").trim(),
            isPhrase = word.optBoolean("isPhrase", term.trim().split(Regex("\\s+")).size > 1),
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
    var matching by remember { mutableStateOf(false) }
    var wordFilter by remember {
        val saved = preferences.getString("word_filter", WordFilter.ALL.name)
        mutableStateOf(WordFilter.values().firstOrNull { it.name == saved } ?: WordFilter.ALL)
    }

    val selectedWords = remember(vocabulary.words, selectedUnits) {
    val unitWords = remember(vocabulary.words, selectedUnits) {
        vocabulary.words.filter { it.unit in selectedUnits && it.translation.isNotBlank() }
    }
    val selectedWords = remember(unitWords, wordFilter) {
        unitWords.filter(wordFilter::includes)
    }

    if (matching && selectedWords.size >= 5) {
        MatchPairsScreen(
            words = selectedWords,
            onBack = { matching = false },
            onAnswer = { wasCorrect ->
                answered += 1
                if (wasCorrect) correct += 1
                preferences.edit().putInt("answered", answered).putInt("correct", correct).apply()
            },
        )
    } else if (studying && selectedWords.isNotEmpty()) {
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
            wordFilter = wordFilter,
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
            onWordFilterChange = { newFilter ->
                wordFilter = newFilter
                preferences.edit().putString("word_filter", newFilter.name).apply()
            },
            onStart = { if (selectedWords.isNotEmpty()) studying = true },
            onStartMatching = { if (selectedWords.size >= 5) matching = true },
        )
    }
}

@Composable
private fun HomeScreen(
    vocabulary: Vocabulary,
    selectedUnits: Set<Int>,
    wordFilter: WordFilter,
    answered: Int,
    correct: Int,
    onToggleUnit: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onWordFilterChange: (WordFilter) -> Unit,
    onStart: () -> Unit,
    onStartMatching: () -> Unit,
) {
    val translatedTotal = vocabulary.words.count { it.translation.isNotBlank() }
    val available = vocabulary.words.count { it.unit in selectedUnits && it.translation.isNotBlank() }
    val available = vocabulary.words.count { it.unit in selectedUnits && it.translation.isNotBlank() && wordFilter.includes(it) }

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

        Text("ТИПЫ КАРТОЧЕК", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WordFilter.values().forEach { filter ->
                FilterChip(
                    selected = wordFilter == filter,
                    onClick = { onWordFilterChange(filter) },
                    label = { Text(filter.label, fontSize = 12.sp) },
                )
            }
        }
        Text(
            "«Фразы» — термины из двух и более слов; они также остаются в фильтре своей части речи.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

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
                val translated = vocabulary.words.count {
                    it.unit == unit.id && it.translation.isNotBlank() && wordFilter.includes(it)
                }
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
                        Text(
                            if (wordFilter == WordFilter.ALL) "$translated из ${unit.count} с переводом" else "$translated карточек этого типа",
                            color = Muted,
                            fontSize = 12.sp,
                        )
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
                text = when {
                    available > 0 -> "В тренировке: $available карточек из выбранных юнитов."
                    wordFilter == WordFilter.ALL -> "В выбранных юнитах пока нет карточек с переводом."
                    else -> "В выбранных юнитах нет карточек типа «${wordFilter.label.lowercase()}»."
                },
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
            Text(if (available > 0) "Тест: выбери перевод" else "Жду переводы", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onStartMatching,
            enabled = available >= 5,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Coral, disabledContainerColor = Color(0xFFB8C3BC)),
        ) {
            Text("Сопоставить 5 пар", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text("В режиме пар найди пять совпадений, затем нажми «Дальше» для следующей пятёрки.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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

@Composable
private fun MatchPairsScreen(words: List<VocabWord>, onBack: () -> Unit, onAnswer: (Boolean) -> Unit) {
    var roundIndex by remember(words) { mutableIntStateOf(0) }
    var previousBatchIds by remember(words) { mutableStateOf(emptySet<String>()) }
    val batch = remember(words, roundIndex, previousBatchIds) {
        val unseen = words.filterNot { it.id in previousBatchIds }
        val pool = if (unseen.size >= 5) unseen else words
        val uniqueTranslations = pool.shuffled().distinctBy { it.translation.trim().lowercase() }
        (if (uniqueTranslations.size >= 5) uniqueTranslations else pool.shuffled()).take(5)
    }
    val translations = remember(batch) { batch.shuffled() }
    val matchedIds = remember(batch) { mutableStateListOf<String>() }
    var selectedLeft by remember(batch) { mutableStateOf<String?>(null) }
    var selectedRight by remember(batch) { mutableStateOf<String?>(null) }
    var wrongPair by remember(batch) { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(wrongPair) {
        if (wrongPair != null) {
            delay(650)
            selectedLeft = null
            selectedRight = null
            wrongPair = null
        }
    }

    fun resolveSelection(leftId: String?, rightId: String?) {
        if (leftId == null || rightId == null) return
        val isCorrect = leftId == rightId
        if (isCorrect) {
            matchedIds.add(leftId)
            selectedLeft = null
            selectedRight = null
        } else {
            wrongPair = leftId to rightId
        }
        onAnswer(isCorrect)
    }

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.size(44.dp).clickable(onClick = onBack),
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Pine)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("СОПОСТАВЛЕНИЕ", color = Pine, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Раунд ${roundIndex + 1} · ${matchedIds.size} из ${batch.size} пар", color = Muted, fontSize = 13.sp)
            }
        }

        LinearProgressIndicator(
            progress = { matchedIds.size.toFloat() / batch.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Coral,
            trackColor = Color(0xFFE6E2D9),
        )

        Text("Нажми на английское слово, затем на его перевод.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ENGLISH", modifier = Modifier.weight(1f), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("РУССКИЙ", modifier = Modifier.weight(1f), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }

        batch.forEachIndexed { rowIndex, word ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MatchOption(
                    text = word.term,
                    selected = selectedLeft == word.id,
                    matched = word.id in matchedIds,
                    incorrect = wrongPair?.first == word.id,
                    enabled = wrongPair == null && word.id !in matchedIds,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val nextSelection = if (selectedLeft == word.id) null else word.id
                        selectedLeft = nextSelection
                        resolveSelection(nextSelection, selectedRight)
                    },
                )
                val translationWord = translations[rowIndex]
                MatchOption(
                    text = translationWord.translation,
                    selected = selectedRight == translationWord.id,
                    matched = translationWord.id in matchedIds,
                    incorrect = wrongPair?.second == translationWord.id,
                    enabled = wrongPair == null && translationWord.id !in matchedIds,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val nextSelection = if (selectedRight == translationWord.id) null else translationWord.id
                        selectedRight = nextSelection
                        resolveSelection(selectedLeft, nextSelection)
                    },
                )
            }
        }

        if (matchedIds.size == batch.size) {
            Text("Все пары найдены!", color = Correct, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    previousBatchIds = batch.map { it.id }.toSet()
                    roundIndex += 1
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pine),
            ) {
                Text("Дальше", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MatchOption(
    text: String,
    selected: Boolean,
    matched: Boolean,
    incorrect: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        matched -> PineSoft
        incorrect -> Color(0xFFF8E6E3)
        selected -> Color(0xFFEAF0FB)
        else -> Color.White
    }
    val borderColor = when {
        matched -> Correct
        incorrect -> Incorrect
        selected -> Pine
        else -> Color(0xFFE1E3E5)
    }
    Surface(
        modifier = modifier.heightIn(min = 70.dp).clickable(enabled = enabled, onClick = onClick),
        color = background,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = Ink, fontSize = 14.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
        }
    }
}
