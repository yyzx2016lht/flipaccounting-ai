package tao.test.flipaccounting

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object BackupManager {
    const val REQUEST_CODE_EXPORT = 1001
    const val REQUEST_CODE_IMPORT = 1002

    fun startExport(activity: Activity) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "FlipAccounting_Backup.json")
        }
        activity.startActivityForResult(intent, REQUEST_CODE_EXPORT)
    }

    fun handleExportResult(context: Context, uri: Uri) {
        try {
            val root = JSONObject().apply {
                put("assets_v1", Prefs.serializeAssetList(Prefs.getAssets(context)))
                put("cat_expense_v1", Prefs.serializeCategoryList(Prefs.getCategories(context, Prefs.TYPE_EXPENSE)))
                put("cat_income_v1", Prefs.serializeCategoryList(Prefs.getCategories(context, Prefs.TYPE_INCOME)))
                
                // 导出账单历史
                val billsArr = org.json.JSONArray()
                Prefs.getBills(context).forEach { bill ->
                    val obj = JSONObject()
                    obj.put("amount", bill.amount)
                    obj.put("type", bill.type)
                    obj.put("assetName", bill.assetName)
                    obj.put("categoryName", bill.categoryName)
                    obj.put("time", bill.time)
                    obj.put("remarks", bill.remarks)
                    obj.put("iconUrl", bill.iconUrl)
                    obj.put("recordTime", bill.recordTime)
                    billsArr.put(obj)
                }
                put("bills_v1", billsArr)

                val wl = org.json.JSONArray()
                Prefs.getAppWhiteList(context).forEach { wl.put(it) }
                put("app_white_list", wl)
            }
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(root.toString(4).toByteArray())
            }
            Utils.toast(context, "备份已导出为文件")
        } catch (e: Exception) {
            Utils.toast(context, "导出失败")
        }
    }

    fun startImport(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }

    fun handleImportResult(context: Context, uri: Uri) {
        try {
            val sb = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine { sb.append(it) }
            }
            Prefs.importRawData(context, JSONObject(sb.toString()))
            Utils.toast(context, "恢复成功，请重启 App")
        } catch (e: Exception) {
            Utils.toast(context, "无效的备份文件")
        }
    }
}