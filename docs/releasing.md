# 发版

开心果 Android 不上应用商店，发版就是构建签好名的 APK，放到 GitHub Release 上。

## 签名密钥

APK 要一直用同一把密钥签名，用户才能直接覆盖升级。密钥丢了，用户只能卸载重装，观看记录和收藏会一起丢掉，所以**不要换密钥，也不要弄丢**。

现在用的密钥：

- 密钥文件：`~/keys/kaixinguo.jks`（别名 `kaixinguo`，RSA 4096，有效到 2126 年）
- 密码：存在 macOS 钥匙串里，名称是「开心果 Android 签名密钥密码」（服务名 `kaixinguo-android-keystore`）
- 证书 SHA-256 指纹：`9e9a4ece9c19ec09d3be9dc4f515bcce22f318e0f026253945e4041f64b42214`

密钥文件和密码要另外备份一份，比如存进密码管理器。

仓库根目录的 `keystore.properties` 告诉 Gradle 用哪把密钥（已经在 `.gitignore` 里，不会提交）。换了电脑或重新克隆后，把密钥文件放回 `~/keys/`，再用钥匙串里的密码重建这个文件：

```sh
p="$(security find-generic-password -s kaixinguo-android-keystore -w)"
(umask 077; printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=kaixinguo\nkeyPassword=%s\n' \
  "$HOME/keys/kaixinguo.jks" "$p" "$p" > keystore.properties)
```

没有 `keystore.properties` 时，release 包用调试密钥签名，只适合自己装着用。

## 每次发版

1. 在 `app/build.gradle.kts` 里把 `versionCode` 加一，`versionName` 改成新版本号。
2. 在 [CHANGELOG.md](../CHANGELOG.md) 里把「未发布」下面的内容挪到新版本下，写上日期，并更新文件底部的比较链接。
3. 测试并构建，确认签名是上面那把密钥（指纹对得上）：

   ```sh
   ./gradlew testDebugUnitTest assembleRelease
   ~/Library/Android/sdk/build-tools/36.0.0/apksigner verify --print-certs \
     app/build/outputs/apk/release/app-universal-release.apk | grep SHA-256
   ```

4. 在真机上装 `app-arm64-v8a-release.apk` 覆盖旧版，确认观看记录和收藏都还在，完整播一集。
5. 把 APK 改成带版本号的名字，算好校验和：

   ```sh
   v=0.1.0
   rm -rf dist && mkdir dist
   for abi in arm64-v8a universal armeabi-v7a x86_64; do
     cp app/build/outputs/apk/release/app-$abi-release.apk dist/kai-xin-guo-$v-$abi.apk
   done
   (cd dist && shasum -a 256 *.apk > kai-xin-guo-$v.sha256)
   ```

6. 提交、打标签、推送，建 GitHub Release，附上 `dist/` 里的文件。说明用 CHANGELOG 里这一版的内容，再加上「安装」一节（下哪个 APK、覆盖安装会保留记录、华为手机的提示、签名指纹和校验方法），参考上一版的 Release：

   ```sh
   git commit -am "发布 $v"
   git tag v$v
   git push origin main v$v
   gh release create v$v dist/* --title "开心果 Android $v" --notes-file notes.md
   ```
