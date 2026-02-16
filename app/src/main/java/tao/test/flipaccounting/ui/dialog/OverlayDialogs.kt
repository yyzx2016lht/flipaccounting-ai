package tao.test.flipaccounting.ui.dialog

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import tao.test.flipaccounting.CategoryNode
import tao.test.flipaccounting.Prefs
import tao.test.flipaccounting.R
import java.util.*

object OverlayDialogs {

    fun showAnchoredMenu(ctx: Context, anchor: View, items: List<String>, onSelected: (String) -> Unit) {
        val popup = ListPopupWindow(ctx).apply {
            setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, items))
            anchorView = anchor
            width = anchor.width
            isModal = true
            setOnItemClickListener { _, _, pos, _ ->
                onSelected(items[pos])
                dismiss()
            }
        }
        popup.show()
    }

    fun showGridCategoryPicker(ctx: Context, currentSelectionText: String, type: Int, onConfirm: (String) -> Unit) {
        val themeContext = ContextThemeWrapper(ctx, R.style.Theme_FlipAccounting)
        val view = LayoutInflater.from(themeContext).inflate(R.layout.dialog_category_picker, null)

        val dialog = AlertDialog.Builder(themeContext).setView(view).create()
        val container = view.findViewById<LinearLayout>(R.id.container_categories)
        // Get categories based on type
        val categories = Prefs.getCategories(ctx, type)
        var currentSelection = currentSelectionText.replace(" > ", "/::/")

        fun render() {
            container.removeAllViews()
            val parts = currentSelection.split("/::/")
            val parent = categories.find { it.name == parts.getOrNull(0) }

            categories.chunked(5).forEach { row ->
                val rowLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                row.forEach { cat ->
                    val itemView = LayoutInflater.from(ctx).inflate(R.layout.item_category_grid, rowLayout, false)
                    itemView.findViewById<TextView>(R.id.tv_category_name).apply {
                        text = cat.name
                        if (cat.name == parent?.name) setTextColor(Color.parseColor("#2196F3"))
                    }
                    Glide.with(ctx).load(cat.icon).transform(CircleCrop()).into(itemView.findViewById(R.id.iv_category_icon))
                    itemView.setOnClickListener { currentSelection = cat.name; render() }
                    rowLayout.addView(itemView, LinearLayout.LayoutParams(0, -2, 1f))
                }
                container.addView(rowLayout)
                if (parent != null && row.any { it.name == parent.name } && parent.subs.isNotEmpty()) {
                    container.addView(createSubPanel(ctx, parent, parts.getOrNull(1)) {
                        currentSelection = "${parent.name}/::/${it.name}"
                        render()
                    })
                }
            }
        }
        render()

        view.findViewById<Button>(R.id.btn_confirm_category).setOnClickListener {
            onConfirm(currentSelection.replace("/::/", " > "))
            dialog.dismiss()
        }

        dialog.window?.let {
            it.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE)
            it.setGravity(Gravity.BOTTOM)
        }
        dialog.show()
    }

    private fun createSubPanel(ctx: Context, parent: CategoryNode, selected: String?, onClick: (CategoryNode) -> Unit): View {
        return GridLayout(ctx).apply {
            columnCount = 5
            setPadding(20, 20, 20, 20)
            setBackgroundResource(R.drawable.bg_search_box)
            parent.subs.forEach { sub ->
                val item = LayoutInflater.from(ctx).inflate(R.layout.item_category_grid, this, false)
                item.findViewById<TextView>(R.id.tv_category_name).text = sub.name
                if (sub.name == selected) {
                    item.findViewById<ImageView>(R.id.iv_category_icon).setColorFilter(Color.parseColor("#2196F3"))
                }
                Glide.with(ctx).load(sub.icon).transform(CircleCrop()).into(item.findViewById(R.id.iv_category_icon))
                item.setOnClickListener { onClick(sub) }
                addView(item, GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply { width = 0 })
            }
        }
    }

    fun showCustomTimePicker(ctx: Context, onConfirm: (String) -> Unit) {
        val themeContext = ContextThemeWrapper(ctx, R.style.Theme_FlipAccounting)
        val view = LayoutInflater.from(themeContext).inflate(R.layout.layout_custom_time_picker, null)
        val dialog = AlertDialog.Builder(themeContext).setView(view).create()

        val cal = Calendar.getInstance()
        val npYear = view.findViewById<NumberPicker>(R.id.np_year).apply { minValue = 2024; maxValue = 2030; value = cal.get(Calendar.YEAR) }
        val npMonth = view.findViewById<NumberPicker>(R.id.np_month).apply { minValue = 1; maxValue = 12; value = cal.get(Calendar.MONTH) + 1 }
        val npDay = view.findViewById<NumberPicker>(R.id.np_day).apply { minValue = 1; maxValue = 31; value = cal.get(Calendar.DAY_OF_MONTH) }
        val npHour = view.findViewById<NumberPicker>(R.id.np_hour).apply { minValue = 0; maxValue = 23; value = cal.get(Calendar.HOUR_OF_DAY) }
        val npMin = view.findViewById<NumberPicker>(R.id.np_minute).apply { minValue = 0; maxValue = 59; value = cal.get(Calendar.MINUTE) }

        view.findViewById<View>(R.id.btn_confirm_time).setOnClickListener {
            val timeStr = String.format(Locale.getDefault(), "%d-%02d-%02d %02d:%02d:00", npYear.value, npMonth.value, npDay.value, npHour.value, npMin.value)
            onConfirm(timeStr)
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_cancel_time)?.setOnClickListener {
            dialog.dismiss() // 直接关闭对话框，不触发 onConfirm 回调
        }
        dialog.window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE)
        dialog.show()
    }
}
