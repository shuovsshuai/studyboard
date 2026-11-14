# 学习看板（小度在家1S · NV2001）

一个不依赖 WebView 的原生 Android 学习计划看板，跑在刷过机的小度在家1S 上，作为默认桌面常驻显示。

## 现在的工作流

```
任意一台电脑
  git clone → 改代码 → 改版本号 → git push
        ↓
  GitHub Actions 自动编译 + 发布 Release（StudyBoard.apk + update.json）
        ↓
  小度开机 / 每 6 小时请求 releases/latest/download/update.json
        ↓
  versionCode 变大 → 下载 → su pm install -r → 系统自动拉起新版
```

**你不需要在任何一台电脑上装 JDK / Android SDK**，编译在云端完成。

## 改版本号（每次发版必做）

`AndroidManifest.xml`：

```xml
android:versionCode="13"     <!-- 必须递增，小数不能回退 -->
android:versionName="2.1"    <!-- 只给人看，Release 标签用它 -->
```

`versionCode` 没变大，设备就不会更新。

## 首次配置（只做一次）

### 1. 建仓库

```bash
cd android-build
git init
git add -A
git commit -m "init: study board"
git remote add origin git@github.com:<你的用户名>/studyboard.git
git push -u origin main
```

### 2. 加两个 Secret

仓库 → Settings → Secrets and variables → Actions → New repository secret：

| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | 见下方命令输出的整行 |
| `KS_PASS` | `study123` |

生成 base64（在 android-build 目录下执行）：

```bash
python -c "import base64;print(base64.b64encode(open('keystore.jks','rb').read()).decode())"
```

> **keystore.jks 已被 .gitignore 排除，绝对不要弄丢。** 丢了之后新包签名不一致，只能卸载重装，
> 设备上的计划表和打勾记录会全部丢失。建议额外存一份到网盘。

### 3. 告诉 App 更新源在哪

`Updater.java` 里的 `DEFAULT_BASE`，或者在设备的「家长设置 → 自动更新」里直接填：

```
https://github.com/<你的用户名>/studyboard/releases/latest/download/
```

`/releases/latest/download/` 这个固定路径永远指向最新一次正式发布，不用改。

## 本地编译（可选）

需要目录内有 `jdk/` 和 `sdk/`，或者环境变量里有 `JAVA_HOME` + `ANDROID_HOME`：

```bash
bash build.sh
```

产物：`StudyBoard.apk`。Windows 与 Linux 同一套脚本。

## 装机（手动，仅首次或救砖时）

1. 小度上打开 ES 文件浏览器 → 菜单 → 网络 → 远程管理器 → 打开，记下 `ftp://192.168.x.x:3721/`
2. 电脑上传：`python -c "from ftplib import FTP;f=FTP();f.connect('192.168.2.127',3721);f.login('anonymous','anonymous');f.storbinary('STOR /Download/StudyBoard.apk',open('StudyBoard.apk','rb'))"`
3. 小度上 ES → Download → 点 APK → 安装

## 代码结构

| 文件 | 作用 |
|---|---|
| `MainActivity.java` | 主界面：时钟 + 倒计时环 + 今日计划时间轴，右下角齿轮进设置 |
| `CountdownRing.java` | 自定义 View，手绘倒计时圆环 |
| `PlanStore.java` | 计划/密码/偏好存储（SharedPreferences） |
| `SettingsActivity.java` | 家长设置：计划表、提醒话术、桌面接管、应用管理、自动更新 |
| `AppListActivity.java` | 应用管理：root 停用/启用应用 |
| `Updater.java` | 自更新：拉 update.json → 下载 → root 静默安装 |
| `BootReceiver.java` | 开机自启 + 抢前台兜底 |

## 已知约束

- 小度的定制系统裁掉了 WebView，任何「浏览器型 kiosk」方案都跑不起来，所以是原生实现。
- 设备是 32 位 Android 8.1、1G 内存，注意内存占用，避免大图和大对象。
- 自动更新会在安装瞬间重启看板（约 3~5 秒），因为是默认桌面，系统会自动重新拉起。
- GitHub 在国内网络下偶有不稳，更新失败只会静默跳过，不影响正常使用。
