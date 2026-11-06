# 参与开发

谢谢你愿意帮忙。开心果 Android 和 macOS、iOS 版一样，目标是**一直简单、一直快**：几页列表、一个详情页、一个播放器。新增功能之前请先想想它是否值得多出来的那份复杂度。

## 开始之前

- **报告问题**：附上手机型号、系统版本（华为请写 EMUI / HarmonyOS 的版本）和调试版的日志：`adb logcat -s kxg sdk`。
- **接口失效**（列表全空、所有剧都播不了）：多半是红果接口变了，先看看 [openhongguo](https://github.com/beilunyang/openhongguo/issues) 有没有人报过。
- **和 iOS、macOS 版共有的逻辑**（分页、选季、续播、解密）：几边要保持一致，改一边时看看另一边要不要跟着改。

## 开发环境

- JDK 17 或更新的版本
- Android SDK：`local.properties` 里写上 `sdk.dir=<SDK 的路径>`。缺的平台和构建工具，Gradle 第一次构建时会自己下载
- Android Studio 可选

```sh
./gradlew testDebugUnitTest   # 逻辑和解密测试，不需要模拟器和网络
./gradlew installDebug        # 装到连着的手机或模拟器
```

先读 [docs/architecture.md](docs/architecture.md)，尤其是「线程模型」一节。

## 提交前检查

```sh
./gradlew testDebugUnitTest assembleRelease
```

改了界面的话，在手机和平板（或大屏模拟器）上都实际点一遍，浅色和深色外观都看看，横竖屏都转一转；改了播放，再完整播一集，拖一下进度，试试画中画和熄屏播放。

## 代码约定

- **逻辑放进 `model`**：不依赖 Android 框架的都放在 `model` 和 `cenc` 里，好在电脑上测试。`ui` 只放界面。
- **主线程独占状态**：模型的状态都在主线程上读写，后台工作做完回到主线程再改状态。
- **界面里没有副作用**：组合函数会反复执行，加载数据放进 `LaunchedEffect`，模型里要有防重复的标记。
- **新功能配测试**：逻辑用 `FakeCatalog` 和 `FakeVideo` 写单元测试，参考 `CatalogTest.kt`。
- **注释**：用英文写完整的句子，说清楚一个东西**是什么**或**为什么**这样做，而不是复述代码。照着已有代码的口吻写。
- **界面文字**用简体中文，标点用全角。
- **依赖**：除了 AndroidX、Media3 和 openhongguo，不加第三方依赖；也不要引入 Google Play 服务，否则华为手机用不了。
- **本地数据格式**：`history.json`、`favorites.json` 和 macOS、iOS 版共用格式，只能加字段，不能改名或删除。

## 提交信息

用中文，一行概括改了什么，必要时空一行再写原因，和 macOS、iOS 版一样。

## 更新日志

用户能感知到的改动，请在 [CHANGELOG.md](CHANGELOG.md) 的「未发布」下面加一行。纯重构和测试不用写。

## 许可证

提交的代码以 [MIT](LICENSE) 许可证发布。
