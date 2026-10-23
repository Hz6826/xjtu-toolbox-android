package com.xjtu.toolbox.platform.qr

/**
 * zxing 3.5.4 `CharacterSetECI` 与 `StringUtils.guessCharset` 的移植；码表在 [QrCjkTables]
 * （由 `tools/gen-qr-charset-tables.java` 从 JDK 的 `Charset` 生成）。
 * 不支持 ECI 12（ISO-8859-10）和 16（ISO-8859-14）：zxing 也不认，JDK 也没有这两个 `Charset`。
 */
internal object QrCharset {

    /** ECI 值 → 编码名（zxing 的 `VALUE_TO_ECI` 那张表，逐个值抄过来）。 */
    private val ECI_NAMES: Map<Int, String> = buildMap {
        put(0, "Cp437")
        put(2, "Cp437")
        put(1, "ISO-8859-1")
        put(3, "ISO-8859-1")
        put(4, "ISO-8859-2")
        put(5, "ISO-8859-3")
        put(6, "ISO-8859-4")
        put(7, "ISO-8859-5")
        put(8, "ISO-8859-6")
        put(9, "ISO-8859-7")
        put(10, "ISO-8859-8")
        put(11, "ISO-8859-9")
        // 12 / 16 留名只为认出是哪个编码，解码照样失败（与 zxing 一致）
        put(12, "ISO-8859-10")
        put(13, "ISO-8859-11")
        put(15, "ISO-8859-13")
        put(16, "ISO-8859-14")
        put(17, "ISO-8859-15")
        put(18, "ISO-8859-16")
        put(20, "Shift_JIS")
        put(21, "windows-1250")
        put(22, "windows-1251")
        put(23, "windows-1252")
        put(24, "windows-1256")
        put(25, "UTF-16BE")
        put(26, "UTF-8")
        put(27, "US-ASCII")
        put(170, "US-ASCII")
        put(28, "Big5")
        // zxing 对 ECI 29 实际用 GB18030 解；HANZI 段才用 GB2312
        put(29, "GB18030")
        put(30, "EUC-KR")
    }

    /** 本实现自己解、不走 [QrCjkTables] 码表的编码名。 */
    private val BUILT_IN = setOf(
        "ISO-8859-1",
        "UTF-8", "UTF8",
        "US-ASCII", "ASCII",
        "UTF-16BE", "UnicodeBig",
        "UTF-16",
        // GB18030 有「四字节形态」，走专门的 [decodeGb18030]
        "GB18030",
    )

    /** ECI 值 → 编码名（`CharacterSetECI.getCharacterSetECIByValue`）；查不到返回 null（zxing 也返回 null），由调用方抛。 */
    fun eciNameFor(value: Int): String? {
        if (value < 0 || value >= 900) return null
        return ECI_NAMES[value]
    }

    /** 这个编码名能不能解。 */
    fun canDecode(name: String): Boolean = name in BUILT_IN || QrCjkTables.TABLES.containsKey(name)

    /** 按编码名解一段字节；解不出来返回 null，由调用方抛 `FormatException`。 */
    fun decode(name: String, bytes: ByteArray): String? = when (name) {
        "ISO-8859-1" -> bytes.map { (it.toInt() and 0xFF).toChar() }.joinToString("")
        "UTF-8", "UTF8" -> bytes.decodeToString()
        "US-ASCII", "ASCII" ->
            bytes.map { if ((it.toInt() and 0xFF) < 0x80) (it.toInt() and 0x7F).toChar() else REPLACEMENT }.joinToString("")
        "UTF-16BE", "UnicodeBig" -> decodeUtf16(bytes, bigEndian = true)
        "UTF-16" -> {
            // 有 BOM 时按 BOM 走；没有就按大端（Java 的 UTF-16 解码器也是这个规矩）
            when {
                bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                    decodeUtf16(bytes.copyOfRange(2, bytes.size), bigEndian = true)
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                    decodeUtf16(bytes.copyOfRange(2, bytes.size), bigEndian = false)
                else -> decodeUtf16(bytes, bigEndian = true)
            }
        }
        "GB18030" -> decodeGb18030(bytes)
        else -> QrCjkTables.TABLES[name]?.let { decodeWithTable(it, bytes) }
    }

