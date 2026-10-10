package com.yuyan.inputmethod

import android.view.KeyEvent
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.manager.InputModeSwitcher
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.utils.StringUtils
import com.yuyan.inputmethod.core.CandidateListItem
import com.yuyan.inputmethod.core.Rime
import com.yuyan.inputmethod.data.InputKey
import com.yuyan.inputmethod.data.KeyRecordStack
import com.yuyan.inputmethod.util.DoublePinYinUtils
import com.yuyan.inputmethod.util.LX17PinYinUtils
import com.yuyan.inputmethod.util.QwertyPinYinUtils
import com.yuyan.inputmethod.util.T9DoublePinYinUtils
import com.yuyan.inputmethod.util.T9PinYinUtils
import java.util.Locale

object RimeEngine {
    private val keyRecordStack = KeyRecordStack()
    private val sp10Buffer = StringBuilder()
    private val sp10SelectedPinyins = HashMap<Int, String>()
    private var pinyins: Array<String> = emptyArray() // 候选词界面的候选拼音列表
    var showCandidates: List<CandidateListItem> = emptyList() // 所有待展示的候选词
    var showComposition: String = "" // 候选词上方展示的拼音
    var preCommitText: String = "" // 待提交的文字
    private var customPhraseSize: Int = 0 // 自定义引擎候选词长度
    const val MASK_CASE_LOWER = 0
    private var charCase = 0x0000
    fun init() {
        Rime.getInstance(false)
    }

    private var currentSchema = CustomConstant.SCHEMA_ZH_T9

    fun selectSchema(mod: String): Boolean {
        currentSchema = mod
        sp10Buffer.clear()
        sp10SelectedPinyins.clear()
        keyRecordStack.clear()
        charCase = MASK_CASE_LOWER
        Rime.startup(Launcher.instance.context, false)
        val rimeMod = if (mod == CustomConstant.SCHEMA_ZH_DOUBLE_ZHUANTOUPENG) CustomConstant.SCHEMA_ZH_QWERTY else mod
        return Rime.selectSchema(rimeMod)
    }

    fun getCurrentRimeSchema(): String {
        return currentSchema
    }

    /**
     * 是否输入完毕
     */
    fun isFinish(): Boolean {
        return keyRecordStack.isEmpty()
    }

    private fun getT9Digit(keyCode: Int): Char? {
        return when (keyCode) {
            KeyEvent.KEYCODE_APOSTROPHE -> '1'
            KeyEvent.KEYCODE_A -> '2'
            KeyEvent.KEYCODE_D -> '3'
            KeyEvent.KEYCODE_G -> '4'
            KeyEvent.KEYCODE_J -> '5'
            KeyEvent.KEYCODE_M -> '6'
            KeyEvent.KEYCODE_P -> '7'
            KeyEvent.KEYCODE_T -> '8'
            KeyEvent.KEYCODE_W -> '9'
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> ('1'.code + keyCode - KeyEvent.KEYCODE_1).toChar()
            else -> null
        }
    }

    fun onNormalKey(event: KeyEvent) {
        val keyCode = event.keyCode
        val rimeSchema = getCurrentRimeSchema()
        if (rimeSchema == CustomConstant.SCHEMA_ZH_DOUBLE_ZHUANTOUPENG) {
            val digit = getT9Digit(keyCode)
            if (digit != null) {
                keyRecordStack.pushKey(event)
                sp10Buffer.append(digit)
                updateSp10RimeAndCandidates()
                return
            }
        }
        val keyChar = if(keyCode == KeyEvent.KEYCODE_APOSTROPHE) if(isFinish()) '/'.code else '\''.code
            else event.unicodeChar
        if (keyRecordStack.pushKey(event)) Rime.processKey(keyChar, event.action)
        updateCandidatesOrCommitText()
    }

