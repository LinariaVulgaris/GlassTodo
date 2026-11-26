package com.glass.todo.widget.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 液态玻璃源截图和生成位图的本地存储。
 *
 * 只在用户确认后把 pending 图提交为正式源图；编辑过程中退出不会影响旧组件背景。
 * 生成图通过 RemoteViews 传输，因此渲染器会严格控制像素数量，这里只负责磁盘和内存缓存。
 */
object WidgetGlassStore {

    private const val DIR_NAME = "widget_glass"
    private const val MAX_SOURCE_SIDE = 2048

    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun directory(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).also { it.mkdirs() }

    fun sourceFile(context: Context, widgetId: Int): File =
        File(directory(context), "source_$widgetId.jpg")

    fun pendingSourceFile(context: Context, widgetId: Int): File =
        File(directory(context), "pending_$widgetId.jpg")

    fun renderedFile(context: Context, widgetId: Int): File =
        File(directory(context), "rendered_$widgetId.png")

    /** 使用系统 ImageDecoder 处理 PNG/JPEG/HEIC 等常见格式，并自动校正 EXIF 方向。 */
    fun importSource(context: Context, uri: Uri, widgetId: Int): File {
        val app = context.applicationContext
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_SOURCE_SIDE) {
                val scale = MAX_SOURCE_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
        return try {
            writeBitmap(pendingSourceFile(app, widgetId), decoded, Bitmap.CompressFormat.JPEG, 92)
        } finally {
            decoded.recycle()
        }
    }

    fun loadSource(file: File): Bitmap? {
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var sample = 1
        val longest = maxOf(options.outWidth, options.outHeight)
        while (longest / (sample * 2) >= MAX_SOURCE_SIDE) sample *= 2
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    fun saveRendered(context: Context, widgetId: Int, bitmap: Bitmap) {
        val app = context.applicationContext
        writeBitmap(renderedFile(app, widgetId), bitmap, Bitmap.CompressFormat.PNG, 100)
        synchronized(cache) { cache.remove(cacheKey(renderedFile(app, widgetId))) }
    }

    fun loadRendered(context: Context, widgetId: Int): Bitmap? {
        val file = renderedFile(context, widgetId)
        if (!file.isFile) return null
        val key = cacheKey(file)
        synchronized(cache) {
            cache.get(key)?.let { return it }
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        ) ?: return null
        synchronized(cache) { cache.put(key, bitmap) }
        return bitmap
    }

    /**
     * 提交编辑中的截图和渲染图。任一步写入失败时恢复旧源图、旧渲染图和 pending 图，
     * 避免 SharedPreferences 尚未更新但旧背景已经被替换。
     */
    fun commitPendingSource(context: Context, widgetId: Int, rendered: Bitmap) {
        val app = context.applicationContext
        val pending = pendingSourceFile(app, widgetId)
        if (!pending.isFile) {
            saveRendered(app, widgetId, rendered)
            return
        }

        val target = sourceFile(app, widgetId)
        val renderedTarget = renderedFile(app, widgetId)
        val sourceBackup = File(target.parentFile, target.name + ".backup")
        val renderedBackup = File(renderedTarget.parentFile, renderedTarget.name + ".backup")
        val hadSource = target.isFile
        val hadRendered = renderedTarget.isFile

        try {
            if (hadSource) Files.copy(target.toPath(), sourceBackup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            if (hadRendered) Files.copy(renderedTarget.toPath(), renderedBackup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            saveRendered(app, widgetId, rendered)
            pending.delete()
        } catch (t: Throwable) {
            // move 已消耗 pending 时先恢复它，保证用户还能直接重试。
            if (!pending.isFile && target.isFile) {
                Files.copy(target.toPath(), pending.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            if (hadSource) {
                Files.move(sourceBackup.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } else {
                target.delete()
            }
            if (hadRendered) {
                Files.move(renderedBackup.toPath(), renderedTarget.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } else {
                renderedTarget.delete()
            }
            throw t
        } finally {
            sourceBackup.delete()
            renderedBackup.delete()
        }
    }

    fun discardPendingSource(context: Context, widgetId: Int) {
        pendingSourceFile(context, widgetId).delete()
    }

    fun deleteFiles(context: Context, widgetId: Int) {
        val app = context.applicationContext
        val rendered = renderedFile(app, widgetId)
        synchronized(cache) { cache.remove(cacheKey(rendered)) }
        rendered.delete()
        sourceFile(app, widgetId).delete()
        pendingSourceFile(app, widgetId).delete()
    }

    private fun cacheKey(file: File): String =
        "${file.absolutePath}|${file.lastModified()}|${file.length()}"

    private fun writeBitmap(
        target: File,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int
    ): File {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            FileOutputStream(temp).use { out ->
                check(bitmap.compress(format, quality, out)) { "无法保存液态玻璃图片" }
                out.flush()
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
        return target
    }
}