    private fun decodeUtf16(bytes: ByteArray, bigEndian: Boolean): String {
        val sb = StringBuilder(bytes.size / 2)
        var i = 0
        while (i + 1 < bytes.size) {
            val hi = bytes[i].toInt() and 0xFF
            val lo = bytes[i + 1].toInt() and 0xFF
            val unit = if (bigEndian) (hi shl 8) or lo else (lo shl 8) or hi
            sb.append(unit.toChar())
            i += 2
        }
        if (i < bytes.size) sb.append('\uFFFD') // 落单的那个字节（Java 也是替换字符）
        return sb.toString()
    }

    /**
     * 查表解码，与生成器里的 `walk` 同一套规则：单字节字符吞 1 字节；双字节格子里正常字符和 [UNMAPPED_CELL]
     * 吞 2 字节，[INVALID_CELL] 只吞首字节。
     */
    private fun decodeWithTable(table: QrCharsetTable, bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val single = table.singleCps[b]
            if (single >= 0) {
                sb.append(single.toChar())
                i++
                continue
            }
            if (table.rows[b] >= 0 && i + 1 < bytes.size) {
                when (val cell = table.cell(b, bytes[i + 1].toInt() and 0xFF)) {
                    INVALID_CELL -> {
                        sb.append(REPLACEMENT)
                        i++
                    }
                    UNMAPPED_CELL -> {
                        sb.append(REPLACEMENT)
                        i += 2
                    }
                    else -> {
                        sb.append(cell)
                        i += 2
                    }
                }
                continue
            }
            sb.append(REPLACEMENT)
            i++
        }
        return sb.toString()
    }

    /**
     * GB18030：在 [decodeWithTable] 之上加四字节形态（第二字节为 `0x30-0x39`）。坏序列吞掉的字节数
     * 按前缀校验到哪一步算，与 JDK 一致（对应生成器里的 `walkGb18030`）。
     */
    private fun decodeGb18030(bytes: ByteArray): String {
        val table = QrCjkTables.GB18030
        val four = QrCjkTables.GB18030_FOUR
        val n = bytes.size
        val sb = StringBuilder(n)
        var i = 0
        while (i < n) {
            val b1 = bytes[i].toInt() and 0xFF
            if (b1 < 0x80) {
                sb.append(b1.toChar())
                i++
                continue
            }
            if (b1 > 0xFE || table.rows[b1] < 0 || i + 1 >= n) {
                sb.append(REPLACEMENT)
                i++
                continue
            }
            val b2 = bytes[i + 1].toInt() and 0xFF
            if (b2 >= 0x30 && b2 <= 0x39) {
                if (i + 2 >= n) {
                    sb.append(REPLACEMENT)
                    i += 2
                    continue
                }
                val b3 = bytes[i + 2].toInt() and 0xFF
                if (b3 < 0x81 || b3 > 0xFE) {
                    sb.append(REPLACEMENT)
                    i += 3
                    continue
                }
                if (i + 3 >= n) {
                    sb.append(REPLACEMENT)
                    i += 3
                    continue
                }
                val b4 = bytes[i + 3].toInt() and 0xFF
                if (b4 < 0x30 || b4 > 0x39) {
                    sb.append(REPLACEMENT)
                    i += 4
                    continue
                }
                val pointer = ((b1 - 0x81) * 10 + (b2 - 0x30)) * 1260 + (b3 - 0x81) * 10 + (b4 - 0x30)
                val cp = four.lookup(pointer)
                if (cp >= 0) sb.appendCodePoint(cp) else sb.append(REPLACEMENT)
                i += 4
                continue
            }
            when (val cell = table.cell(b1, b2)) {
                INVALID_CELL -> {
                    sb.append(REPLACEMENT)
                    i++
                }
                UNMAPPED_CELL -> {
                    sb.append(REPLACEMENT)
                    i += 2
                }
                else -> {
                    sb.append(cell)
                    i += 2
                }
            }
        }
        return sb.toString()
    }

    /** 猜一段字节是哪个编码 —— zxing 3.5.4 `StringUtils.guessCharset(bytes, null)` 的移植。 */
    fun guessCharset(bytes: ByteArray): String {
        // 先试 UTF-16：带它那个 BOM 的就算 UTF-16
        if (bytes.size > 2 &&
            ((bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) ||
                (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()))
        ) {
            return "UTF-16"
        }
        // 眼下只试着分开 ISO-8859-1 / UTF-8 / Shift_JIS 这三样 —— 它们是最常见的
        val length = bytes.size
        var canBeIso88591 = true
        var canBeShiftJis = true // zxing 那份是「SHIFT_JIS_CHARSET != null」；JVM 上都非 null
        var canBeUtf8 = true
        var utf8BytesLeft = 0
        var utf2BytesChars = 0
        var utf3BytesChars = 0
        var utf4BytesChars = 0
        var sjisBytesLeft = 0
        var sjisKatakanaChars = 0
        var sjisCurKatakanaWordLength = 0
        var sjisCurDoubleBytesWordLength = 0
        var sjisMaxKatakanaWordLength = 0
        var sjisMaxDoubleBytesWordLength = 0
        var isoHighOther = 0
        val utf8bom = bytes.size > 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        var i = 0
        while (i < length && (canBeIso88591 || canBeShiftJis || canBeUtf8)) {
            val value = bytes[i].toInt() and 0xFF
            // UTF-8
            if (canBeUtf8) {
                if (utf8BytesLeft > 0) {
                    if ((value and 0x80) == 0) {
                        canBeUtf8 = false
                    } else {
                        utf8BytesLeft--
                    }
                } else if ((value and 0x80) != 0) {
                    if ((value and 0x40) == 0) {
                        canBeUtf8 = false
                    } else {
                        utf8BytesLeft++
                        if ((value and 0x20) == 0) {
                            utf2BytesChars++
                        } else {
                            utf8BytesLeft++
                            if ((value and 0x10) == 0) {
                                utf3BytesChars++
                            } else {
                                utf8BytesLeft++
                                if ((value and 0x08) == 0) {
                                    utf4BytesChars++
                                } else {
                                    canBeUtf8 = false
                                }
                            }
                        }
                    }
                }
            }
            // ISO-8859-1
            if (canBeIso88591) {
                if (value > 0x7F && value < 0xA0) {
                    canBeIso88591 = false
                } else if (value > 0x9F && (value < 0xC0 || value == 0xD7 || value == 0xF7)) {
                    isoHighOther++
                }
            }
            // Shift_JIS
            if (canBeShiftJis) {
                if (sjisBytesLeft > 0) {
                    if (value < 0x40 || value == 0x7F || value > 0xFC) {
                        canBeShiftJis = false
                    } else {
                        sjisBytesLeft--
                    }
                } else if (value == 0x80 || value == 0xA0 || value > 0xEF) {
                    canBeShiftJis = false
                } else if (value > 0xA0 && value < 0xE0) {
                    sjisKatakanaChars++
                    sjisCurDoubleBytesWordLength = 0
                    sjisCurKatakanaWordLength++
                    if (sjisCurKatakanaWordLength > sjisMaxKatakanaWordLength) {
                        sjisMaxKatakanaWordLength = sjisCurKatakanaWordLength
                    }
                } else if (value > 0x7F) {
                    sjisBytesLeft++
                    sjisCurKatakanaWordLength = 0
                    sjisCurDoubleBytesWordLength++
                    if (sjisCurDoubleBytesWordLength > sjisMaxDoubleBytesWordLength) {
                        sjisMaxDoubleBytesWordLength = sjisCurDoubleBytesWordLength
                    }
                } else {
                    sjisCurKatakanaWordLength = 0
                    sjisCurDoubleBytesWordLength = 0
                }
            }
            i++
        }
        if (canBeUtf8 && utf8BytesLeft > 0) canBeUtf8 = false
        if (canBeShiftJis && sjisBytesLeft > 0) canBeShiftJis = false
        // 简单：有 BOM 或者至少有一个不是单字节的合法字符（且没有证据说它不是 UTF-8），就收工
        if (canBeUtf8 && (utf8bom || utf2BytesChars + utf3BytesChars + utf4BytesChars > 0)) return "UTF-8"
        // 简单：假定 SJIS，或者连着 >= 3 个非 ASCII 字符（且没有反证），就收工
        // （`ASSUME_SHIFT_JIS` 在这个仓里恒 false，见文件头）
        if (canBeShiftJis && (sjisMaxKatakanaWordLength >= 3 || sjisMaxDoubleBytesWordLength >= 3)) {
            return "Shift_JIS"
        }
        // SJIS / ISO-8859-1 启发式：只有两个连着的片假名，或 ≥10% 字节是「像大写」的非字母数字 Latin-1 ⇒ SJIS。
        if (canBeIso88591 && canBeShiftJis) {
            return if ((sjisMaxKatakanaWordLength == 2 && sjisKatakanaChars == 2) || isoHighOther * 10 >= length) {
                "Shift_JIS"
            } else {
                "ISO-8859-1"
            }
        }
        // 再不成，按 ISO-8859-1、Shift_JIS、UTF-8 的顺序试，最后落到平台默认编码
        if (canBeIso88591) return "ISO-8859-1"
        if (canBeShiftJis) return "Shift_JIS"
        if (canBeUtf8) return "UTF-8"
        // 实在猜不出，就按平台默认编码来（JVM 上就是 UTF-8）
        return "UTF-8"
    }
}

