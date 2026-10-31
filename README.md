# OpenListClient

自建 [OpenList](https://github.com/OpenListTeam/OpenList) 网盘服务的 Android 客户端。

纯 Jetpack Compose 实现，单 Activity 架构。支持文件浏览、在线预览、上传下载、搜索与收藏，专为连接自建/内网部署的 OpenList 服务设计。

## 功能

- **文件浏览**：目录树导航、返回上级恢复上次浏览位置、刷新、搜索、收藏夹
- **在线预览**（流式，大文件秒开）：
  - 图片 / GIF：双击两级放大 + 单指平移，按原分辨率解码保持清晰
  - 视频 / 音频：内嵌播放器
  - PDF：官方 PdfRenderer 逐页渲染
  - 文本 / 代码：20+ 语言语法高亮，自动识别 UTF-8 / UTF-16 / GBK 编码
  - 压缩包：zip / jar / apk 内部文件列表
- **文件操作**：上传（同步 + 任务轮询 + 服务器端大小校验，杜绝假成功）、下载到系统存储、新建文件夹、重命名、删除、查看属性
- **多服务器**：启动即进入服务器列表，支持添加服务器；点当前服务器进入，点其他已保存服务器即可切换（复用 Token，无需重新登录），也可删除服务器
- **会话**：DataStore 持久化登录状态，Token 过期自动退回服务器列表；退出当前服务器不影响其他已保存服务器

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin 1.9 · Jetpack Compose（BOM 2024.02.01）· Material 3 |
| 网络 | Retrofit 2 · OkHttp 4 · Gson |
| 图片 / GIF | Coil 2 |
| 本地存储 | DataStore Preferences |
| 架构 | 单 Activity + Navigation Compose · MVVM |

## 构建

要求：JDK 17（Gradle 8.4 不支持 JDK 22+）

```bash
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 使用

1. 部署好 OpenList 服务（默认监听 5244 端口）
2. 打开 App 先进入服务器列表，点「添加服务器」，输入服务器地址（如 `http://192.168.1.100:5244`）、用户名、密码
3. 登录后即可浏览和操作文件；下次启动仍会先进入服务器列表，点击上次使用的服务器即可进入
4. 访问其他服务器时，点浏览页右上角的服务器图标进入服务器列表；点当前服务器进入，点其他已保存服务器切换，无需重新登录

收藏夹按服务器隔离，切换服务器后只显示当前服务器的收藏。

### 明文传输策略

自建服务通常为内网 HTTP。Android 的网络安全配置不支持按私有网段（CIDR）匹配，而服务器地址由用户登录时填写，无法在编译期枚举，因此平台层保持明文可用，实际限制由 `CleartextGuard` 在运行时执行：

- 私有网络 / 本机地址（`10/8`、`172.16/12`、`192.168/16`、`169.254/16`、回环、`*.local` 等内网主机名）允许 HTTP
- 公网地址必须使用 HTTPS，否则请求会被拒绝并提示
- API、下载、Coil 图片加载、视频/音频流式预览均应用同一策略

公网部署请直接使用 HTTPS。

## 项目结构

```
app/src/main/java/com/openlist/client/
├── data/          # 数据层：模型、Retrofit 接口、仓库（下载候选逐级重试、上传完整性校验）
├── ui/screen/     # 登录 / 文件浏览 / 收藏界面
│   └── preview/   # 文件预览（按类型拆分为图片/代码/PDF/音频/视频/压缩包）
├── ui/viewmodel/  # ViewModel
├── ui/theme/      # 主题与自适应尺寸
└── util/          # 会话管理、URL 编解码、编码检测、语法高亮等工具
```

## 测试

```bash
./gradlew testDebugUnitTest
```

覆盖 URL 编解码、明文 HTTP 策略，以及仓库层（文件列表 / 搜索 / 下载链接回退 / 下载缓存 / 上传轮询与大小校验）。

## License

[MIT](LICENSE)
