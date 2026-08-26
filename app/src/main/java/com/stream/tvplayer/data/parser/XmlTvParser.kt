package com.stream.tvplayer.data.parser

import android.util.Xml
import com.stream.tvplayer.data.local.EpgEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

object XmlTvParser {
    private val xmlTvDateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            val cleanStr = dateStr.trim()
            val formatted = if (!cleanStr.contains(" ")) "$cleanStr +0000" else cleanStr
            xmlTvDateFormat.parse(formatted)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun parseStream(
        inputStream: InputStream,
        onBatchParsed: suspend (List<EpgEntity>) -> Unit
    ) {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(inputStream, "UTF-8")
        }

        val batch = mutableListOf<EpgEntity>()
        var eventType = parser.eventType
        var currentChannel: String? = null
        var currentStart = 0L
        var currentEnd = 0L
        var currentTitle: String? = null
        var currentDesc: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "programme" -> {
                            currentChannel = parser.getAttributeValue(null, "channel")
                            currentStart = parseDate(parser.getAttributeValue(null, "start"))
                            currentEnd = parseDate(parser.getAttributeValue(null, "stop"))
                            currentTitle = null
                            currentDesc = null
                        }
                        "title" -> if (currentChannel != null) currentTitle = parser.nextText()
                        "desc" -> if (currentChannel != null) currentDesc = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "programme" && currentChannel != null && currentTitle != null) {
                        batch.add(
                            EpgEntity(
                                channelId = currentChannel,
                                title = currentTitle,
                                description = currentDesc,
                                startEpochMs = currentStart,
                                endEpochMs = currentEnd
                            )
                        )
                        if (batch.size >= 500) {
                            kotlinx.coroutines.runBlocking { onBatchParsed(batch.toList()) }
                            batch.clear()
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        if (batch.isNotEmpty()) {
            kotlinx.coroutines.runBlocking { onBatchParsed(batch.toList()) }
            batch.clear()
        }
    }
}
