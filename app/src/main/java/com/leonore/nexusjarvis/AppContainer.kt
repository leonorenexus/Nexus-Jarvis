package com.leonore.nexusjarvis

import android.app.Application
import androidx.room.Room
import com.leonore.nexusjarvis.ai.core.ContextManager
import com.leonore.nexusjarvis.ai.core.ActionExecutor
import com.leonore.nexusjarvis.ai.core.JarvisEngine
import com.leonore.nexusjarvis.ai.core.ToolCall
import com.leonore.nexusjarvis.ai.planning.ActionValidator
import com.leonore.nexusjarvis.ai.planning.TaskResultVerifier
import com.leonore.nexusjarvis.ai.provider.ProviderFactory
import com.leonore.nexusjarvis.ai.tools.AppLauncherTool
import com.leonore.nexusjarvis.ai.tools.AutomationTool
import com.leonore.nexusjarvis.ai.tools.DeviceInfoTool
import com.leonore.nexusjarvis.ai.tools.FileSearchTool
import com.leonore.nexusjarvis.ai.tools.GameLauncherTool
import com.leonore.nexusjarvis.ai.tools.MessageTool
import com.leonore.nexusjarvis.ai.tools.ReadNotificationsTool
import com.leonore.nexusjarvis.ai.tools.SettingsTool
import com.leonore.nexusjarvis.ai.tools.ShowNotificationTool
import com.leonore.nexusjarvis.ai.tools.ToolRegistry
import com.leonore.nexusjarvis.ai.tools.UrlTool
import com.leonore.nexusjarvis.automation.RoutineEngine
import com.leonore.nexusjarvis.automation.RoutineRepository
import com.leonore.nexusjarvis.data.JarvisMemoryRepository
import com.leonore.nexusjarvis.data.SecretStore
import com.leonore.nexusjarvis.data.SettingsRepository
import com.leonore.nexusjarvis.data.db.NexusDatabase
import com.leonore.nexusjarvis.gamebooster.GameProfileManager
import com.leonore.nexusjarvis.gamebooster.GameRepository
import com.leonore.nexusjarvis.security.AuditLogger
import com.leonore.nexusjarvis.security.PrivilegeManager
import com.leonore.nexusjarvis.voice.SpeechOutputManager
import kotlinx.coroutines.flow.MutableStateFlow

/** Tindakan sensitif yang menunggu konfirmasi pengguna (mis. dari perintah suara). */
class PendingActions {
    val items = MutableStateFlow<List<ToolCall>>(emptyList())
    fun add(call: ToolCall) { items.value = items.value + call }
    fun remove(call: ToolCall) { items.value = items.value - call }
}

class AppContainer(val app: Application) {
    val db: NexusDatabase by lazy { Room.databaseBuilder(app, NexusDatabase::class.java, "nexus.db").build() }
    val settings = SettingsRepository(app)
    val secrets = SecretStore(app)
    val audit by lazy { AuditLogger(db.auditDao()) }
    val memory by lazy { JarvisMemoryRepository(db.chatDao(), settings) }
    val routines by lazy { RoutineRepository(app, db.taskDao()) }
    val routineEngine by lazy { RoutineEngine(this) }
    val games by lazy { GameRepository(db.gameDao(), db.sessionDao()) }
    val privilege by lazy { PrivilegeManager(app, audit) }
    val profiles by lazy { GameProfileManager(app, privilege, settings) }

    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry().apply {
            register(DeviceInfoTool())
            register(AppLauncherTool())
            register(SettingsTool())
            register(UrlTool())
            register(AutomationTool())
            register(GameLauncherTool())
            register(ShowNotificationTool())
            register(ReadNotificationsTool())
            register(FileSearchTool())
            register(MessageTool())
        }
    }
    val validator by lazy { ActionValidator(toolRegistry) }
    val executor by lazy { ActionExecutor(this, TaskResultVerifier()) }
    val providers by lazy { ProviderFactory(settings, secrets) }
    val contextManager by lazy { ContextManager(this) }
    val engine by lazy { JarvisEngine(this) }
    val speechOut by lazy { SpeechOutputManager(app, settings) }

    val pendingActions = PendingActions()
    val chatPrefill = MutableStateFlow<String?>(null)
}
