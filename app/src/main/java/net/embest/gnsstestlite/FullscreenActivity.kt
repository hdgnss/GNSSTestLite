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

package com.hdgnss.gnsstestlite

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast.LENGTH_SHORT
import android.widget.Toast.makeText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_fullscreen.*
import com.hdgnss.gnsstestlite.utils.*
import java.text.SimpleDateFormat
import java.util.*


class FullscreenActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {
    private var mJobStarted: Boolean = false
    private val mContext by lazy { this }

    private var mPosition: Int = 0

    private val mFile = FileHelper()

    private var mGnssInfo = GnssInfo()
    private var mTestJob = TestJob()
    private val mXmlHelper = XmlHelper()

    private var mRecordFileName: String = "gnss_record"

    private var mService: LocationManager? = null
    private var mProvider: LocationProvider? = null
    private var mGnssStarted: Boolean = false

    private var mTrueLocation: Location? = null
    private var mFirstFixed = false
    private var mFirstFixedNmea = false

    private var mStartTimeStamp = 0L


    private var mGnssStatusCallBack: GnssStatus.Callback? = null
    private var mOnNmeaMessageListener: OnNmeaMessageListener? = null
    private var mGpsStatusListener: GpsStatus.Listener? = null
    private var mGpsStatusNmeaListener: GpsStatus.NmeaListener? = null
    private var mGnssMeasurementsCallBack: GnssMeasurementsEvent.Callback? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        supportActionBar?.hide()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        checkAndRequestPermissions()

        isGooglePlayServicesAvailable(mContext)

        Log.e(TAG, "Android SDK: ${Build.VERSION.SDK_INT}")

        dummy_button.setOnClickListener {
            if (!mJobStarted) {
                mTestJob.newTest()
                mJobStarted = true
                onMakeRecordName()
                dummy_button.text = "Stop"
                Log.d(TAG, mTestJob.name + ":" + mTestJob.round)
                onStartTest()
            } else {
                mJobStarted = false
                dummy_button.text = "Start"
                removeTimeOutTimer()
                removeRequestTimer()
                removeJobTimer()
                gpsStop()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "onDestroy()")
        unregisterCallbacks()
    }

    override fun onResume() {
        Log.e(TAG, "onResume()")
        super.onResume()
        //Keep screen
        val job = mFile.getTestJobs()
        val jobAdapter = ArrayAdapter<String>(mContext, android.R.layout.simple_spinner_item, job)
        jobAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerJob.adapter = jobAdapter
        if (mPosition >= job.size) {
            mPosition = 0
        }

        spinnerJob.setSelection(mPosition)
        spinnerJob.onItemSelectedListener = this
    }


    override fun onPause() {
        super.onPause()
        Log.e(TAG, "onPause")
    }

    private var mJobHandler = Handler()

    private fun removeJobTimer() {
        mJobHandler.removeCallbacks(mJobRunnable)
    }

    private var mJobRunnable: Runnable = Runnable {
        Log.i(TAG, "mJobRunnable....")
        mGnssInfo.reset()
        gpsStart(1000, 0.0F)
        addTimeOutTimer(mTestJob.timeout)
    }

    private fun onStartTest() {
        if (!mTestJob.finished) {
            mJobHandler.postDelayed(mJobRunnable, mTestJob.delay)
        } else {
            Log.e(TAG, "onStartTest Done")
            dummy_button.text = "Start"
            mJobStarted = false
            mTestJob.testDone()
        }
        onUpdateView()
    }


    override fun onNothingSelected(parent: AdapterView<*>?) {
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        mPosition = position

        val mJobFileName = parent!!.getItemAtPosition(position).toString() + ".xml"


        mTestJob = mXmlHelper.parse(mJobFileName)

        mTrueLocation = Location("")

        if (mTestJob.cep != "") {
            val lonlat = mTestJob.cep.split(',')
            mTrueLocation!!.latitude = lonlat[0].toDouble()
            mTrueLocation!!.longitude = lonlat[1].toDouble()

            Log.e(
                TAG,
                "CEP:${mTestJob.cep}, ${mTrueLocation!!.latitude},${mTrueLocation!!.longitude}"
            )
        } else {
            Log.e(TAG, "NO CEP")
            mTrueLocation!!.latitude = 361.0
            mTrueLocation!!.longitude = 361.0
        }
        if (mTestJob.delay < 100L) {
            mTestJob.delay = 100L
        }

        mGnssInfo.reset()

        Log.e(TAG, "${mTestJob.name}, ${mTestJob.round}, ${mTestJob.request}, ${mTestJob.delete}")
    }