/** 替换字符：解不出来时填的那个（与 `new String(bytes, charset)` 的宽松口径同一个字）。 */
private const val REPLACEMENT = '\uFFFD'

/** 码表里的两种「坏格子」—— 见 [QrCharsetTable]。 */
private const val UNMAPPED_CELL = '\uFFFD'
private const val INVALID_CELL = '\u0000'

/**
 * 一张码表（[QrCjkTables] 里的每一个 `val` 都是它）。
 *
 * 布局（生成器那边是同一份定义，改一处要改两处）：
 *
 *  - [singleCps]：256 项。`singleCps[b] >= 0` = 字节 `b` 自己就是一个字符（就是那个码位）；`-1` = 它不是单字节字符；
 *  - [rows] / [cols]：256 项。首字节 → 行号、尾字节 → 列号，`-1` = 不是首/尾字节；
 *  - [flat]：行主序的 `rows × nCols` 表，格子三种：正常字符、[UNMAPPED_CELL]（结构合法但没映射，吞 2 字节）、
 *    [INVALID_CELL]（结构不合法，只吞 1 字节）。
 *
 * 单字节集没有双字节那一半 ⇒ [rows] 全 `-1`、[flat] 是空的。
 */
internal class QrCharsetTable private constructor(
    val singleCps: IntArray,
    val rows: IntArray,
    val cols: IntArray,
    val nCols: Int,
    val flat: String,
) {

    /**
     * `(首字节, 尾字节)` → 那一格；`rows[lead] < 0` 或 `cols[trail] < 0` 都算 [INVALID_CELL]（只吞首字节）。
     */
    fun cell(lead: Int, trail: Int): Char {
        val row = rows[lead]
        if (row < 0) return INVALID_CELL
        val col = cols[trail]
        if (col < 0) return INVALID_CELL
        return flat[row * nCols + col]
    }

    internal companion object {
        /** 单字节集：只有 [singleCps]。 */
        fun single(singleCps: IntArray): QrCharsetTable =
            QrCharsetTable(singleCps, NO_ROWS, NO_ROWS, 0, "")

        /** 双字节集：单字节 + 双字节那半张表。 */
        fun double(
            singleCps: IntArray,
            rows: IntArray,
            cols: IntArray,
            nCols: Int,
            flat: String,
        ): QrCharsetTable = QrCharsetTable(singleCps, rows, cols, nCols, flat)

        private val NO_ROWS = IntArray(256) { -1 }
    }
}

