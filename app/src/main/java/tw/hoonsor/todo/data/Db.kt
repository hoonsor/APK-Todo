package tw.hoonsor.todo.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/*
 * 本機快取。欄位對應 Supabase 的 todo_* 表（docs/DATA_MODEL.md），時間一律存 epoch 毫秒；
 * serverUpdatedAt 是伺服器的 updated_at 原字串，只用來推進同步游標。
 */

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int = 0,
    val icon: String? = null,
    val isDefault: Boolean = false,
    val sortKey: String,
    val createdAt: Long,
    val deletedAt: Long? = null,
    val serverUpdatedAt: String? = null,
)

@Entity(tableName = "tasks", indices = [Index("listId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val title: String,
    val note: String = "",
    val important: Boolean = false,
    val completedAt: Long? = null,
    /** yyyy-MM-dd */
    val dueDate: String? = null,
    val remindAt: Long? = null,
    /** JSON，見 Repeat.toJson() */
    val repeat: String? = null,
    val repeatRoot: String? = null,
    val repeatSeq: Int? = null,
    /** yyyy-MM-dd；等於今天＝在我的一天 */
    val myDayDate: String? = null,
    val sortKey: String,
    val source: String = "app",
    val createdAt: Long,
    val deletedAt: Long? = null,
    val serverUpdatedAt: String? = null,
) {
    val done get() = completedAt != null
    val due: LocalDate? get() = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun inMyDay(today: LocalDate) = myDayDate == today.toString()
}

@Entity(tableName = "steps", indices = [Index("taskId")])
data class StepEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val done: Boolean = false,
    val sortKey: String,
    val createdAt: Long,
    val deletedAt: Long? = null,
    val serverUpdatedAt: String? = null,
)

/** 還沒送到伺服器的修改；依 seq 順序送出 */
@Entity(tableName = "outbox", indices = [Index("rowId")])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    /** todo_lists／todo_tasks／todo_steps */
    val tableName: String,
    val rowId: String,
    /** insert（整筆）或 update（只有改的欄位） */
    val op: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Dao
interface TodoDao {
    // ---- 清單 ----
    @Query("SELECT * FROM lists WHERE deletedAt IS NULL ORDER BY isDefault DESC, sortKey, createdAt")
    fun listsFlow(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists WHERE deletedAt IS NULL ORDER BY isDefault DESC, sortKey, createdAt")
    suspend fun lists(): List<ListEntity>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun list(id: String): ListEntity?

    @Upsert suspend fun upsertList(e: ListEntity)

    // ---- 待辦 ----
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    fun tasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    suspend fun tasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun taskFlow(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun tasksByIds(ids: List<String>): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE repeatRoot = :root AND repeatSeq = :seq LIMIT 1")
    suspend fun repeatInstance(root: String, seq: Int): TaskEntity?

    @Upsert suspend fun upsertTask(e: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun hardDeleteTask(id: String)

    // ---- 步驟 ----
    @Query("SELECT * FROM steps WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY sortKey, createdAt")
    fun stepsFlow(taskId: String): Flow<List<StepEntity>>

    @Query("SELECT * FROM steps WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY sortKey, createdAt")
    suspend fun steps(taskId: String): List<StepEntity>

    @Query("SELECT * FROM steps WHERE deletedAt IS NULL")
    fun allStepsFlow(): Flow<List<StepEntity>>

    @Query("SELECT * FROM steps WHERE id = :id")
    suspend fun step(id: String): StepEntity?

    @Upsert suspend fun upsertStep(e: StepEntity)

    @Query("DELETE FROM steps WHERE taskId = :taskId")
    suspend fun hardDeleteSteps(taskId: String)

    // ---- outbox ----
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(e: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY seq LIMIT :limit")
    suspend fun pending(limit: Int = 200): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    fun pendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE rowId = :rowId")
    suspend fun pendingFor(rowId: String): Int

    @Query("DELETE FROM outbox WHERE seq = :seq")
    suspend fun done(seq: Long)

    @Query("DELETE FROM outbox WHERE rowId = :rowId")
    suspend fun dropFor(rowId: String)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE seq = :seq")
    suspend fun failed(seq: Long, error: String)

    @Query("DELETE FROM lists")
    suspend fun clearLists()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM steps")
    suspend fun clearSteps()

    @Query("DELETE FROM outbox")
    suspend fun clearOutbox()
}

@Database(entities = [ListEntity::class, TaskEntity::class, StepEntity::class, OutboxEntity::class], version = 1, exportSchema = true)
abstract class TodoDb : RoomDatabase() {
    abstract fun dao(): TodoDao

    companion object {
        fun create(context: Context): TodoDb =
            Room.databaseBuilder(context, TodoDb::class.java, "todo.db").build()
    }
}