    private val mLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {

            mGnssInfo.speed = location.speed
            mGnssInfo.altitude = location.altitude
            mGnssInfo.latitude = location.latitude
            mGnssInfo.longitude = location.longitude
            mGnssInfo.time = location.time
            if (mTrueLocation!!.latitude > 360 && mTrueLocation!!.longitude > 360) {
                mGnssInfo.accuracy = location.accuracy
            } else {
                mGnssInfo.accuracy = mTrueLocation!!.distanceTo(location)
            }

            Log.e(TAG, "onLocationChanged")
            onUpdateView()
        }

        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, "LocationListener onProviderDisabled")
        }

        override fun onProviderEnabled(provider: String) {
            Log.i(TAG, "LocationListener onProviderEnabled")
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            Log.e(TAG, "LocationListener onStatusChanged")
        }
    }

    private fun registerNmeaMessageListener() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (mOnNmeaMessageListener == null) {
                    mOnNmeaMessageListener = OnNmeaMessageListener { message, _ ->
                        mFile.writeNmeaFile(mRecordFileName + "_" + (mTestJob.count), message)
                        if (!mFirstFixedNmea) {
                            if (message.indexOf("RMC") > 0) {
                                val messageList = message.split(',')
                                if (messageList[2] == "A") {
                                    mGnssInfo.ttff_nmea =
                                        (System.currentTimeMillis() - mStartTimeStamp) / 1000.0f
                                    mFirstFixedNmea = true
                                    Log.e(TAG, "FIXED:NMEA N:${mGnssInfo.ttff_nmea}")
                                    addCheckAndroidFixTimer(3)
                                }
                            }
                        }
                    }
                }
                mService!!.addNmeaListener(mOnNmeaMessageListener)
            } else {
                if (mGpsStatusNmeaListener == null) {
                    mGpsStatusNmeaListener = GpsStatus.NmeaListener { timestamp, message ->
                        mFile.writeNmeaFile(mRecordFileName + "_" + (mTestJob.count), message)
                        if (!mFirstFixedNmea) {
                            if (message.indexOf("RMC") > 0) {
                                val messageList = message.split(',')
                                if (messageList[2] == "A") {
                                    mGnssInfo.ttff_nmea =
                                        (System.currentTimeMillis() - mStartTimeStamp) / 1000.0f

                                    var nmeaLocation = Location("")

                                    nmeaLocation.latitude =
                                        ParseLatitude(messageList[3], messageList[4])
                                    nmeaLocation.longitude =
                                        ParseLongitude(messageList[5], messageList[6])

                                    if (mTrueLocation!!.latitude > 360 && mTrueLocation!!.longitude > 360) {
                                        mGnssInfo.acc_nmea = 999.0f
                                    } else {
                                        mGnssInfo.acc_nmea =
                                            mTrueLocation!!.distanceTo(nmeaLocation)
                                    }

                                    mFirstFixedNmea = true
                                    Log.e(TAG, "FIXED:NMEA:${mGnssInfo.ttff_nmea}")
                                    addCheckAndroidFixTimer(3)
                                }
                            }
                        }
                    }
                }
                mService!!.addNmeaListener(mGpsStatusNmeaListener)
            }
        }
    }


    private fun ParseLatitude(s: String, d: String): Double {
        val _lat = s.toDoubleOrNull() // StringTools.parseDouble(s, 99999.0)
        return if (_lat != null) {
            var lat = (_lat.toLong() / 100L).toDouble() // _lat is always positive here
            lat += (_lat - lat * 100.0) / 60.0
            if (d.equals("S", ignoreCase = true)) -lat else lat
        } else {
            90.0 // invalid latitude
        }

    }

    private fun ParseLongitude(s: String, d: String): Double {
        val _lon = s.toDoubleOrNull()
        return if (_lon != null) {
            var lon = (_lon.toLong() / 100L).toDouble()
            lon += (_lon - (lon * 100.0)) / 60.0
            if (d.equals("W", ignoreCase = true)) -lon else lon
        } else {
            180.0 // invalid longitude
        }
    }


    private fun registerGnssStatusCallback() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (mGpsStatusListener == null) {
                mGpsStatusListener = GpsStatus.Listener { event ->
                    val status = mService!!.getGpsStatus(null)
                    when (event) {
                        GpsStatus.GPS_EVENT_FIRST_FIX -> {
                            if (!mFirstFixed) {
                                mGnssInfo.ttff = status.timeToFirstFix / 1000f
                                mFirstFixed = true
                                Log.e(TAG, "TTFF(KK):${mGnssInfo.ttff}")
                                if (mTestJob.request > 1) {
                                    addRequestTimer(mTestJob.request)
                                } else {
                                    onOneTestDone(true)
                                }
                            }
                            Log.e(TAG, "Android KK Fixed, No need NMEA check.")
                            removeCheckAndroidFixTimer()
                        }
                        GpsStatus.GPS_EVENT_SATELLITE_STATUS -> {
                            var use = 0
                            var view = 0

                            val itrator = status.satellites.iterator()
                            mGnssInfo.satellites.clear()
                            while (itrator.hasNext() && view <= status.maxSatellites) {
                                val sat = itrator.next()
                                if (sat.snr > 0) {
                                    view++
                                    val satellite = GnssSatellite()
                                    satellite.svid = sat.prn
                                    satellite.cn0 = sat.snr
                                    satellite.inUse = sat.usedInFix()
                                    mGnssInfo.addSatellite(satellite)
                                    if (sat.usedInFix()) {
                                        use++
                                    }
                                }
                            }
                            var cn0 = 0.0f
                            var count = 4
                            if (count > mGnssInfo.satellites.size) {
                                count = mGnssInfo.satellites.size
                            }
                            var string_cn0 = ""
                            for (i in 0 until count) {
                                cn0 += mGnssInfo.satellites[i].cn0
                                string_cn0 += "Prn: ${mGnssInfo.satellites[i].svid} CN0  ${mGnssInfo.satellites[i].cn0} "

                            }


                            mGnssInfo.top4 = cn0 / count
                            Log.d(TAG, "AVG CN0: ${mGnssInfo.top4}: $string_cn0")
                            mGnssInfo.inuse = use
                            mGnssInfo.inview = view
                            onUpdateView()
                        }
                        else -> {
                        }
                    }
                }
            }
            mService!!.addGpsStatusListener(mGpsStatusListener)
        }
    }


    private fun onOneTestDone(firstFix: Boolean) {
        gpsStop()

        if (firstFix) {
            val data =
                "Index:${mTestJob.count},TTFF:${mGnssInfo.ttff},TTFFNMEA:${mGnssInfo.ttff_nmea},ACC:${mGnssInfo.accuracy},ACC_NMEA:${mGnssInfo.acc_nmea}\r\n"
            mFile.writeResultFile(mRecordFileName, data)
        } else {
            val data =
                "Index:${mTestJob.count},TTFF:${mTestJob.timeout},TTFFNMEA:${mTestJob.timeout}\r\n"
            mFile.writeResultFile(mRecordFileName, data)
        }

        mTestJob.oneTestDone()
        Log.e(TAG, "onOneTestDone. Index: ${mTestJob.count}")

        onUpdateView()
        onStartTest()
    }


    private fun onUpdateView() {

        textInfoTtff.text = mGnssInfo.ttff.toString()
        if (mGnssInfo.accuracy > 0)
            textInfoAcc.text = String.format("%.1f", mGnssInfo.accuracy)
        else
            textInfoAcc.text = String.format("%.1f", mGnssInfo.acc_nmea)
        textInfoLon.text = String.format("%.3f", mGnssInfo.longitude)
        textInfoLat.text = String.format("%.3f", mGnssInfo.latitude)
        textInfoInview.text = mGnssInfo.inview.toString()
        textInfoInuse.text = mGnssInfo.inuse.toString()
        textInfoCN0.text = String.format("%.1f", mGnssInfo.top4)
        textInfoIndex.text = mTestJob.count.toString()

    }

    private var mTimerHandler = Handler()

    private fun addTimeOutTimer(time: Long) {
        if (time > 0) {
            mTimerHandler.removeCallbacks(mTimerOutRunnable)
            mTimerHandler.postDelayed(mTimerOutRunnable, time * 1000)
        }
    }

    private fun removeTimeOutTimer() {
        mTimerHandler.removeCallbacks(mTimerOutRunnable)
    }

    private var mTimerOutRunnable: Runnable = Runnable {
        Log.i(TAG, "TimerOut....")
        onOneTestDone(false)
    }

    private fun addRequestTimer(time: Long) {
        if (time > 0) {
            removeRequestTimer()
            mTimerHandler.postDelayed(mRequestRunnable, time * 1000)
        }
    }

    private fun removeRequestTimer() {
        mTimerHandler.removeCallbacks(mRequestRunnable)
    }


    private var mRequestRunnable: Runnable = Runnable {
        Log.i(TAG, "Request....")
        onOneTestDone(true)
    }

    private fun addCheckAndroidFixTimer(time: Long) {
        if (time > 0) {
            removeCheckAndroidFixTimer()
            mTimerHandler.postDelayed(mCheckAndroidFixRunnable, time * 1000)
        }
    }

    private fun removeCheckAndroidFixTimer() {
        mTimerHandler.removeCallbacks(mCheckAndroidFixRunnable)
    }

    private var mCheckAndroidFixRunnable: Runnable = Runnable {

        if (!mFirstFixed) {
            Log.e(TAG, "No Android Fix!")
            if (mTestJob.request > 3) {
                addRequestTimer(mTestJob.request - 3)
            } else {
                onOneTestDone(true)
            }
        }
    }


    private fun unregisterCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (mGnssStatusCallBack != null) {
                mService!!.unregisterGnssStatusCallback(mGnssStatusCallBack)
                mGnssStatusCallBack = null
            }
        } else {
            mService!!.removeGpsStatusListener(mGpsStatusListener)
        }
        if (mGnssMeasurementsCallBack != null) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mService!!.unregisterGnssMeasurementsCallback(mGnssMeasurementsCallBack)
            }
            mGnssMeasurementsCallBack = null
        }
    }


    private fun onUpdatePermissions() {
        mService = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (Build.VERSION.SDK_INT >= 28) {
            Log.e(
                TAG,
                "gnssHardwareModelName: " + mService!!.gnssHardwareModelName + mService!!.gnssYearOfHardware
            )
        }

        mProvider = mService!!.getProvider(LocationManager.GPS_PROVIDER)

        if (mProvider == null) {
            Log.e(TAG, "Unable to get GPS_PROVIDER")
            makeText(this, getString(R.string.gps_not_supported), LENGTH_SHORT).show()
            finish()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                Log.e(TAG, "lastLocation:$location")
            }
        mFile.initConfigFile(mContext)
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionWrite =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val permissionLocation =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        val listPermissionsNeeded = ArrayList<String>()

        if (permissionWrite != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissionLocation != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionsNeeded.toTypedArray(),
                REQUEST_ID_MULTIPLE_PERMISSIONS
            )
            return false
        }
        onUpdatePermissions()
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.d(TAG, "Permission callback called")
        when (requestCode) {
            REQUEST_ID_MULTIPLE_PERMISSIONS -> {
                val perms = HashMap<String, Int>()
                perms[Manifest.permission.WRITE_EXTERNAL_STORAGE] =
                    PackageManager.PERMISSION_GRANTED
                perms[Manifest.permission.ACCESS_FINE_LOCATION] = PackageManager.PERMISSION_GRANTED
                if (grantResults.isNotEmpty()) {
                    for (i in permissions.indices)
                        perms[permissions[i]] = grantResults[i]
                    if (perms[Manifest.permission.WRITE_EXTERNAL_STORAGE] == PackageManager.PERMISSION_GRANTED
                        && perms[Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.e(TAG, "storage & location services permission granted")
                        onUpdatePermissions()
                    } else {
                        Log.d(TAG, "Some permissions are not granted ask again ")
                        if (ActivityCompat.shouldShowRequestPermissionRationale(
                                this,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                            || ActivityCompat.shouldShowRequestPermissionRationale(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        ) {
                            val dialog = AlertDialog.Builder(this)
                            dialog.setMessage(R.string.msg_request_permissions)
                                .setPositiveButton(R.string.msg_dialog_yes) { _, _ -> checkAndRequestPermissions() }
                                .setNegativeButton(R.string.msg_dialog_cancel) { _, _ -> finish() }
                            dialog.show()
                        } else {
                            val dialog = AlertDialog.Builder(this)
                            dialog.setMessage(R.string.msg_request_permissions_settings)
                                .setPositiveButton(R.string.msg_dialog_yes) { _, _ ->
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:com.hdgnss.gnsstestlite")
                                        )
                                    )
                                }
                                .setNegativeButton(R.string.msg_dialog_cancel) { _, _ -> finish() }
                            dialog.show()
                        }
                    }
                }
            }
        }
    }

    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        val googleApiAvailability: GoogleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode: Int = googleApiAvailability.isGooglePlayServicesAvailable(context)
        if (resultCode !== ConnectionResult.SUCCESS) {
            Log.e(TAG, "isGooglePlayServicesAvailable")
            return false
        }
        Log.e(TAG, "isGooglePlayServicesAvailable: Yes")
        return true
    }

    private fun onMakeRecordName() {
        val lTime = System.currentTimeMillis()

        val time = SimpleDateFormat("yyMMdd_HHmmss", Locale.US)

        mRecordFileName = Build.MODEL + "_" + mTestJob.name + "_" + time.format(lTime)
        mRecordFileName = mRecordFileName.replace("-", "_")
        mRecordFileName = mRecordFileName.replace(" ", "_")
        mRecordFileName = mRecordFileName.replace(".", "")

        Log.i(TAG, "Name:$mRecordFileName")
    }


    private fun deleteAidingData(delete: Int) {
        val bundle = Bundle()
        if (delete and GPS_DELETE_EPHEMERIS == GPS_DELETE_EPHEMERIS)
            bundle.putBoolean("ephemeris", true)
        if (delete and GPS_DELETE_ALMANAC == GPS_DELETE_ALMANAC)
            bundle.putBoolean("almanac", true)
        if (delete and GPS_DELETE_POSITION == GPS_DELETE_POSITION)
            bundle.putBoolean("position", true)
        if (delete and GPS_DELETE_TIME == GPS_DELETE_TIME)
            bundle.putBoolean("time", true)
        if (delete and GPS_DELETE_IONO == GPS_DELETE_IONO)
            bundle.putBoolean("iono", true)
        if (delete and GPS_DELETE_UTC == GPS_DELETE_UTC)
            bundle.putBoolean("utc", true)
        if (delete and GPS_DELETE_HEALTH == GPS_DELETE_HEALTH)
            bundle.putBoolean("health", true)
        if (delete and GPS_DELETE_SVDIR == GPS_DELETE_SVDIR)
            bundle.putBoolean("svdir", true)
        if (delete and GPS_DELETE_SVSTEER == GPS_DELETE_SVSTEER)
            bundle.putBoolean("svsteer", true)
        if (delete and GPS_DELETE_SADATA == GPS_DELETE_SADATA)
            bundle.putBoolean("sadata", true)
        if (delete and GPS_DELETE_RTI == GPS_DELETE_RTI)
            bundle.putBoolean("rti", true)
        if (delete and GPS_DELETE_CELLDB_INFO == GPS_DELETE_CELLDB_INFO)
            bundle.putBoolean("celldb-info", true)
        if (delete and GPS_DELETE_ALL == GPS_DELETE_ALL)
            bundle.putBoolean("all", true)
//        Log.e(TAG, "delete_aiding_data: $bundle")
        sendExtraCommand("delete_aiding_data", bundle)
    }


    private fun gpsStart(minTime: Long, minDis: Float) {
        if (!mGnssStarted) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (mTestJob.delete > 0) {
                    deleteAidingData(mTestJob.delete)
                }
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
//                    mService!!.startGnssBatch()
//                }

                mService!!.requestLocationUpdates(
                    mProvider!!.name,
                    minTime,
                    minDis,
                    mLocationListener
                )
                mStartTimeStamp = System.currentTimeMillis()
                mGnssStarted = true
                mFirstFixed = false
                mFirstFixedNmea = false
                if (mTestJob.delete > 0) {
                    deleteAidingData(mTestJob.delete)
                }
                if (mTestJob.inject_time) {
                    val bundle = Bundle()
                    sendExtraCommand("force_time_injection", bundle)
                }

                if (mTestJob.inject_xtra) {
                    val bundle = Bundle()
                    sendExtraCommand("force_xtra_injection", bundle)
                }
                registerGnssStatusCallback()
                registerNmeaMessageListener()
                registerGnssMeasurementsCallback()
            }
        }
    }


    private fun gpsStop() {
        if (mGnssStarted) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mService!!.removeUpdates(mLocationListener)
                mGnssStarted = false
            }
        }
        unregisterCallbacks()
    }


    private fun sendExtraCommand(command: String, bundle: Bundle) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mService!!.sendExtraCommand(mProvider!!.name, command, bundle)
            Log.e(TAG, "sendExtraCommand: ${mProvider!!.name} $command")
        }
    }

    private fun registerGnssMeasurementsCallback() {
        if (mGnssMeasurementsCallBack == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mGnssMeasurementsCallBack = object : GnssMeasurementsEvent.Callback() {
                    override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                        for (meas in event.measurements) {
                            val measurementStream =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    "Raw,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\r\n".format(
                                        SystemClock.elapsedRealtime(),
                                        event.clock.timeNanos,
                                        event.clock.leapSecond,
                                        event.clock.timeUncertaintyNanos,
                                        event.clock.fullBiasNanos,
                                        event.clock.biasNanos,
                                        event.clock.biasUncertaintyNanos,
                                        event.clock.driftNanosPerSecond,
                                        event.clock.driftUncertaintyNanosPerSecond,
                                        event.clock.hardwareClockDiscontinuityCount,
                                        meas.svid,
                                        meas.timeOffsetNanos,
                                        meas.state,
                                        meas.receivedSvTimeNanos,
                                        meas.receivedSvTimeUncertaintyNanos,
                                        meas.cn0DbHz,
                                        meas.pseudorangeRateMetersPerSecond,
                                        meas.pseudorangeRateUncertaintyMetersPerSecond,
                                        meas.accumulatedDeltaRangeState,
                                        meas.accumulatedDeltaRangeMeters,
                                        meas.accumulatedDeltaRangeUncertaintyMeters,
                                        meas.carrierFrequencyHz,
                                        meas.carrierCycles,
                                        meas.carrierPhase,
                                        meas.carrierPhaseUncertainty,
                                        meas.multipathIndicator,
                                        meas.snrInDb,
                                        meas.constellationType,
                                        meas.automaticGainControlLevelDb,
                                        meas.carrierFrequencyHz
                                    ).replace("NaN", "")
                                } else {
                                    "Raw,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\r\n".format(
                                        SystemClock.elapsedRealtime(),
                                        event.clock.timeNanos,
                                        event.clock.leapSecond,
                                        event.clock.timeUncertaintyNanos,
                                        event.clock.fullBiasNanos,
                                        event.clock.biasNanos,
                                        event.clock.biasUncertaintyNanos,
                                        event.clock.driftNanosPerSecond,
                                        event.clock.driftUncertaintyNanosPerSecond,
                                        event.clock.hardwareClockDiscontinuityCount,
                                        meas.svid,
                                        meas.timeOffsetNanos,
                                        meas.state,
                                        meas.receivedSvTimeNanos,
                                        meas.receivedSvTimeUncertaintyNanos,
                                        meas.cn0DbHz,
                                        meas.pseudorangeRateMetersPerSecond,
                                        meas.pseudorangeRateUncertaintyMetersPerSecond,
                                        meas.accumulatedDeltaRangeState,
                                        meas.accumulatedDeltaRangeMeters,
                                        meas.accumulatedDeltaRangeUncertaintyMeters,
                                        meas.carrierFrequencyHz,
                                        meas.carrierCycles,
                                        meas.carrierPhase,
                                        meas.carrierPhaseUncertainty,
                                        meas.multipathIndicator,
                                        meas.snrInDb,
                                        meas.constellationType,
                                        "",
                                        meas.carrierFrequencyHz
                                    ).replace("NaN", "")
                                }
                            mFile.writeMeasurementFile(
                                mRecordFileName + "_" + (mTestJob.count),
                                measurementStream
                            )
                        }
                    }
                    override fun onStatusChanged(status: Int) {}
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mService!!.registerGnssMeasurementsCallback(mGnssMeasurementsCallBack)
            }
        }
    }

    companion object {
        private const val TAG = "GNSSTest"

        private const val REQUEST_ID_MULTIPLE_PERMISSIONS = 1

        private const val GPS_DELETE_EPHEMERIS = 0x0001
        private const val GPS_DELETE_ALMANAC = 0x0002
        private const val GPS_DELETE_POSITION = 0x0004
        private const val GPS_DELETE_TIME = 0x0008
        private const val GPS_DELETE_IONO = 0x0010
        private const val GPS_DELETE_UTC = 0x0020
        private const val GPS_DELETE_HEALTH = 0x0040
        private const val GPS_DELETE_SVDIR = 0x0080
        private const val GPS_DELETE_SVSTEER = 0x0100
        private const val GPS_DELETE_SADATA = 0x0200
        private const val GPS_DELETE_RTI = 0x0400
        private const val GPS_DELETE_CELLDB_INFO = 0x8000
        private const val GPS_DELETE_ALL = 0xFFFF
    }
}

