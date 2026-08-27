package com.stream.tvplayer.data.parser

import android.util.Xml
import com.stream.tvplayer.data.local.EpgEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XmlTvParser {

    private val xmlTvDateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun parseStream(
        inputStream: InputStream,
        batchSize: Int = 200,
        onBatchParsed: suspend (List<EpgEntity>) -> Unit
    ) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        val batch = mutableListOf<EpgEntity>()

        var currentChannelTvgId: String? = null
        var currentStartMs: Long = 0L
        var currentEndMs: Long = 0L
        var currentTitle: String = ""
        var currentDesc: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "programme" -> {
                            currentChannelTvgId = parser.getAttributeValue(null, "channel")
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")

                            currentStartMs = parseXmlTvDate(startStr)
                            currentEndMs = parseXmlTvDate(stopStr)
                            currentTitle = "No Title"
                            currentDesc = null
                        }
                        "title" -> {
                            if (currentChannelTvgId != null) {
                                currentTitle = parser.nextText() ?: "No Title"
                            }
                        }
                        "desc" -> {
                            if (currentChannelTvgId != null) {
                                currentDesc = parser.nextText()
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "programme" && currentChannelTvgId != null) {
                        if (currentEndMs > System.currentTimeMillis() - 86400000L) {
                            batch.add(
                                EpgEntity(
                                    channelTvgId = currentChannelTvgId,
                                    title = currentTitle,
                                    description = currentDesc,
                                    startEpochMs = currentStartMs,
                                    endEpochMs = currentEndMs
                                )
                            )
                        }

                        if (batch.size >= batchSize) {
                            onBatchParsed(batch.toList())
                            batch.clear()
                        }
                        currentChannelTvgId = null
                    }
                }
            }
            eventType = parser.next()
        }

        if (batch.isNotEmpty()) {
            onBatchParsed(batch.toList())
            batch.clear()
        }
    }

    private fun parseXmlTvDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            val normalized = if (!raw.contains(" ")) {
                if (raw.length >= 14) "${raw.substring(0, 14)} +0000" else raw
            } else raw
            xmlTvDateFormat.parse(normalized)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
