# Ncrust v1.3.8

## 许可证说明 / Upstream License Attribution
本软件基于 [GuitaristRin/Ncrust](https://github.com/GuitaristRin/Ncrust) 开源项目衍生构建。
原项目版权所有：Copyright (c) 2026 Takahashi_Rinta，遵循 MIT 许可证。完整许可证内容参见仓库 [LICENSE](https://github.com/GuitaristRin/Ncrust/blob/master/LICENSE)。

---

## 更新内容 / Release Notes (v1.3.8)

1. **首页模块重构：用「继续收听」替代「新歌速递」**
   - 彻底移除首页对远端无针对性新歌速递接口的依赖与冷启动预取开销；
   - 首页第二栏改为更加个性化的「继续收听」，优先读取本地最新收听足迹（最多 20 首），为空时平滑回退到我喜欢的单曲，无历史与收藏时则优雅降级为推荐歌单矩阵；
   - 标题栏增加一键「播放全部」快捷动作；
   - 覆盖中英日等 8 种语言的国际化文字映射。

2. **冷启动网络与内存优化**
   - 在 `AppWarmup` 和 `ContentCache` 中移除冗余的新歌速递接口请求与封面预取，进一步缩短冷启动网络耗时与主线程卡顿。

3. **详情页脚手架稳定性保障**
   - 恢复 `DetailScaffold` 对 `body` 插槽与横向分页（如歌手页热门歌曲 / 专辑滑动切换）的完整支持，确保各页面架构与滚动体验平滑一致。

---

### 下载
- **Ncrust-v1.3.8-release.apk** (SHA-256 / v2 签名校验通过，R8 全量优化体积 ~3.82 MB)
