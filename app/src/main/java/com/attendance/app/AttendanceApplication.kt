package com.attendance.app

import android.app.Application
import com.attendance.app.data.local.AppDatabase
import com.attendance.app.data.repository.AttendanceRepository
import com.attendance.app.location.LocationHelper
import com.attendance.app.ml.FaceDetectorHelper
import com.attendance.app.ml.FaceNetModelHelper

class AttendanceApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: AttendanceRepository
        private set

    lateinit var faceNetHelper: FaceNetModelHelper
        private set

    lateinit var faceDetectorHelper: FaceDetectorHelper
        private set

    lateinit var locationHelper: LocationHelper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        repository = AttendanceRepository(database.staffDao(), database.attendanceDao(), this)
        faceNetHelper = FaceNetModelHelper(this)
        faceDetectorHelper = FaceDetectorHelper()
        locationHelper = LocationHelper(this)
    }

    companion object {
        lateinit var instance: AttendanceApplication
            private set
    }
}
