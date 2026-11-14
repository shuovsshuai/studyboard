# 接手文档 HANDOFF

> 换电脑 / 换人继续开发时，先读这份文件。所有关键信息都在这里，不需要依赖任何单台电脑上的环境。
> 最后更新：2026-11-14（v2.2 / versionCode 14）

---

## 1. 这是什么

家里 **小度在家1S（型号 NV2001）** 上常驻运行的「学习计划看板」原生 Android App。
替代原厂桌面，全天候显示当前时间、当前/下一项学习计划、倒计时环，到点语音播报提醒。

- 包名：`com.family.studyboard`
- App 名：学习看板
- 语言：纯 Java，**不依赖 Gradle**，用 `build.sh`（aapt2 + javac + d8 + apksigner）手工构建
- 仓库：`git@github.com:shuovsshuai/studyboard.git`（公开仓库，main 分支）

## 2. 设备现状（已改造完成，不需要再刷机）

| 项目 | 值 |
|---|---|
| 型号 | 小度在家1S **NV2001** |
| 屏幕 | 1024×600（7 寸），Android 8.1，1G RAM / 8G ROM |
| 序列号 | 2G2037CC0E3EA927 |
| 系统固件 | `DuerShow_RK3326_v1.66.3.20230318151114760.R`（已刷，含 Magisk 25.2 root） |
| 局域网 IP | `192.168.2.127`（DHCP，可能变化） |
| 默认桌面 | `com.family.studyboard`（本 App 同时声明了 LAUNCHER / HOME / LEANBACK_LAUNCHER） |
| 已 root | Magisk 25.2，本 App 已获超级用户授权（自动更新靠它静默安装） |
| 原厂/当贝桌面 | 当贝已卸载；小度原桌面仍在系统里但不再抢前台 |

刷机要点（万一哪天需要重刷）：RKDevTool v2.86，只刷 boot + system，地址 Boot `0x22000`、System `0x0026c000`；进 LOADER 模式是**先按住顶部圆键再上电**（1S 只有一个集成键，狂按无效）。

## 3. 换电脑怎么继续

**代码在 GitHub，编译在 GitHub Actions，所以新电脑什么都不用装（除了 Git）。**

```bash
# 1. 拿代码
git clone git@github.com:shuovsshuai/studyboard.git studyboard
cd studyboard

# 2. 改代码（src/com/family/studyboard/）

# 3. 发版三步曲：改完 → 抬版本号 → 推
#    打开 AndroidManifest.xml，versionCode +1、versionName +0.1（必须同步抬！）
git add -A
git commit -m "说明改了什么"
git push
```

约 5 分钟后 GitHub Actions 自动编译并用正式签名打包，发布 Release。
小度会在**开机时**或**每 6 小时**自动检查升级（root 静默安装 + 自动重启回看板）。

**新电脑的 Git 认证**（二选一）：
- SSH（推荐）：`ssh-keygen -t ed25519 -C "your@mail"` → 把 `~/.ssh/id_ed25519.pub` 内容加到 GitHub → Settings → SSH and GPG keys
- 或者直接用 HTTPS + Personal Access Token

**本地想自己编译**（可选，非必需）：需要放 `jdk/`（JDK 17）和 `sdk/`（Android SDK，platform 33 + build-tools 33.0.2）两个目录进来，然后 `bash build.sh`。这两个目录很大且已在 `.gitignore` 里，不在仓库中——新电脑上完全可以跳过，交给 CI。

## 4. 签名（最关键，别丢）

- keystore 文件：`keystore.jks`（**不进仓库**，`.gitignore` 排除）
- alias：`sb`；storepass / keypass 均为 `study123`
- 云端已存为 GitHub Secret：`KEYSTORE_BASE64`（keystore 的 base64）、`KS_PASS`（= study123）
- 签名证书 SHA-1：`235fc24ff75cddfffdecdafcf131dba333c2bf8a`

> **后果警告**：一旦密钥丢失，之后打出的包签名就和设备上的不同，设备只能卸载重装（会丢掉所有计划设置）。
> 建议把 `keystore.jks` 或它的 base64 文本再备份一份到网盘/微信收藏。GitHub Secret 里那份是主备份。

## 5. 自动更新机制（`Updater.java`）

```
GET {base}update.json  →  比对 versionCode  →  大了就 GET {base}StudyBoard.apk
   →  su -c "pm install -r -d <apk>"  →  am start 回看板
```

- 更新源地址：`https://github.com/shuovsshuai/studyboard/releases/latest/download/`（代码里是 `Updater.DEFAULT_BASE`，设备设置页也能改）
- 仓库**必须保持 Public**，否则设备匿名下载不了 Release
- 检查间隔 6 小时，开关和地址在设备设置页「自动更新」区
- 失败日志：设备上 `/data/local/tmp/sb_update.log`
- 国内访问 GitHub 偶发不通，失败是静默跳过（下个周期再试）。若要更稳，可给 `Updater.base` 配国内备用源（自建/对象存储，放同样两个文件即可）

## 6. 设备上的操作（现场维护用）

| 操作 | 方法 |
|---|---|
| 进家长设置 | 长按屏幕**左下角 2.5 秒**，或点右下角小齿轮；密码 `1234` |
| 传文件进设备 | ES 文件浏览器 → 远程管理器（显示 `ftp://192.168.2.127:3721/`，匿名）→ 电脑端 FTP 上传。注意：切走该界面 FTP 就停 |
| 看当前版本 | 看板右下角小齿轮左边的极淡小字，如 `v2.2 (14)` |
| 强制检查更新 | 设置页 →「保存并立即检查」 |
| 退出到别处 | 设置页里有对应入口 |

> ADB 一直是 `unauthorized`（小度拦授权弹窗），**别指望 ADB**，走 FTP。

## 7. 已知坑（踩过的，别再踩）

1. **该固件裁掉了 WebView** —— Fully Kiosk 等所有 kiosk 浏览器方案启动即崩，必须用原生 App。
2. 构建脚本早期用 `|| true` 吞掉编译错误，导致装了旧代码。现在 `build.sh` 是 fail-fast，并且会校验 class 数量（<5 直接失败）。
3. 当贝桌面会抢前台（开机先进看板再被顶掉）—— 已通过卸载当贝 + 关掉其开机启动解决。
4. App 内 **不显示** 应用图标入口（当贝按包去重只显示一个图标），所以设置入口放在界面内（长按左下角 / 齿轮）。
5. `git commit` 在本地偶现"nothing to commit 但提交已生成"的假象，以 `git log -1` 为准。
6. 拉取/轮询 GitHub 状态时，如果命令跑在受限沙箱里，SSH 密钥读取会被拦导致 `ls-remote` 假失败。

## 8. 接下来可以做的

- 把示例作息（`PlanStore.DEFAULT_PLAN`，13 条约 07:00–21:00）换成孩子真实作息
  - 格式：`HH:MM-HH:MM 标题|备注|语音提醒语`，每行一条，`||` 表示空字段
  - 注意：仓库是公开的，代码里别写真实姓名等隐私；也可以只在设备设置页里改（改完不受发版影响）
- 计划支持按星期区分（工作日/周末）
- 语音提醒换成更自然的人声 TTS
- 增加"完成打勾"交互，让看板能记录完成情况