    fun onDeleteKey() {
        if (getCurrentRimeSchema() == CustomConstant.SCHEMA_ZH_DOUBLE_ZHUANTOUPENG && sp10Buffer.isNotEmpty()) {
            val lastChunkIndex = (sp10Buffer.length - 1) / 2
            if (sp10Buffer.length % 2 == 0 && sp10SelectedPinyins.containsKey(lastChunkIndex)) {
                sp10SelectedPinyins.remove(lastChunkIndex)
            } else {
                sp10Buffer.deleteCharAt(sp10Buffer.length - 1)
                val newChunkCount = (sp10Buffer.length + 1) / 2
                sp10SelectedPinyins.keys.filter { it >= newChunkCount }.forEach { sp10SelectedPinyins.remove(it) }
            }
            keyRecordStack.processDelAction()
            if (sp10Buffer.isEmpty()) {
                Rime.clearComposition()
                updateCandidatesOrCommitText()
            } else {
                updateSp10RimeAndCandidates()
            }
            return
        }
        sp10Buffer.clear()
        sp10SelectedPinyins.clear()
        keyRecordStack.processDelAction()
        updateCandidatesOrCommitText()
    }

    private fun getFirstUnconfirmedChunkIndex(): Int {
        val buf = sp10Buffer.toString()
        val fullChunkCount = buf.length / 2
        for (i in 0 until fullChunkCount) {
            if (!sp10SelectedPinyins.containsKey(i)) {
                return i
            }
        }
        return fullChunkCount
    }

    private fun generateAllCombinations(chunkLists: List<List<String>>): List<String> {
        if (chunkLists.isEmpty()) return emptyList()
        var result = listOf("")
        for (list in chunkLists) {
            val next = ArrayList<String>(result.size * list.size)
            for (prefix in result) {
                for (item in list) {
                    next.add(prefix + item)
                }
            }
            result = next
        }
        return result
    }

    private fun updateSp10RimeAndCandidates() {
        val rimeSchema = getCurrentRimeSchema()
        val buf = sp10Buffer.toString()
        if (buf.isEmpty()) {
            Rime.clearComposition()
            updateCandidatesOrCommitText()
            return
        }

        val totalChunks = (buf.length + 1) / 2
        val chunkPinyinLists = ArrayList<List<String>>(totalChunks)
        for (i in 0 until totalChunks) {
            val start = i * 2
            val end = minOf(start + 2, buf.length)
            val chunk = buf.substring(start, end)
            if (chunk.length == 2) {
                val validPinyins = T9DoublePinYinUtils.keyToPinyin(rimeSchema, chunk)
                val selected = sp10SelectedPinyins[i]
                if (selected != null && validPinyins.contains(selected)) {
                    chunkPinyinLists.add(listOf(selected))
                } else {
                    chunkPinyinLists.add(validPinyins.take(2).toList().ifEmpty { listOf(chunk) })
                }
            } else if (chunk.length == 1) {
                val initials = T9DoublePinYinUtils.keyToPinyin(rimeSchema, chunk)
                chunkPinyinLists.add(initials.take(2).toList().ifEmpty { listOf(chunk) })
            }
        }

        val combinations = generateAllCombinations(chunkPinyinLists).take(6)

        // Cache JNI queries during this single frame pass to eliminate redundant C++ calls
        val pinyinCache = HashMap<String, List<CandidateListItem>>()
        fun getCachedCandidates(pinyin: String): List<CandidateListItem> {
            return pinyinCache.getOrPut(pinyin) {
                Rime.clearComposition()
                pinyin.forEach { ch -> Rime.processKey(ch.code, 0) }
                Rime.getRimeContext()?.candidates?.asList() ?: emptyList()
            }
        }

        val scoredCombinations = combinations.sortedByDescending { combo ->
            val cands = getCachedCandidates(combo)
            if (cands.isEmpty()) 0
            else cands.first().text.length * 1000 + minOf(cands.size, 10) * 10
        }

        val primaryPinyin = scoredCombinations.firstOrNull() ?: buf

        // 1. Collect candidates for top combinations using cached queries
        val comboCandidatesList = ArrayList<List<CandidateListItem>>()
        val maxCandidatesPerCombo = if (totalChunks == 1) 5 else 10

        for (comboPinyin in scoredCombinations) {
            val cands = getCachedCandidates(comboPinyin)
            if (cands.isNotEmpty()) {
                comboCandidatesList.add(cands.take(maxCandidatesPerCombo))
            }
        }

        // 2. Multi-pronunciation Round-Robin Candidate Interleaving
        val roundRobinCandidates = ArrayList<Pair<CandidateListItem, Int>>()
        val seenTexts = HashSet<String>()

        var maxDepth = 0
        for (cands in comboCandidatesList) {
            if (cands.size > maxDepth) maxDepth = cands.size
        }

        for (depth in 0 until maxDepth) {
            for ((comboIndex, cands) in comboCandidatesList.withIndex()) {
                if (depth < cands.size) {
                    val cand = cands[depth]
                    if (seenTexts.add(cand.text)) {
                        val score = cand.text.length * 10000 - depth * 200 - comboIndex * 10
                        roundRobinCandidates.add(Pair(cand, score))
                    }
                }
            }
        }

        roundRobinCandidates.sortByDescending { it.second }
        val mergedCandidates = roundRobinCandidates.map { it.first }

        // 3. Restore primaryPinyin in Rime so preedit composition is active
        Rime.clearComposition()
        primaryPinyin.forEach { ch ->
            Rime.processKey(ch.code, 0)
        }

        updateCandidatesOrCommitText()

        if (mergedCandidates.isNotEmpty()) {
            showCandidates = mergedCandidates
        }
    }

