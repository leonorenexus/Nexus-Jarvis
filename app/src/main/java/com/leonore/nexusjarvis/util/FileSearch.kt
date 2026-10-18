package com.leonore.nexusjarvis.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

/** Pencarian file hanya di dalam folder yang diberikan pengguna lewat Storage Access Framework. */
object FileSearch {
    fun search(context: Context, treeUri: Uri, query: String, max: Int = 10): List<String> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val q = query.lowercase(Locale.ROOT).trim()
        if (q.isEmpty()) return emptyList()
        val results = mutableListOf<String>()
        var visited = 0

        fun walk(dir: DocumentFile, path: String, depth: Int) {
            if (depth > 6) return
            for (f in dir.listFiles()) {
                visited++
                if (results.size >= max || visited > 3000) return
                val n = f.name ?: continue
                if (n.lowercase(Locale.ROOT).contains(q)) results += "$path/$n"
                if (f.isDirectory) walk(f, "$path/$n", depth + 1)
            }
        }
        walk(root, root.name ?: "", 0)
        return results
    }
}
