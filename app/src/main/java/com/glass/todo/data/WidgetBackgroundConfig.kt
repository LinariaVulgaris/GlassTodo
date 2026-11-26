package com.glass.todo.data

/** 组件背景类型：默认背景、用户图片或纯色块。 */
enum class WidgetBackgroundType {
    NONE,
    IMAGE,
    COLOR
}

/** 每个桌面组件独立保存的背景配置；图片裁剪坐标仍复用归一化图像区域。 */
data class WidgetBackgroundConfig(
    val type: WidgetBackgroundType = WidgetBackgroundType.NONE,
    val color: Int = DEFAULT_COLOR,
    val sourceFileName: String? = null,
    val crop: GlassCrop? = null
) {
    val isImage: Boolean
        get() = type == WidgetBackgroundType.IMAGE && !sourceFileName.isNullOrBlank()

    companion object {
        const val DEFAULT_COLOR = 0xFF263238.toInt()
    }
}