    fun selectCandidate(index: Int): String? {
        val rimeSchema = getCurrentRimeSchema()
        if (rimeSchema == CustomConstant.SCHEMA_ZH_DOUBLE_ZHUANTOUPENG) {
            val candidate = showCandidates.getOrNull(index)
            if (candidate != null) {
                val commitText = candidate.text
                preCommitText = commitText
                keyRecordStack.pushCandidateSelectAction()

                val committedChars = commitText.length
                val committedDigits = committedChars * 2

                if (committedDigits > 0 && committedDigits <= sp10Buffer.length) {
                    sp10Buffer.delete(0, committedDigits)
                    val newMap = HashMap<Int, String>()
                    for ((k, v) in sp10SelectedPinyins) {
                        if (k >= committedChars) {
                            newMap[k - committedChars] = v
                        }
                    }
                    sp10SelectedPinyins.clear()
                    sp10SelectedPinyins.putAll(newMap)
                } else {
                    sp10Buffer.clear()
                    sp10SelectedPinyins.clear()
                }

                if (sp10Buffer.isNotEmpty()) {
                    updateSp10RimeAndCandidates()
                } else {
                    sp10Buffer.clear()
                    sp10SelectedPinyins.clear()
                    keyRecordStack.clear()
                    Rime.clearComposition()
                    showComposition = ""
                    showCandidates = emptyList()
                    pinyins = emptyArray()
                }
                return commitText
            }
        }

        val indexReal = index - customPhraseSize
        Rime.selectCandidate(indexReal)
        keyRecordStack.pushCandidateSelectAction()
        return updateCandidatesOrCommitText()
    }

