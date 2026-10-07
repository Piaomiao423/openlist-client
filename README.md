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
- **会话**：DataStore 持久化登录状态，Token 过期自动退回登录页

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
2. 打开 App，输入服务器地址（如 `http://192.168.1.100:5244`）、用户名、密码
3. 登录后即可浏览和操作文件

自建服务通常为内网 HTTP 明文，`network_security_config.xml` 全局放开了明文流量（有意的安全取舍），使用 https 部署时可自行收紧。

## 项目结构

```
app/src/main/java/com/openlist/client/
├── data/          # 数据层：模型、Retrofit 接口、仓库（下载候选逐级重试、上传完整性校验）
├── ui/screen/     # 登录 / 文件浏览 / 预览 / 收藏界面
├── ui/viewmodel/  # ViewModel
├── ui/theme/      # 主题与自适应尺寸
└── util/          # 会话管理、编码检测、语法高亮等工具
```

## License

[MIT](LICENSE)
