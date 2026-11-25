package com.glass.todo

import android.app.Application
import com.glass.todo.data.Repository
import com.glass.todo.widget.UpdateScheduler

class GlassTodoApp : Application() {

    /** 全 App 共享的数据入口，组件与界面都从这里取 */
    val repository: Repository by lazy { Repository.get(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 每次冷启动都按当前组件实例重新排每日刷新闹钟：
        // 闹钟在应用被强制停止后会失效，重装/更新后也需要重新登记；
        // 如果用户已经移除全部组件，则顺手取消，避免无意义的每日唤醒。
        UpdateScheduler.sync(this)
    }

    companion object {
        @Volatile
        lateinit var instance: GlassTodoApp
            private set

        fun repo(context: android.content.Context): Repository =
            Repository.get(context.applicationContext)
    }
}