    fun getNextPageCandidates(): Array<CandidateListItem> {
        return if (Rime.hasRight()) {
            Rime.processKey(keyRecordStack.getRimeKeycodeByName("Page_Down"), 0)
           val candidates = Rime.getRimeContext()!!.candidates
            when (charCase) {
                KeyEvent.META_SHIFT_ON -> {
                    for (item in candidates) {
                        item.text = item.text.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                }
                KeyEvent.META_CAPS_LOCK_ON -> {
                    for (item in candidates) {
                        item.text = item.text.uppercase()
                    }
                }
                else -> {
                    for (item in candidates) {
                        item.text = item.text.lowercase()
                    }
                }
            }
            candidates
        } else emptyArray()
    }

    fun selectPinyin(index: Int) {
        val rimeSchema = getCurrentRimeSchema()
        if (rimeSchema == CustomConstant.SCHEMA_ZH_DOUBLE_ZHUANTOUPENG) {
            if (index in pinyins.indices && sp10Buffer.isNotEmpty()) {
                val chosenPinyin = pinyins[index]
                val unconfirmedIndex = getFirstUnconfirmedChunkIndex()
                sp10SelectedPinyins[unconfirmedIndex] = chosenPinyin
                updateSp10RimeAndCandidates()
            }
            return
        }
        val pinyinKey = keyRecordStack.pushPinyinSelectAction(pinyins[index]) ?: return
        Rime.replaceKey(pinyinKey.posInInput, pinyinKey.t9Keys().length, pinyinKey.pinyin())
        updateCandidatesOrCommitText()
    }

    fun predictAssociationWords(text: String) {
        sp10Buffer.clear()
        sp10SelectedPinyins.clear()
        pinyins = emptyArray()
        if (text.isNotEmpty()) {
            showCandidates = buildList {
                val words = Rime.getAssociateList(text)
                val firstFive = words.take(5)
                addAll(firstFive.filterNotNull().map { CandidateListItem("", it) })
                addAll(CustomEngine.predictAssociationWordsChinese(text).map { CandidateListItem("", it) })
                val remaining = words.drop(5)
                addAll(remaining.filterNotNull().map { CandidateListItem("", it) })
            }
            showComposition = ""
        }
    }

    fun selectAssociation(index: Int): String? {
        val indexReal = index - customPhraseSize
        val chosenText = showCandidates.getOrNull(indexReal)?.text ?: ""
        sp10Buffer.clear()
        sp10SelectedPinyins.clear()
        Rime.chooseAssociate(indexReal)
        updateCandidatesOrCommitText()
        preCommitText = chosenText
        return chosenText
    }

    fun reset() {
        sp10Buffer.clear()
        sp10SelectedPinyins.clear()
        showCandidates = emptyList()
        pinyins = emptyArray()
        showComposition = ""
        preCommitText = ""
        keyRecordStack.clear()
        Rime.clearComposition()
        if(charCase == KeyEvent.META_SHIFT_ON) charCase = MASK_CASE_LOWER
    }

    fun destroy() = Rime.destroy()

    private fun updateCandidatesOrCommitText(): String? {
        val rimeCommit = Rime.getRimeCommit()
        val rimeSchema = getCurrentRimeSchema()

        if (rimeCommit != null) {
            val commitText = rimeCommit.commitText
            preCommitText = if (charCase == KeyEvent.META_SHIFT_ON) {
                commitText.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            } else if (charCase == KeyEvent.META_CAPS_LOCK_ON) {
                commitText.uppercase()
            } else {
                commitText.lowercase()
            }

            if (rimeSchema == CustomConstant.SCHEMA_ZH_DOUBLE_ZHUANTOUPENG) {
                val committedChars = commitText.length
                val committedDigits = committedChars * 2
                if (committedDigits > 0 && committedDigits <= sp10Buffer.length) {
                    sp10Buffer.delete(0, committedDigits)
                    val newMap = HashMap<Int, String>()
                    for ((k, v) in sp10SelectedPinyins) {
                        if (k >= committedChars) {
                            newMap[k - committedChars] = v
                        }
                    }
                    sp10SelectedPinyins.clear()
                    sp10SelectedPinyins.putAll(newMap)
                } else {
                    sp10Buffer.clear()
                    sp10SelectedPinyins.clear()
                }

                if (sp10Buffer.isNotEmpty()) {
                    updateSp10RimeAndCandidates()
                } else {
                    sp10Buffer.clear()
                    sp10SelectedPinyins.clear()
                    keyRecordStack.clear()
                    Rime.clearComposition()
                    showComposition = ""
                    showCandidates = emptyList()
                    pinyins = emptyArray()
                }
            } else {
                keyRecordStack.clear()
                showComposition = ""
                showCandidates = emptyList()
                pinyins = emptyArray()
            }
            return preCommitText
        }
        val candidates = Rime.getRimeContext()?.candidates?.asList() ?: emptyList()
        customPhraseSize = 0
        val compositionText = Rime.compositionText
        showCandidates = when {
            compositionText.isNotBlank() -> {
                val phrase = CustomEngine.processPhrase(compositionText.replace("\'", ""))
                if(InputModeSwitcher.isEnglish && StringUtils.isLetter(compositionText) &&
                    !compositionText.equals(candidates.first().text, ignoreCase = true) ){
                    phrase.add(0, compositionText)
                }
                customPhraseSize = phrase.size
                phrase.map { content -> CandidateListItem("📋", content) }.toMutableList().plus(candidates)
            }
            else -> candidates
        }
        var count = Rime.compositionText.count { it in 'A'..'Z' }
        if (count > 0) {
            keyRecordStack.forEachReversed { inputKey ->
                if (inputKey is InputKey.T9Key) inputKey.consumed = count-- <= 0
            }
        }
        var composition = getCurrentComposition(candidates)
        when (charCase) {
            KeyEvent.META_SHIFT_ON -> {
                for (item in showCandidates) item.text = item.text.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                composition = composition.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            KeyEvent.META_CAPS_LOCK_ON -> {
                for (item in showCandidates) item.text = item.text.uppercase()
                composition = composition.uppercase()
            }
            else -> {
                for (item in showCandidates) item.text = item.text.lowercase()
                composition = composition.lowercase()
            }
        }
        pinyins = when (rimeSchema) {
            CustomConstant.SCHEMA_ZH_T9 -> {
                T9PinYinUtils.t9KeyToPinyin(Regex("[A-Z]+").find(compositionText.replace("'", ""))?.value ?: "")
            }
            CustomConstant.SCHEMA_ZH_DOUBLE_LX17 -> {
                LX17PinYinUtils.lx17KeyToPinyin(Regex("[A-Z]+").find(compositionText.replace("'", ""))?.value ?: "")
            }
            CustomConstant.SCHEMA_ZH_DOUBLE_ZHUANTOUPENG -> {
                if (sp10Buffer.isNotEmpty()) {
                    val buf = sp10Buffer.toString()
                    val unconfirmedIndex = getFirstUnconfirmedChunkIndex()
                    val start = unconfirmedIndex * 2
                    if (start < buf.length) {
                        val end = minOf(start + 2, buf.length)
                        val chunk = buf.substring(start, end)
                        T9DoublePinYinUtils.keyToPinyin(rimeSchema, chunk)
                    } else {
                        emptyArray()
                    }
                } else {
                    emptyArray()
                }
            }
            else -> {
                emptyArray()
            }
        }
        showComposition = if (rimeSchema == CustomConstant.SCHEMA_ZH_DOUBLE_ZHUANTOUPENG && sp10Buffer.isNotEmpty()) {
            composition.ifEmpty { sp10Buffer.toString() }
        } else {
            composition
        }
        preCommitText = ""
        return null
    }

    /**
     * 拿到候选词拼音组合
     */
    fun getPrefixs(): Array<String> {
        return pinyins
    }

    private fun getCurrentComposition(candidates: List<CandidateListItem>): String {
        val composition = Rime.compositionText
        val rimeSchema = getCurrentRimeSchema()
        if(rimeSchema == CustomConstant.SCHEMA_EN) return ""
        if(composition.isEmpty()) return ""
        if(candidates.isEmpty()) return composition
        val comment = candidates.first().comment
        val result =  when {
            comment.isNotBlank() && comment.startsWith("~") -> composition
            rimeSchema == CustomConstant.SCHEMA_ZH_T9 -> {
                T9PinYinUtils.getT9Composition(composition, comment)
            }
            rimeSchema == CustomConstant.SCHEMA_ZH_DOUBLE_LX17 -> {
                T9PinYinUtils.getT9Composition(composition, comment)
            }
            rimeSchema == CustomConstant.SCHEMA_ZH_DOUBLE_ZHUANTOUPENG -> {
                composition.ifEmpty { sp10Buffer.toString() }
            }
            rimeSchema.startsWith(CustomConstant.SCHEMA_ZH_DOUBLE_FLYPY) -> {
                if(!AppPrefs.getInstance().keyboardSetting.keyboardDoubleInputKey.getValue()) composition
                else DoublePinYinUtils.getDoublePinYinComposition(rimeSchema, composition, comment)
            }
            else -> {
                QwertyPinYinUtils.getQwertyComposition(composition, comment)
            }
        }
        return if (!composition.endsWith("'") && result.endsWith("'")) result.dropLast(1) else result
    }

    /**
     * 设置输入法搜索参数
     */
    fun setImeOption(option: String, value: Boolean) {
        Rime.setOption(option, value)
    }

    fun setCharCase(charCase: Int) {
        this.charCase = charCase
    }

}