/**
 * GB18030 的四字节形态：指针 → 码位。
 *
 * 同一个 `[starts[i], ends[i]]` 段里，指针每 +1、码位也 +1（起始码位在 [cps] 里）；段之间的空档就是「没有映射」。
 * 指针超出定义范围（≥ 39420）时 [lookup] 返回 -1，[QrCharset.decodeGb18030] 给替换字符。
 */
internal class QrGb18030Four(
    private val starts: IntArray,
    private val ends: IntArray,
    private val cps: IntArray,
) {

    /** 指针 → 码位；没有映射返回 -1。二分找段。 */
    fun lookup(pointer: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            when {
                pointer < starts[mid] -> hi = mid - 1
                pointer > ends[mid] -> lo = mid + 1
                else -> return cps[mid] + (pointer - starts[mid])
            }
        }
        return -1
    }
}

/** commonMain 的 `StringBuilder` 没有 `appendCodePoint`；GB18030 四字节形态会映到补充平面，要拼代理对。 */
private fun StringBuilder.appendCodePoint(cp: Int) {
    if (cp <= 0xFFFF) {
        append(cp.toChar())
    } else {
        val v = cp - 0x10000
        append((0xD800 + (v shr 10)).toChar())
        append((0xDC00 + (v and 0x3FF)).toChar())
    }
}
