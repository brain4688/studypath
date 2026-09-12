package com.studypath.app

import android.app.Application
import com.studypath.app.data.api.AiClient
import com.studypath.app.data.db.AppDatabase
import com.studypath.app.data.repo.PlanRepository

/** 轻量手动依赖容器 */
class AppContainer(app: Application) {
    val database: AppDatabase = AppDatabase.get(app)
    val aiClient = AiClient()
    val repository = PlanRepository(
        planDao = database.planDao(),
        phaseDao = database.phaseDao(),
        taskDao = database.taskDao(),
        configDao = database.apiConfigDao(),
    )
}

class StudyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
