package com.leonore.nexusjarvis.util

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Locale

data class LaunchableApp(val label: String, val packageName: String, val isGame: Boolean)

data class AppMatch(val best: LaunchableApp?, val candidates: List<LaunchableApp>)

object AppIndex {
    fun list(pm: PackageManager): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
                LaunchableApp(ri.loadLabel(pm).toString(), ai.packageName, ai.category == ApplicationInfo.CATEGORY_GAME)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun normalize(s: String): String = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** Pencocokan nama: exact > diawali > mengandung. Jika ambigu, best = null dan kandidat dikembalikan. */
    fun match(apps: List<LaunchableApp>, query: String): AppMatch {
        val q = normalize(query)
        if (q.isEmpty()) return AppMatch(null, emptyList())
        val tiers = listOf(
            apps.filter { normalize(it.label) == q },
            apps.filter { normalize(it.label).startsWith(q) },
            apps.filter { normalize(it.label).contains(q) }
        )
        val hit = tiers.firstOrNull { it.isNotEmpty() } ?: return AppMatch(null, emptyList())
        return if (hit.size == 1) AppMatch(hit[0], hit) else AppMatch(null, hit.take(5))
    }

    fun launchIntent(pm: PackageManager, pkg: String): Intent? = pm.getLaunchIntentForPackage(pkg)
}
