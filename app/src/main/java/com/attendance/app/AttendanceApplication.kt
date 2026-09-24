package com.attendance.app

import android.app.Application
import com.attendance.app.data.local.AppDatabase
import com.attendance.app.data.repository.AttendanceRepository
import com.attendance.app.location.LocationHelper
import com.attendance.app.ml.FaceDetectorHelper
import com.attendance.app.ml.FaceNetModelHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AttendanceApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        repository = AttendanceRepository(
            staffDao = database.staffDao(),
            attendanceDao = database.attendanceDao(),
            adminUserDao = database.adminUserDao(),
            context = this
        )
        faceNetHelper = FaceNetModelHelper(this)
        faceDetectorHelper = FaceDetectorHelper()
        locationHelper = LocationHelper(this)

        // Seed default demo accounts on first launch
        applicationScope.launch {
            repository.seedDatabaseIfNeeded()
        }
    }

    companion object {
        lateinit var instance: AttendanceApplication
            private set
    }
}
