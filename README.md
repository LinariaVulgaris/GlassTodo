# GlassTodo

GlassTodo 是一个使用 Kotlin 和 Android 官方 AppWidget API 开发的桌面待办应用。组件默认使用透明背景和白色文字，可直接叠加在壁纸上，也支持静态玻璃底板与自定义排版。

## 功能

- **待办事项**：新建、编辑、删除、撤销删除，点击待办直接切换完成状态。
- **截止时间**：每条待办可单独设置具体截止时间。
- **日期显示设置**：App 列表与桌面组件均可选择显示 DDL、剩余天数或两者，并可选择不显示时间、精确到小时或精确到分钟。
- **桌面组件**：
  - 待办组件支持自适应单列排版，可配置显示条数。
  - 可从桌面直接勾选待办、打开 App 或新建待办。
- **外观自定义**：自定义文字颜色、字号和玻璃底板。
- **静态液态玻璃**：导入桌面截图、校准组件区域并生成带模糊、折射和高光的背景。
- **自动更新**：数据变化、跨日、系统时间或时区变化、开机及应用更新后刷新组件。

> 液态玻璃背景为静态预渲染，不是实时读取壁纸。移动或缩放组件、更换壁纸后，需要重新校准背景。App 内的尺寸与形状设置控制排版；实际桌面占格仍需在桌面长按组件后调整。

## 环境要求

- Android Studio
- JDK 17
- Android SDK 36
- 最低运行系统：Android 12（API 31）

项目使用 Gradle Wrapper，无需单独安装 Gradle。首次在 Android Studio 中打开项目时，让其自动同步依赖并生成本机的 `local.properties`。

## 构建

在项目根目录执行：

```bash
./gradlew :app:assembleDebug
```

Windows 下也可以执行：

```bat
gradlew.bat :app:assembleDebug
```

Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Debug 包名为 `com.glass.todo.debug`，会由 Android Gradle Plugin 自动签名，可直接安装测试。正式 Release 包名为 `com.glass.todo`，当前尚未配置发布签名；上传应用市场前需要在 `app/build.gradle.kts` 中配置自己的 keystore。

## 测试与检查

```bash
# 单元测试
./gradlew :app:testDebugUnitTest

# Android Lint
./gradlew :app:lintDebug

# 构建 Debug 与 Release
./gradlew :app:assembleDebug :app:assembleRelease

# 编译仪器测试（需要连接设备或模拟器后运行）
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:connectedDebugAndroidTest
```

测试覆盖日期格式化、待办日期显示、组件字号和布局几何等逻辑。数据库迁移测试位于 `app/src/androidTest`，用于验证历史升级、v3 到 v4 删除倒数日数据以及待办数据保留情况。

## 技术栈

- Kotlin、Java 17
- Android AppWidget / RemoteViews / `RemoteCollectionItems`
- Room、Kotlin Coroutines 与 Flow
- ViewBinding、Material Components
- AndroidX、KSP、Gradle Version Catalog
- minSdk 31，compileSdk / targetSdk 36

应用不依赖 Compose 或 Glance，待办保存在本地 Room 数据库中，不读取系统日历，也不需要账号或网络权限。

## 项目结构

```text
app/src/main/java/com/glass/todo/
├── data/          # Room 实体、DAO、数据库迁移与偏好存储
├── ui/            # 待办、设置、玻璃校准与布局界面
├── util/          # 日期计算、文字格式、玻璃渲染和布局工具
└── widget/        # 桌面组件、点击动作与定时刷新
app/schemas/       # Room 数据库结构快照
app/src/test/      # 本地单元测试
app/src/androidTest/ # Android 仪器测试
```

## 数据升级

当前数据库版本为 **4**。v1 到 v3 保留历史迁移路径；v3 到 v4 删除倒数日 `events` 表，待办内容、截止时间和手动排序均保持不变。后续修改实体时，应增加新的迁移、导出对应 schema，并补充迁移测试。

## 开发者

本软件由 **LinariaVulgaris** 开发。

项目地址：[https://github.com/LinariaVulgaris/GlassTodo](https://github.com/LinariaVulgaris/GlassTodo)
