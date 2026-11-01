# Ncrust v1.4.7 Release Notes

> 本项目为 [GuitaristRin/Ncrust](https://github.com/GuitaristRin/Ncrust) 的分支定制版本。
> 原项目开源许可证：MIT License (Copyright (c) 2026 Takahashi_Rinta).

---

### 🐞 全仓缺陷排查与修复版（无新功能）

本版不改任何界面观感、不加功能，只有缺陷修复。全部改动来自一次全仓代码审查：播放层、数据层、界面层各扫一遍，逐条给出「可复现路径 + 代码证据」后才动手。

---

### 💾 播放会话落盘：不再被自己的取消删掉

- **现象**：进程被杀后重启，队列/播放模式丢失；偶发「上次听到哪一首」完全消失。
- **根因**：`PlaybackStateManager.saveSession` 用 `flushJob?.cancel()` 抢占上一次待写任务（200ms 防抖）。协程取消是以 `CancellationException` 抛出的，而它也是 `Exception` 的子类，被同一个 `catch (_: Exception)` 兜住后当成「序列化失败」，反手执行 `clearSession(ctx)` —— 刚排队的新变更丢了，上一次已成功落盘的有效会话也被从磁盘删掉。
- **修复**：先 `catch (e: CancellationException) { throw e }` 再 `catch (_: Exception) { clearSession(ctx) }`。取消归取消，真失败才清空。

### 📋 队列三条不变量修复

1. **删除队列项后游标不左移**（`removeFromQueue`）：`[A,B,C]` 播 B 时删掉 A，队列变成 `[B,C]` 而游标仍指 1（= C）→ 高亮错行、落盘索引错误、预载错歌。现改为纯函数 `PlaybackQueueLogic.indexAfterRemoval` 统一算游标（删游标之前的项减一、删当前项落到下一首、删空置 -1、越界夹紧），并补了 6 组 JVM 单测。
2. **乱序模式下点选曲目后两游标脱钩**（`playFromQueue`）：只改 `currentQueueIndex` 不动 `shuffledPosition`，点选的歌播完会按旧乱序序列接走（漏播/重播）。现点选时回填 `shuffledPosition`，找不到就重洗。
3. **线性模式到队尾时 UI 与音频长期不一致**（`playNext`）：原实现把游标搬到队首、`currentSong` 改成首曲，却只调 `pausePlayback()` —— ExoPlayer 里载入的还是队尾那首，再点播放会 resume 队尾歌。现该分支只暂停，游标与当前曲都不动。

### 🔁 不可播放歌曲的无限环绕熔断

- **现象**：登录过期 / 无会员 / 整队列下架时，播放器会不停跳歌，每跳一次还附带最多 6 次逐音质档位的取链请求，发热耗电。
- **根因**：取链全档失败 → `onUnplayable` → `playNext()` 的链路没有任何计数或熔断，CYCLE/SINGLE/SHUFFLE/INFINITY 四种模式都会永久环绕。
- **修复**：连续不可播计数累计到队列长度即暂停并停止续跳，真正切歌成功时清零。

### 🎛️ 恢复歌词/队列视图下的控制栏紧凑布局

- 设置页里的「歌词模式精简控制栏」此前是摆设：`de764eb` 曾把它接成条件式（面板滑出时收紧控制栏），40 分钟后 `7c5089a` 把它改成了恒 `false`，开关从此拨动无效果。消费端 `FullPlayerControls` 的尺寸分级一直完好未删，所以这是**回退回归**而非新功能，恢复条件式即可（`PlayerCard.kt:644`）。
- **注意行为变化**：该项默认开启，所以打开歌词或队列视图时，底部播放控制栏会自动收紧（按钮尺寸从 76dp 缩到 52dp、左右内边距 24dp→16dp），给歌词留出更大视野。若不喜欢，可在「歌词」卡片里关掉该开关。若冒烟发现尺寸异常，回退只需把该行改回 `compact = false`。
- 同时隐藏 4 个**从未接线**（写 `prefs` 但全仓无消费者）的开关入口：性能与低功耗模式、看歌词时屏幕常亮、Fluent Design 质感小圆角、触觉轻震反馈。`ncrust_settings` 里的存量键与文案一律保留，升级后不会重置任何设置；每处删除位置都留了注释，实现后按注释恢复入口即可。

### 🎧 车机/蓝牙语音搜索

- 补齐 `MEDIA_PLAY_FROM_SEARCH` 的 intent-filter 与 `MediaSessionCompat.Callback.onPlayFromSearch` 覆写（此前 Lint 判为 error）。
- 搜索与入队的决定留给界面层：命中单曲走既有的「插到当前下一首并立即播」路径，**不**让 service 自己取流直连播放，避免破坏「队列下标 ≡ 正在播的曲目」这条不变量。

### 📊 首页空白与收藏分页漏歌

- **首页空白**：`AppWarmup` 在断网/接口失败时两个请求都返回 null，却照样打上「刚预热过」的时间戳；`ContentCache.clearAll()` 又只清数据不清时间戳 —— 于是冷启动离线、或切换账号/内存压力清缓存后 15 秒内进首页，`HomeScreen` 认定「刚预热过」直接返回，页面停在**空网格且既无 loading 也不重试**。现三处一起修：清缓存连时间戳归零、只有真拿到数据才打标记、界面侧再要求数据非空才允许提前返回。
- **收藏分页漏歌**：`loadMoreLikedSongs` 用 `cachedSongs.size` 当红心歌单下标。本地点赞会往缓存头部插入一条、底表不动 → 偏移 +1，每点一次赞就永久跳过一首；取消收藏则反向下漂，触底空转。现偏移改为「底表中第一首尚未加载的歌」，并让点赞/取消同步维护底表。

### 🎼 歌词解析

- 一行挂多个时间标签（`[00:01.00][00:05.00]同一句词`）此前只取第一个标签，后续标签会当成正文残留在面板上直接显示 `[00:05.00]`，且 5 秒处永不出现这句词。现按标签展开成多条。
- 不带毫秒的 `[mm:ss]` 此前整行被正则丢弃，现按 0 毫秒计入。
- 新增 `LrcParserTest`（8 个用例）：单标签、三位毫秒、多标签展开、无毫秒、同行双语、乱序时间、空文本/无标签行、升序契约。

### 🔐 加密与网络层

- `EapiCrypto` / `PlaylistApi` 原使用 `java.util.Base64`（**API 26+**），而本应用 `minSdk 24` —— Android 7.0/7.1 设备上执行到该行会 `NoClassDefFoundError` 崩溃。现统一改用 `android.util.Base64`（解码 `DEFAULT`、编码 `NO_WRAP`，与既有 `WeapiCrypto` 口径一致）。
- `updatePlaylistInfo` 的回退分支丢弃了 `/api/playlist/desc/update` 的响应体（OkHttp 连接不释放，直到 GC），且只返回名称分支的结果 —— 描述更新失败也报成功。现消费并校验，两步都成功才算成功。
- `RetrofitClient.currentCookie` 加 `@Volatile`：登录/登出在主线程写、拦截器在 OkHttp 线程读，二者无 happens-before 边，此前可能读到旧值（表现为刚登录后个别请求不带 Cookie）。

### ⚡ 界面层

- **Android 7.0/7.1 封面主题色崩溃**：`PlayerCard` 无条件读取 `Bitmap.Config.HARDWARE`（API 26 才加入的枚举常量），只要有封面位图就会抛 `NoSuchFieldError`。现加 `SDK_INT >= O` 守卫，低版本走原图。
- **歌词面板去掉无效的 Crossfade**：`Crossfade(targetState = song?.id)` 两层 lambda 读的是**同一个** `lyrics` State，而切歌瞬间 `resetLyricsForNewSong` 已把它清空 —— 「旧词淡出 / 新词淡入」从未真正发生，只是过渡期短暂多合成一层全屏歌词列表（低端机掉帧）。现保留单层，观感仍由既有的面板淡出 + 大封面覆盖提供。
- **队列行 key 加下标兜底**：`"s-${id}"` 在队列出现重复 id 时会生成重复 key，`LazyColumn` 直接抛 `IllegalArgumentException`。现 key 带上队列下标，同一次渲染内必然唯一（`replaceQueueAndPlay` 侧也补了 `distinctBy`）。
- **搜索清除后的脏结果**：`clearQuery()` 未取消已排队的 500ms 防抖任务，清除后可能被挂起任务又填回一轮结果。现补 `searchJob?.cancel()`。
- **并发安全**：`PlayerViewModel` 的预载缓存改 `ConcurrentHashMap`、在途标记加 `@Volatile`；`ContentCache` 的 LRU 从 `androidx.collection.LruCache`（文档明确非线程安全）换成 `android.util.LruCache`（内部同步）。
- **冷启动预热**：`AppWarmup` 的 SharedPreferences 预触摸清单补上 `play_history`，最近播放的首次读盘不再落在主线程。
- **主题**：`windowLightNavigationBar`（API 27）标注 `tools:targetApi="27"`，消除 Lint 的 NewApi 误报（API 24–26 平台本就忽略该属性）。

---

### 📦 安装包验证

- **文件名**：`Ncrust-v1.4.7-release.apk`
- **版本号**：`v1.4.7` (versionCode 22)
- **编译方式**：R8 全量优化，混淆与资源缩减已开启
- **Lint**：release 变体 **0 error**（此前 10 条）
- **单测**：JVM 单测 48 例全绿（新增 LrcParser 8 例 + 队列游标 6 例）
- **R8 落盘兼容性**：`library` 包持久化模型的混淆字段名与 v1.4.6 一致（`AlbumInfo` / `PlayHistoryManager$Entry` / `SearchHistoryManager$HistoryItem` 均为 a/b/c/d/e），覆盖安装后旧数据仍可读

---

### ℹ️ 已知但本版未修

- `i18n` 覆盖缺口：`en` 缺 1 条、`zh_TW`/`ja_JP` 各缺 23 条、其余 5 个语言各缺 24 条（编辑歌单、批量管理、加入歌单、缓存标签等新文案只有简中）。需要人工定译文，单列为一次 `fix(i18n)` 提交。
- 4 个设置开关「写而无读」（低功耗模式、歌词常亮、现代圆角、触觉反馈）：拨动无实际效果，属功能实现而非缺陷修补，**本版已隐藏它们的入口**（`ncrust_settings` 里的存量键与文案一律保留，不会重置用户设置）。其中「现代圆角」与 Kanesumi 直角规范冲突，实现前需先定规范口径。
- `PlayerCard` 组合期读取动画值（性能问题，非功能缺陷）：修复会牵动预测性返回手势的注册时机，属历史反复回归区，本版不动。
