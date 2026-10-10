package com.yuyan.inputmethod.util

object T9DoublePinYinUtils {

    private val VALID_PINYINS = setOf(
        "a", "ai", "an", "ang", "ao",
        "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
        "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
        "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dia", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
        "e", "ei", "en", "eng", "er",
        "fa", "fan", "fang", "fei", "fen", "feng", "fiao", "fo", "fou", "fu",
        "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
        "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
        "ji", "jia", "jiang", "jian", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun",
        "ka", "kai", "kan", "kang", "kao", "ke", "kei", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
        "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "lo", "long", "lou", "lu", "luan", "lue", "lve", "lun", "luo", "lv",
        "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
        "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nue", "nve", "nuo", "nv",
        "o", "ou",
        "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
        "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun",
        "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "rua", "ruan", "rui", "run", "ruo",
        "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
        "ta", "tai", "tan", "tang", "tao", "te", "tei", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
        "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
        "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun",
        "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun",
        "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo"
    )

    interface T9DoublePinYinScheme {
        fun keyToPinyin(sequence: String?): Array<String>
        fun pinyin2Key(sequence: String?): String
    }

    /**
     * 砖头鹏九宫格双拼方案 (sp10.ini)
     */
    class ZhuantoupengScheme : T9DoublePinYinScheme {
        // 声母数字映射
        private val initialMap = mapOf(
            "b" to "2", "c" to "2", "sh" to "2",
            "ch" to "3", "d" to "3", "f" to "3",
            "g" to "4", "h" to "4",
            "j" to "5", "k" to "5", "l" to "5",
            "m" to "6", "n" to "6",
            "p" to "7", "q" to "7", "r" to "7", "s" to "7",
            "t" to "8", "zh" to "8",
            "w" to "9", "x" to "9", "y" to "9", "z" to "9",
            "" to "4" // 零声母映射为 4
        )

        // 韵母数字映射
        private val finalMap = mapOf(
            "uang" to "1", "ian" to "1", "ing" to "1", "en" to "1",
            "a" to "2", "ao" to "2", "ia" to "2",
            "eng" to "3", "iong" to "3", "ue" to "3", "e" to "3",
            "ong" to "4", "uai" to "4", "er" to "4", "i" to "4", "iu" to "4",
            "iang" to "5", "uan" to "5", "an" to "5",
            "ei" to "6", "ie" to "6", "ui" to "6", "o" to "6",
            "iao" to "7", "ai" to "7", "un" to "7",
            "ua" to "8", "ou" to "8", "u" to "8", "v" to "8",
            "ang" to "9", "in" to "9", "uo" to "9"
        )

        private val pinyinMap = HashMap<String, String>(100)

        init {
            val initials = listOf("b", "c", "ch", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "q", "r", "s", "sh", "t", "w", "x", "y", "z", "zh", "")
            val finals = listOf("a", "ai", "an", "ang", "ao", "e", "ei", "en", "eng", "er", "i", "ia", "iang", "ian", "iao", "ie", "in", "ing", "iong", "iu", "o", "ong", "ou", "u", "ua", "uai", "uan", "uang", "ue", "ui", "un", "uo", "v")

            val tempMap = HashMap<String, MutableList<String>>()
            for (init in initials) {
                val initDigit = initialMap[init] ?: continue
                for (fin in finals) {
                    val finDigit = finalMap[fin] ?: continue
                    val code = initDigit + finDigit

                    val syllable = when {
                        init in listOf("j", "q", "x", "y") && fin == "v" -> init + "u"
                        init in listOf("j", "q", "x", "y") && fin == "ue" -> init + "ue"
                        init in listOf("n", "l") && fin == "v" -> init + "v"
                        init in listOf("n", "l") && fin == "ue" -> init + "ve"
                        else -> init + fin
                    }

                    if (syllable in VALID_PINYINS && tempMap[code]?.contains(syllable) != true) {
                        tempMap.getOrPut(code) { mutableListOf() }.add(syllable)
                    }
                }
            }
            for ((code, list) in tempMap) {
                pinyinMap[code] = list.joinToString(",")
            }
        }

        override fun keyToPinyin(sequence: String?): Array<String> {
            if (sequence.isNullOrEmpty()) {
                return emptyArray()
            }
            if (sequence.length == 1) {
                val digit = sequence
                val matchingInitials = initialMap.filter { it.value == digit }.keys.filter { it.isNotEmpty() }
                return matchingInitials.toTypedArray()
            }
            val sequenceString = if (sequence.length >= 2) sequence.substring(0, 2) else sequence
            return pinyinMap[sequenceString]?.split(",")?.toTypedArray() ?: emptyArray()
        }

        override fun pinyin2Key(sequence: String?): String {
            if (sequence.isNullOrEmpty()) {
                return ""
            }
            for ((key, value) in pinyinMap) {
                val pinyinList = value.split(",")
                if (pinyinList.any { it == sequence }) {
                    return key
                }
            }
            return initialMap[sequence] ?: ""
        }
    }

    // 方案注册表：后续如需加入其他九宫格双拼方案，只需在此处注册即可，无需修改核心逻辑
    private val schemes = mapOf(
        "double_pinyin_zhuantoupeng" to ZhuantoupengScheme()
    )

    private fun getScheme(schemaId: String): T9DoublePinYinScheme {
        return schemes[schemaId] ?: schemes["double_pinyin_zhuantoupeng"]!!
    }

    fun keyToPinyin(schemaId: String, sequence: String?): Array<String> {
        return getScheme(schemaId).keyToPinyin(sequence)
    }

    fun pinyin2Key(schemaId: String, sequence: String?): String {
        return getScheme(schemaId).pinyin2Key(sequence)
    }
}
