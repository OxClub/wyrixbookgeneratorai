package com.wyrix.bookgenerator

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String, val lang: String, val pages: Int,
    val title: String = "", val bible: String = "", val summary: String = "", val status: String = "NEW"
)

@Entity
data class Section(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long, val idx: Int, val chIdx: Int, val chTitle: String, val chSummary: String, val part: Int,
    val text: String = "", val text2: String = "", val status: String = "PENDING"
)

@Dao
interface BookDao {
    @Insert suspend fun addBook(b: Book): Long
    @Update suspend fun saveBook(b: Book)
    @Query("SELECT * FROM Book ORDER BY id DESC") fun books(): Flow<List<Book>>
    @Query("SELECT * FROM Book WHERE id=:id") suspend fun book(id: Long): Book?
    @Insert suspend fun addSections(s: List<Section>)
    @Update suspend fun saveSection(s: Section)
    @Query("SELECT * FROM Section WHERE bookId=:id AND status!='DONE' ORDER BY idx LIMIT 1") suspend fun next(id: Long): Section?
    @Query("SELECT * FROM Section WHERE bookId=:id AND idx=:idx") suspend fun at(id: Long, idx: Int): Section?
    @Query("SELECT * FROM Section WHERE bookId=:id ORDER BY idx") suspend fun sections(id: Long): List<Section>
    @Query("SELECT * FROM Section WHERE bookId=:id AND chIdx=:ch ORDER BY idx") suspend fun chapter(id: Long, ch: Int): List<Section>
    @Query("SELECT COUNT(*) FROM Section WHERE bookId=:id AND status='DONE'") fun done(id: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM Section WHERE bookId=:id") fun total(id: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM Section WHERE bookId=:id") suspend fun totalNow(id: Long): Int
}

@Database(entities = [Book::class, Section::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): BookDao
    companion object {
        @Volatile private var i: Db? = null
        fun get(c: Context): Db = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "bookgen.db").build().also { i = it }
        }
    }
}
