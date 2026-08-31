package com.dk.tvplayer.data.parser

import android.util.Xml
import com.dk.tvplayer.data.local.TvEpgProgramEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XmlTvParser {
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun parse(inputStream: InputStream): List<TvEpgProgramEntity> {
        val programs = mutableListOf<TvEpgProgramEntity>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentChannel: String? = null
        var startTime: Long = 0
        var endTime: Long = 0
        var title: String? = null
        var description: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if ("programme".equals(tagName, ignoreCase = true)) {
                        currentChannel = parser.getAttributeValue(null, "channel")
                        val startStr = parser.getAttributeValue(null, "start")
                        val stopStr = parser.getAttributeValue(null, "stop")
                        startTime = parseDate(startStr)
                        endTime = parseDate(stopStr)
                    } else if ("title".equals(tagName, ignoreCase = true)) {
                        title = parser.nextText()
                    } else if ("desc".equals(tagName, ignoreCase = true)) {
                        description = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if ("programme".equals(tagName, ignoreCase = true)) {
                        val chId = currentChannel
                        val progTitle = title
                        if (chId != null && progTitle != null) {
                            programs.add(
                                TvEpgProgramEntity(
                                    channelId = chId,
                                    title = progTitle,
                                    description = description,
                                    startTime = startTime,
                                    endTime = endTime
                                )
                            )
                        }
                        currentChannel = null
                        title = null
                        description = null
                    }
                }
            }
            eventType = parser.next()
        }
        return programs
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
