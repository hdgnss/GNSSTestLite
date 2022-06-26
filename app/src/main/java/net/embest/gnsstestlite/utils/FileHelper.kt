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

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.hdgnss.gnsstestlite.R
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter


class FileHelper {

    private val mExternalPath = EXTERNALPATH + "GNSSTestLite/"

    init {
        val file = File(mExternalPath)
        if (!file.exists()) {
            file.mkdirs()
        }
        val fileConfig = File(mExternalPath, "config")
        if (!fileConfig.exists()) {
            fileConfig.mkdirs()
        }
    }

    fun writeNmeaFile(fileName: String, data: String) {
        if (mExternalPath != "") {
            try {
                val file = File(mExternalPath + fileName + "_nmea.txt")
                if (!file.exists()) {

                    file.createNewFile()
                }
                val stream = OutputStreamWriter(FileOutputStream(file, true))

                stream.write(data)
                stream.close()
            } catch (e: Exception) {
                Log.d(TAG, "file create error")
            }
        }
    }

    fun writeMeasurementFile(fileName: String, data: String) {
        if (mExternalPath != "") {
            try {
                val file = File(mExternalPath + fileName + "_raw.txt")
                if (!file.exists()) {

                    file.createNewFile()

                    val fileVersion = (
                            "# Version: "
                                    + LOGGER_VERSION
                                    + " Platform: "
                                    + Build.VERSION.RELEASE
                                    + " "
                                    + "Manufacturer: "
                                    + Build.MANUFACTURER
                                    + " "
                                    + "Model: "
                                    + Build.MODEL
                                    + "\r\n")

                    val header: String = ("# \r\n# Header Description:\r\n# \r\n"
                            + fileVersion
                            + "# Raw,ElapsedRealtimeMillis,TimeNanos,LeapSecond,TimeUncertaintyNanos,FullBiasNanos,"
                            + "BiasNanos,BiasUncertaintyNanos,DriftNanosPerSecond,DriftUncertaintyNanosPerSecond,"
                            + "HardwareClockDiscontinuityCount,Svid,TimeOffsetNanos,State,ReceivedSvTimeNanos,"
                            + "ReceivedSvTimeUncertaintyNanos,Cn0DbHz,PseudorangeRateMetersPerSecond,"
                            + "PseudorangeRateUncertaintyMetersPerSecond,"
                            + "AccumulatedDeltaRangeState,AccumulatedDeltaRangeMeters,"
                            + "AccumulatedDeltaRangeUncertaintyMeters,CarrierFrequencyHz,CarrierCycles,"
                            + "CarrierPhase,CarrierPhaseUncertainty,MultipathIndicator,SnrInDb,"
                            + "ConstellationType,AgcDb,CarrierFrequencyHz\r\n"
                            + "# \r\n")
                    val stream = OutputStreamWriter(FileOutputStream(file, true))
                    stream.write(header)
                    stream.close()
                }
                val stream = OutputStreamWriter(FileOutputStream(file, true))

                stream.write(data)
                stream.close()
            } catch (e: Exception) {
                Log.d(TAG, "file create error")
            }
        }
    }

    fun writeResultFile(fileName: String, data: String) {
        if (mExternalPath != "") {
            try {
                val file = File(mExternalPath + fileName + "_result.txt")
                if (!file.exists()) {
                    file.createNewFile()
                }
                val stream = OutputStreamWriter(FileOutputStream(file, true))

                stream.write(data)
                stream.close()
            } catch (e: Exception) {
                Log.d(TAG, "file create error")
            }
        }
    }

    fun getTestJobs(): ArrayList<String> {
        val extension = "xml"
        val lstFile = ArrayList<String>()
        val files = File("$mExternalPath/config").listFiles().sorted()

        for (i in files.indices) {
            val f = files[i]
            if (f.isFile && !f.isHidden) {
                if (f.path.substring(f.path.length - extension.length) == extension) {
                    lstFile.add(f.name.substring(0, f.name.length - 4))
                }
            }
        }
        return lstFile
    }

//    fun readConfigFile(fileName: String): String{
//        var input = ""
//        if (mExternalPath != "") {
//            try {
//                val file = File(mExternalPath + "config/", fileName )
//                if (!file.exists()) {
//                    file.createNewFile()
//                }
//
//                input = FileInputStream(file).bufferedReader().use { it.readText() }
//
//            } catch (e: Exception) {
//                Log.d(TAG, "file create error")
//            }
//        }
//        return input
//    }


    fun initConfigFile(context: Context) {
        val file = File(mExternalPath)
        if (!file.exists()) {
            file.mkdirs()
        }
        val fileConfig = File(mExternalPath, "config")
        if (!fileConfig.exists()) {
            fileConfig.mkdirs()
        }
        val readWriteMap = hashMapOf(
            "config/Track.xml" to R.raw.track,
            "config/Hot.xml" to R.raw.hot,
            "config/Warm.xml" to R.raw.warm,
            "config/Cold.xml" to R.raw.cold,
            "config/Cold_T.xml" to R.raw.cold_time,
            "config/Cold_TL.xml" to R.raw.cold_time_lto
        )

        for ((name, id) in readWriteMap) {
            val f = File(mExternalPath, name)
            if (!f.exists()) {
                try {
                    val input = context.resources.openRawResource(id)
                    val output = FileOutputStream(f, false)
                    val data = ByteArray(1024 * 1024)
                    var count: Int
                    while (true) {
                        count = input.read(data, 0, 1024 * 1024)
                        if (count == -1)
                            break
                        output.write(data, 0, count)
                    }
                    output.flush()
                    output.close()
                    input.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error:$e")
                }
            }
        }
    }

    companion object {
        private const val TAG = "GNSSTestLite"
        private const val LOGGER_VERSION = "v2.0.0.1"
        val EXTERNALPATH = Environment.getExternalStorageDirectory().path + "/"
    }
}