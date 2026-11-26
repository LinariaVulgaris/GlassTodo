package com.glass.todo.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.glass.todo.R
import com.glass.todo.databinding.ActivityMainBinding
import com.glass.todo.ui.settings.SettingsActivity
import com.glass.todo.ui.todo.TodoEditSheet
import com.glass.todo.ui.todo.TodoListFragment
import com.glass.todo.widget.WidgetIntent

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** App 保持前台跨日时，Room Flow 不会仅因日期变化而重新发数据，需要主动重绘文案。 */
    private val dateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshVisibleDates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // targetSdk 35+ 强制边到边；显式给系统栏与刘海留出空间，避免工具栏被遮挡。
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_todo -> {
                    switchTo(TAG_TODO) { TodoListFragment() }
                    true
                }

                else -> false
            }
        }

        if (savedInstanceState == null) {
            switchTo(TAG_TODO) { TodoListFragment() }
        }
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // 这些是系统受保护广播，用 NOT_EXPORTED 明确表示不接收其他 App 伪造的广播。
        ContextCompat.registerReceiver(
            this,
            dateChangeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // 从后台回来时也重算一次，覆盖「跨日发生在 Activity 不可见期间」的情况。
        refreshVisibleDates()
    }

    override fun onStop() {
        unregisterReceiver(dateChangeReceiver)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** 组件上的「+」按钮会带着这个 action 跳进来，需要自动弹出新建待办 */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != WidgetIntent.ACTION_OPEN_ADD) return
        if (supportFragmentManager.findFragmentByTag(TAG_EDIT_SHEET) != null) return
        binding.bottomNav.selectedItemId = R.id.nav_todo
        TodoEditSheet.newInstance().show(supportFragmentManager, TAG_EDIT_SHEET)
        // 消费掉，避免旋转屏幕时重复弹窗
        intent.action = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** 用 show/hide 而不是 replace，这样切 tab 时列表滚动位置和编辑状态不会丢 */
    private fun switchTo(tag: String, create: () -> Fragment) {
        val fm = supportFragmentManager
        val target = fm.findFragmentByTag(tag) ?: create()
        val tx = fm.beginTransaction()
        fm.fragments.forEach { f ->
            if (f !== target && f.isAdded) tx.hide(f)
        }
        if (target.isAdded) tx.show(target) else tx.add(R.id.fragment_container, target, tag)
        tx.setReorderingAllowed(true).commit()
    }

    private fun refreshVisibleDates() {
        (supportFragmentManager.findFragmentByTag(TAG_TODO) as? TodoListFragment)?.refreshDates()
    }

    private companion object {
        const val TAG_TODO = "tab_todo"
        const val TAG_EDIT_SHEET = "todo_edit_sheet"
    }
}
