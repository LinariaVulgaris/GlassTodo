package com.glass.todo.widget.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.glass.todo.data.WidgetBackgroundConfig
import com.glass.todo.util.WidgetBackgroundRenderer
import com.glass.todo.widget.glass.WidgetGlassStore
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * 组件背景图片与裁剪结果的本地存储。
 *
 * 新文件放在 widget_backgrounds；旧版液态玻璃保存的原始截图仍可作为图片来源读取，
 * 但旧的模糊渲染图永远不会直接用于新背景。保存后的位图同样受 Binder 像素上限约束。
 */
object WidgetBackgroundStore {
    private const val TAG = "WidgetBackgroundStore"
    private const val DIR_NAME = "widget_backgrounds"
    private const val MAX_SOURCE_SIDE = 2560

    private val widgetLocks = ConcurrentHashMap<Int, Any>()

    private fun widgetLock(widgetId: Int): Any =
        widgetLocks.computeIfAbsent(widgetId) { Any() }

    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun directory(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).also { it.mkdirs() }

    private fun sourceTarget(context: Context, widgetId: Int): File =
        File(directory(context), "source_$widgetId.png")

    private fun legacySourceTarget(context: Context, widgetId: Int): File =
        File(directory(context), "source_$widgetId.jpg")

    /** 新原图不存在时回退到旧版截图，实现无缝迁移且不重复占用一份大图。 */
    fun sourceFile(context: Context, widgetId: Int): File =
        sourceTarget(context, widgetId).takeIf { it.isFile }
            ?: legacySourceTarget(context, widgetId).takeIf { it.isFile }
            ?: WidgetGlassStore.sourceFile(context, widgetId)

    fun pendingSourceFile(context: Context, widgetId: Int): File {
        val png = File(directory(context), "pending_$widgetId.png")
        if (png.isFile) return png
        return File(directory(context), "pending_$widgetId.jpg")
    }

    /** v2 使用更高的像素预算与 RGB_565；旧的低分辨率渲染图自然失效并按需重建。 */
    private fun renderedTarget(context: Context, widgetId: Int): File =
        File(directory(context), "background_${widgetId}_v2.png")

    private fun legacyRenderedFile(context: Context, widgetId: Int): File =
        File(directory(context), "background_$widgetId.png")

    fun renderedFile(context: Context, widgetId: Int): File = renderedTarget(context, widgetId)

