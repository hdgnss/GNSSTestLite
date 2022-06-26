/*
 * Copyright (C) 2022 HDGNSS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hdgnss.gnsstestlite.utils

import android.os.Environment
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileInputStream
import java.io.IOException


class XmlHelper {

    private val mExternalPath = EXTERNAL_PATH + "GNSSTestLite/config"

    private val mTestJob = TestJob()

    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(file: String): TestJob {
        mTestJob.cleanJob()
        mTestJob.name = file.substring(0, file.length - 4)
        val initialFile = File(mExternalPath, file)
        val inputStream = FileInputStream(initialFile)
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            readTest(parser)
            return mTestJob
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readTest(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, ns, "test")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "round" -> {
                    mTestJob.round = readLong(parser, "round")
                }
                "cep" -> {
                    mTestJob.cep = readString(parser, "cep")
                }
                "delay" -> {
                    mTestJob.delay = readLong(parser, "delay")
                }
                "request" -> {
                    mTestJob.request = readLong(parser, "request")
                }
                "timeout" -> {
                    mTestJob.timeout = readLong(parser, "timeout")
                }
                "delete" -> {
                    mTestJob.delete = readInt(parser, "delete")
                }
                "inject_time" -> {
                    mTestJob.inject_time = readBoolean(parser, "inject_time")
                }
                "inject_xtra" -> {
                    mTestJob.inject_xtra = readBoolean(parser, "inject_xtra")
                }
                else -> skip(parser)
            }
        }
    }


    @Throws(IOException::class, XmlPullParserException::class)
    private fun readLong(parser: XmlPullParser, tag: String): Long {
        parser.require(XmlPullParser.START_TAG, ns, tag)
        val text = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, tag)
        return text.toLong()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readInt(parser: XmlPullParser, tag: String): Int {
        parser.require(XmlPullParser.START_TAG, ns, tag)
        val text = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, tag)
        return text.toInt()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readString(parser: XmlPullParser, tag: String): String {
        parser.require(XmlPullParser.START_TAG, ns, tag)
        val text = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, tag)
        return text
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readBoolean(parser: XmlPullParser, tag: String): Boolean {
        parser.require(XmlPullParser.START_TAG, ns, tag)
        val text = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, tag)
        return text.toBoolean()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    companion object {
        //private const val TAG = "GNSSTestXml"
        private val ns: String? = null
        private val EXTERNAL_PATH = Environment.getExternalStorageDirectory().path + "/"
    }
}