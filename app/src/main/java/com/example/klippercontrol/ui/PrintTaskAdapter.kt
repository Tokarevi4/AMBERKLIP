package com.example.klippercontrol.ui

import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.example.klippercontrol.R
import com.example.klippercontrol.model.PrintTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrintTaskAdapter(
    private val onTaskClick: (PrintTask) -> Unit,
    private val onSortClick: (SortField) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class SortField {
        NAME,
        PRINT_DATE,
        MODIFIED_DATE,
        FILE_SIZE
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_TASK = 1

        private const val DIVIDER_WIDTH_DP = 1

        /*
         * Небольшой зазор между содержимым строки
         * и внешней границей карточки.
         *
         * Благодаря этому текст и разделители
         * физически не попадают в область скругления.
         */
        private const val CARD_INSET_DP = 1
    }

    private val tasks =
        mutableListOf<PrintTask>()

    private var sortField =
        SortField.MODIFIED_DATE

    private var sortAscending =
        false

    fun submitList(
        newTasks: List<PrintTask>
    ) {
        tasks.clear()
        tasks.addAll(newTasks)

        notifyDataSetChanged()
    }

    fun refreshHeaderLanguage() {
        notifyItemChanged(0)
    }

    fun setSortState(
        field: SortField,
        ascending: Boolean
    ) {
        sortField = field
        sortAscending = ascending

        notifyItemChanged(0)
    }

    override fun getItemViewType(
        position: Int
    ): Int {

        return if (position == 0) {
            VIEW_TYPE_HEADER
        } else {
            VIEW_TYPE_TASK
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {

        return if (
            viewType == VIEW_TYPE_HEADER
        ) {
            createHeaderViewHolder(parent)
        } else {
            createTaskViewHolder(parent)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {

        if (holder is HeaderViewHolder) {
            bindHeader(holder)
            return
        }

        if (holder is TaskViewHolder) {

            val task =
                tasks[position - 1]

            bindTask(
                holder,
                task,
                position == itemCount - 1
            )
        }
    }

    override fun getItemCount(): Int =
        tasks.size + 1

    private fun createHeaderViewHolder(
        parent: ViewGroup
    ): HeaderViewHolder {

        val metrics =
            parent.context
                .resources
                .displayMetrics

        val density =
            metrics.density

        val screenDp =
            metrics.widthPixels / density

        val geometry =
            calculateGeometry(screenDp)

        val row =
            LinearLayout(parent.context)

        val rowParams =
            RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )

        val inset =
            dpToPx(
                parent.context,
                CARD_INSET_DP
            )

        rowParams.leftMargin = inset
        rowParams.rightMargin = inset

        row.layoutParams =
            rowParams

        row.orientation =
            LinearLayout.HORIZONTAL

        row.gravity =
            Gravity.CENTER_VERTICAL

        /*
         * Теперь используется реальный drawable header.
         * Раньше здесь был setBackgroundColor(), из-за чего
         * bg_tasks_header.xml вообще не работал.
         */
        row.setBackgroundResource(
            R.drawable.bg_tasks_header
        )

        row.setPadding(
            geometry.horizontalPadding +
                dpToPx(parent.context, 2),
            6,
            geometry.horizontalPadding +
                dpToPx(parent.context, 2),
            6
        )

        val name =
            addHeaderCell(
                row,
                parent.context.getString(
                    R.string.task_name
                ),
                geometry.nameWeight,
                geometry.nameWidth,
                geometry.headerTextSize,
                Gravity.START
            )

        addVerticalDivider(row)

        val printDate =
            addHeaderCell(
                row,
                parent.context.getString(
                    R.string.task_print_date
                ),
                geometry.dateWeight,
                0,
                geometry.headerTextSize,
                Gravity.CENTER
            )

        addVerticalDivider(row)

        val modifiedDate =
            addHeaderCell(
                row,
                parent.context.getString(
                    R.string.task_modified_date
                ),
                geometry.dateWeight,
                0,
                geometry.headerTextSize,
                Gravity.CENTER
            )

        addVerticalDivider(row)

        val fileSize =
            addHeaderCell(
                row,
                parent.context.getString(
                    R.string.task_file_size
                ),
                geometry.sizeWeight,
                0,
                geometry.headerTextSize,
                Gravity.CENTER
            )

        return HeaderViewHolder(
            row,
            name,
            printDate,
            modifiedDate,
            fileSize
        )
    }

    private fun bindHeader(
        holder: HeaderViewHolder
    ) {

        val context =
            holder.itemView.context

        holder.name.text =
            headerText(
                context.getString(
                    R.string.task_name
                ),
                SortField.NAME
            )

        holder.printDate.text =
            headerText(
                context.getString(
                    R.string.task_print_date
                ),
                SortField.PRINT_DATE
            )

        holder.modifiedDate.text =
            headerText(
                context.getString(
                    R.string.task_modified_date
                ),
                SortField.MODIFIED_DATE
            )

        holder.fileSize.text =
            headerText(
                context.getString(
                    R.string.task_file_size
                ),
                SortField.FILE_SIZE
            )

        holder.name.setOnClickListener {
            onSortClick(
                SortField.NAME
            )
        }

        holder.printDate.setOnClickListener {
            onSortClick(
                SortField.PRINT_DATE
            )
        }

        holder.modifiedDate.setOnClickListener {
            onSortClick(
                SortField.MODIFIED_DATE
            )
        }

        holder.fileSize.setOnClickListener {
            onSortClick(
                SortField.FILE_SIZE
            )
        }
    }

    private fun headerText(
        title: String,
        field: SortField
    ): String {

        if (field != sortField) {
            return title
        }

        return if (sortAscending) {
            "$title ↑"
        } else {
            "$title ↓"
        }
    }

    private fun createTaskViewHolder(
        parent: ViewGroup
    ): TaskViewHolder {

        val metrics =
            parent.context
                .resources
                .displayMetrics

        val density =
            metrics.density

        val screenDp =
            metrics.widthPixels / density

        val geometry =
            calculateGeometry(screenDp)

        val row =
            LinearLayout(parent.context)

        val rowParams =
            RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )

        val inset =
            dpToPx(
                parent.context,
                CARD_INSET_DP
            )

        rowParams.leftMargin = inset
        rowParams.rightMargin = inset

        row.layoutParams =
            rowParams

        row.orientation =
            LinearLayout.VERTICAL

        val content =
            LinearLayout(parent.context)

        content.orientation =
            LinearLayout.HORIZONTAL

        content.gravity =
            Gravity.CENTER_VERTICAL

        content.setPadding(
            geometry.horizontalPadding,
            7,
            geometry.horizontalPadding,
            7
        )

        val name =
            addTaskCell(
                content,
                geometry.nameWeight,
                geometry.nameWidth,
                geometry.nameTextSize,
                Gravity.START,
                true
            )

        addVerticalDivider(content)

        val printDate =
            addTaskCell(
                content,
                geometry.dateWeight,
                0,
                geometry.dateTextSize,
                Gravity.CENTER,
                false
            )

        addVerticalDivider(content)

        val modifiedDate =
            addTaskCell(
                content,
                geometry.dateWeight,
                0,
                geometry.dateTextSize,
                Gravity.CENTER,
                false
            )

        addVerticalDivider(content)

        val fileSize =
            addTaskCell(
                content,
                geometry.sizeWeight,
                0,
                geometry.sizeTextSize,
                Gravity.CENTER,
                false
            )

        row.addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        /*
         * Горизонтальный разделитель находится уже
         * внутри inset строки, поэтому он не касается
         * внешних скруглённых углов карточки.
         */
        val divider =
            View(parent.context)

        divider.setBackgroundColor(
            ContextCompat.getColor(parent.context, R.color.border)
        )

        row.addView(
            divider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(
                    parent.context,
                    DIVIDER_WIDTH_DP
                )
            )
        )

        return TaskViewHolder(
            row,
            name,
            printDate,
            modifiedDate,
            fileSize,
            divider
        )
    }

    private fun bindTask(
        holder: TaskViewHolder,
        task: PrintTask,
        isLast: Boolean
    ) {

        holder.name.text =
            task.name

        holder.printDate.text =
            formatDate(
                task.printDate
            )

        holder.modifiedDate.text =
            formatDate(
                task.modifiedDate
            )

        holder.fileSize.text =
            formatSize(
                task.fileSize
            )

        /*
         * Последняя строка получает нижние скругления.
         */
        if (isLast) {

            holder.itemView.setBackgroundResource(
                R.drawable.bg_tasks_last_row
            )

            /*
             * У последней строки нет горизонтального
             * разделителя, иначе он пересекал бы
             * нижние скругления.
             */
            holder.divider.visibility =
                View.GONE

        } else {

            holder.itemView.background = null

            holder.divider.visibility =
                View.VISIBLE
        }

        holder.itemView.setOnClickListener {
            onTaskClick(task)
        }
    }

    private fun addHeaderCell(
        parent: LinearLayout,
        text: String,
        weight: Float,
        widthDp: Int,
        textSize: Float,
        gravity: Int
    ): TextView {

        val view =
            TextView(parent.context)

        view.text =
            text

        view.textSize =
            textSize

        view.setTypeface(
            null,
            Typeface.BOLD
        )

        view.setTextColor(
            ContextCompat.getColor(parent.context, R.color.text_primary)
        )

        view.gravity =
            gravity or Gravity.CENTER_VERTICAL

        view.setPadding(
            3,
            3,
            3,
            3
        )

        view.setSingleLine(false)

        view.maxLines = 2

        view.ellipsize =
            TextUtils.TruncateAt.END

        view.isClickable = true
        view.isFocusable = true

        val params =
            if (widthDp > 0) {

                LinearLayout.LayoutParams(
                    dpToPx(
                        parent.context,
                        widthDp
                    ),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

            } else {

                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    weight
                )
            }

        parent.addView(
            view,
            params
        )

        return view
    }

    private fun addVerticalDivider(
        parent: LinearLayout
    ) {

        val divider =
            View(
                parent.context
            )

        divider.setBackgroundColor(
            ContextCompat.getColor(parent.context, R.color.border)
        )

        parent.addView(
            divider,
            LinearLayout.LayoutParams(
                dpToPx(
                    parent.context,
                    DIVIDER_WIDTH_DP
                ),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun addTaskCell(
        parent: LinearLayout,
        weight: Float,
        widthDp: Int,
        textSize: Float,
        gravity: Int,
        allowTwoLines: Boolean
    ): TextView {

        val view =
            TextView(parent.context)

        view.textSize =
            textSize

        view.setTextColor(
            ContextCompat.getColor(parent.context, R.color.text_secondary)
        )

        view.gravity =
            gravity or Gravity.CENTER_VERTICAL

        view.setPadding(
            3,
            2,
            3,
            2
        )

        if (allowTwoLines) {

            view.maxLines = 2

            view.ellipsize =
                TextUtils.TruncateAt.END

        } else {

            view.setSingleLine(true)

            view.ellipsize =
                TextUtils.TruncateAt.END
        }

        val params =
            if (widthDp > 0) {

                LinearLayout.LayoutParams(
                    dpToPx(
                        parent.context,
                        widthDp
                    ),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

            } else {

                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    weight
                )
            }

        parent.addView(
            view,
            params
        )

        return view
    }

    private fun calculateGeometry(
        screenDp: Float
    ): TableGeometry {

        /*
         * Таблица рассчитана под узкие Android-экраны.
         *
         * Чем меньше экран, тем меньше шрифт
         * и тем больше места получает имя файла.
         */

        return when {

            screenDp <= 340f -> {

                TableGeometry(
                    nameWeight = 2.20f,
                    dateWeight = 1.05f,
                    sizeWeight = 0.75f,

                    nameWidth = 0,

                    nameTextSize = 12f,
                    dateTextSize = 9.5f,
                    sizeTextSize = 9.5f,
                    headerTextSize = 10f,

                    horizontalPadding = 2
                )
            }

            screenDp <= 380f -> {

                TableGeometry(
                    nameWeight = 2.30f,
                    dateWeight = 1.10f,
                    sizeWeight = 0.80f,

                    nameWidth = 0,

                    nameTextSize = 12.5f,
                    dateTextSize = 10f,
                    sizeTextSize = 10f,
                    headerTextSize = 10.5f,

                    horizontalPadding = 3
                )
            }

            screenDp <= 430f -> {

                TableGeometry(
                    nameWeight = 2.40f,
                    dateWeight = 1.15f,
                    sizeWeight = 0.85f,

                    nameWidth = 0,

                    nameTextSize = 13f,
                    dateTextSize = 10.5f,
                    sizeTextSize = 10.5f,
                    headerTextSize = 11f,

                    horizontalPadding = 4
                )
            }

            else -> {

                TableGeometry(
                    nameWeight = 2.50f,
                    dateWeight = 1.20f,
                    sizeWeight = 0.90f,

                    nameWidth = 0,

                    nameTextSize = 13f,
                    dateTextSize = 11f,
                    sizeTextSize = 11f,
                    headerTextSize = 12f,

                    horizontalPadding = 4
                )
            }
        }
    }

    private fun formatDate(
        timestamp: Long?
    ): String {

        if (timestamp == null) {
            return "---"
        }

        val formatter =
            SimpleDateFormat(
                "dd.MM.yy HH:mm",
                Locale.getDefault()
            )

        return formatter.format(
            Date(timestamp)
        )
    }

    private fun formatSize(
        size: Long
    ): String {

        if (size < 1024L) {
            return "$size B"
        }

        if (
            size <
            1024L * 1024L
        ) {
            return String.format(
                Locale.getDefault(),
                "%.1f KB",
                size / 1024.0
            )
        }

        if (
            size <
            1024L *
            1024L *
            1024L
        ) {
            return String.format(
                Locale.getDefault(),
                "%.1f MB",
                size /
                    (
                        1024.0 *
                        1024.0
                    )
            )
        }

        return String.format(
            Locale.getDefault(),
            "%.1f GB",
            size /
                (
                    1024.0 *
                    1024.0 *
                    1024.0
                )
            )
    }

    private fun dpToPx(
        context: android.content.Context,
        dp: Int
    ): Int {

        return (
            dp *
                context.resources.displayMetrics.density
            ).toInt()
    }

    private data class TableGeometry(
        val nameWeight: Float,
        val dateWeight: Float,
        val sizeWeight: Float,

        val nameWidth: Int,

        val nameTextSize: Float,
        val dateTextSize: Float,
        val sizeTextSize: Float,
        val headerTextSize: Float,

        val horizontalPadding: Int
    )

    class HeaderViewHolder(
        itemView: View,
        val name: TextView,
        val printDate: TextView,
        val modifiedDate: TextView,
        val fileSize: TextView
    ) : RecyclerView.ViewHolder(itemView)

    class TaskViewHolder(
        itemView: View,
        val name: TextView,
        val printDate: TextView,
        val modifiedDate: TextView,
        val fileSize: TextView,
        val divider: View
    ) : RecyclerView.ViewHolder(itemView)
}