    /** 使用系统 ImageDecoder 处理 PNG/JPEG/HEIC 等常见格式，并自动校正 EXIF 方向。 */
    fun importSource(context: Context, uri: Uri, widgetId: Int): File = synchronized(widgetLock(widgetId)) {
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
        try {
            // PNG 无损保存原图，避免 JPEG 92 在裁剪、放大后产生额外的块状失真。
            val target = File(directory(app), "pending_$widgetId.png")
            writeBitmap(target, decoded, Bitmap.CompressFormat.PNG, 100)
            // 清理上一轮可能残留的旧 JPEG 草稿，后续统一读取新的 PNG。
            File(directory(app), "pending_$widgetId.jpg").delete()
            target
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

    fun saveRendered(context: Context, widgetId: Int, bitmap: Bitmap): Unit = synchronized(widgetLock(widgetId)) {
        val app = context.applicationContext
        val file = renderedFile(app, widgetId)
        writeBitmap(file, bitmap, Bitmap.CompressFormat.PNG, 100)
        synchronized(cache) { cache.remove(cacheKey(file)) }
    }

    fun loadRendered(context: Context, widgetId: Int): Bitmap? = synchronized(widgetLock(widgetId)) {
        val file = renderedFile(context, widgetId)
        if (!file.isFile) return null
        val key = cacheKey(file)
        synchronized(cache) {
            cache.get(key)?.let { return it }
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        ) ?: return null
        synchronized(cache) { cache.put(key, bitmap) }
        bitmap
    }

    /** 首次应用或迁移旧截图时生成普通图片背景，后续刷新直接读缓存。 */
    fun loadOrCreateRendered(
        context: Context,
        widgetId: Int,
        config: WidgetBackgroundConfig,
        minimumPixels: Int = 0
    ): Bitmap? = synchronized(widgetLock(widgetId)) {
        if (!config.isImage) return null
        loadRendered(context, widgetId)?.let { return it }
        val sourceFile = sourceFile(context, widgetId)
        val source = loadSource(sourceFile) ?: return null
        val rendered = try {
            WidgetBackgroundRenderer.render(source, config.crop ?: FULL_CROP, minimumPixels = minimumPixels)
        } finally {
            source.recycle()
        }
        try {
            saveRendered(context, widgetId, rendered)
            loadRendered(context, widgetId) ?: rendered
        } catch (t: Throwable) {
            // 磁盘写入失败不应让整个组件刷新失败；本次仍可使用内存中的位图。
            Log.e(TAG, "保存组件背景失败", t)
            rendered
        }
    }

    /**
     * 提交编辑中的原图和背景图。任一步写入失败时恢复旧文件，
     * 避免 SharedPreferences 尚未更新但旧背景已经被替换。
     */
    fun commitPendingSource(context: Context, widgetId: Int, rendered: Bitmap): Unit = synchronized(widgetLock(widgetId)) {
        val app = context.applicationContext
        val pending = pendingSourceFile(app, widgetId)
        if (!pending.isFile) {
            saveRendered(app, widgetId, rendered)
            return
        }

        val target = sourceTarget(app, widgetId)
        val renderedTarget = renderedFile(app, widgetId)
        val hadSource = target.isFile
        val hadRendered = renderedTarget.isFile
        var sourceBackup: File? = null
        var renderedBackup: File? = null
        var sourceBackedUp = false
        var renderedBackedUp = false
        var preserveBackups = false

        try {
            if (hadSource) {
                sourceBackup = File.createTempFile(target.name + "_", ".backup", target.parentFile)
                Files.copy(target.toPath(), sourceBackup.toPath(), StandardCopyOption.REPLACE_EXISTING)
                sourceBackedUp = true
            }
            if (hadRendered) {
                renderedBackup = File.createTempFile(renderedTarget.name + "_", ".backup", renderedTarget.parentFile)
                Files.copy(renderedTarget.toPath(), renderedBackup.toPath(), StandardCopyOption.REPLACE_EXISTING)
                renderedBackedUp = true
            }
            moveReplacing(pending, target)
            saveRendered(app, widgetId, rendered)
            pending.delete()
            // 新原图已经落盘，不再回退到旧截图，同时释放迁移前占用的空间。
            WidgetGlassStore.deleteFiles(app, widgetId)
            legacySourceTarget(app, widgetId).delete()
            legacyRenderedFile(app, widgetId).delete()
        } catch (t: Throwable) {
            preserveBackups = true
            try {
                if (!pending.isFile && target.isFile) {
                    Files.copy(target.toPath(), pending.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (rollback: Throwable) {
                t.addSuppressed(rollback)
            }
            if (sourceBackedUp) {
                try {
                    moveReplacing(sourceBackup!!, target)
                } catch (rollback: Throwable) {
                    t.addSuppressed(rollback)
                }
            } else if (!hadSource) {
                target.delete()
            }
            if (renderedBackedUp) {
                try {
                    moveReplacing(renderedBackup!!, renderedTarget)
                } catch (rollback: Throwable) {
                    t.addSuppressed(rollback)
                }
            } else if (!hadRendered) {
                renderedTarget.delete()
            }
            synchronized(cache) { cache.remove(cacheKey(renderedTarget)) }
            throw t
        } finally {
            if (!preserveBackups) {
                sourceBackup?.delete()
                renderedBackup?.delete()
            }
        }
    }

    fun discardPendingSource(context: Context, widgetId: Int): Unit = synchronized(widgetLock(widgetId)) {
        val directory = directory(context)
        File(directory, "pending_$widgetId.png").delete()
        File(directory, "pending_$widgetId.jpg").delete()
    }

    fun deleteFiles(context: Context, widgetId: Int): Unit = synchronized(widgetLock(widgetId)) {
        val app = context.applicationContext
        val rendered = renderedFile(app, widgetId)
        synchronized(cache) { cache.remove(cacheKey(rendered)) }
        rendered.delete()
        legacyRenderedFile(app, widgetId).delete()
        sourceTarget(app, widgetId).delete()
        discardPendingSource(app, widgetId)
        legacySourceTarget(app, widgetId).delete()
        // 迁移后不再需要的旧版原图与渲染图一起清理。
        WidgetGlassStore.deleteFiles(app, widgetId)
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
        val temp = File.createTempFile(target.name + "_", ".tmp", target.parentFile)
        try {
            FileOutputStream(temp).use { out ->
                check(bitmap.compress(format, quality, out)) { "无法保存组件背景图片" }
                out.flush()
            }
            moveReplacing(temp, target)
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
        return target
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private val FULL_CROP = com.glass.todo.data.GlassCrop(0f, 0f, 1f, 1f)
}
