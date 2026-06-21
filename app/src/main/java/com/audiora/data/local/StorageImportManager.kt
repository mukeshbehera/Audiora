package com.audiora.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

data class ImportedFile(
    val uriString: String,
    val name: String,
    val size: Long,
    val type: String,
    val isPermissionValid: Boolean
)

class StorageImportManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("storage_import_prefs", Context.MODE_PRIVATE)

    fun getImportedFiles(): List<ImportedFile> {
        val jsonStr = prefs.getString("imported_files", "[]") ?: "[]"
        val list = mutableListOf<ImportedFile>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uriStr = obj.getString("uri")
                val name = obj.getString("name")
                val size = obj.getLong("size")
                val type = obj.getString("type")
                
                // Verify if permission survives restart
                val hasPerm = hasPersistedPermission(uriStr)
                list.add(ImportedFile(uriStr, name, size, type, hasPerm))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing imported files")
        }
        return list
    }

    fun saveImportedFile(uri: Uri, name: String, size: Long, type: String): Boolean {
        val uriStr = uri.toString()
        
        // Take persistent permission
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            Timber.d("Successfully persisted URI permission for: $uriStr")
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist URI permission for: $uriStr")
            // Even if contentResolver.takePersistableUriPermission fails, we'll still save it.
        }

        val currentList = getImportedFiles().toMutableList()
        // Avoid duplicates
        if (currentList.none { it.uriString == uriStr }) {
            currentList.add(ImportedFile(uriStr, name, size, type, true))
            persistList(currentList)
            return true
        }
        return false
    }

    fun updateImportedFiles(list: List<ImportedFile>) {
        persistList(list)
    }

    fun removeImportedFile(uriStr: String) {
        // Release URL permission if possible
        try {
            val uri = Uri.parse(uriStr)
            val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, releaseFlags)
        } catch (e: Exception) {
            Timber.e(e, "Failed to release permission")
        }
        
        val currentList = getImportedFiles().filter { it.uriString != uriStr }
        persistList(currentList)
    }

    private fun persistList(list: List<ImportedFile>) {
        try {
            val arr = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("uri", item.uriString)
                obj.put("name", item.name)
                obj.put("size", item.size)
                obj.put("type", item.type)
                arr.put(obj)
            }
            prefs.edit().putString("imported_files", arr.toString()).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error persisting imported files")
        }
    }

    private fun hasPersistedPermission(uriStr: String): Boolean {
        try {
            val uri = Uri.parse(uriStr)
            // Check in list of persisted permissions
            val persistedPerms = context.contentResolver.persistedUriPermissions
            for (perm in persistedPerms) {
                if (perm.uri.toString() == uriStr && perm.isReadPermission) {
                    return true
                }
            }
            
            // Fallback: test if we can open input stream
            context.contentResolver.openInputStream(uri)?.use { 
                return true
            }
        } catch (e: Exception) {
            Timber.w("No active permission for URI: $uriStr")
        }
        return false
    }
}
