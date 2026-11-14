#!/bin/bash
# 学习看板 APK 构建脚本（Windows / Linux / macOS 通用）
set -e
cd "$(dirname "$0")"
ROOT="$PWD"

# ---- 平台适配 ----
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) EXE=".exe" ;;
  *)                    EXE="" ;;
esac
PY="python"
command -v python >/dev/null 2>&1 || PY="python3"

# ---- JDK：优先用目录内的，其次用环境里的 ----
if [ -x "$ROOT/jdk/bin/java$EXE" ]; then
  export JAVA_HOME="$ROOT/jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# ---- SDK：优先用目录内的，其次用 ANDROID_HOME ----
if [ -d "$ROOT/sdk/platforms" ]; then
  SDK="$ROOT/sdk"
elif [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME/platforms" ]; then
  SDK="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT/platforms" ]; then
  SDK="$ANDROID_SDK_ROOT"
else
  echo "!! 找不到 Android SDK（既没有 ./sdk，也没有 ANDROID_HOME）"
  exit 1
fi
export ANDROID_HOME="$SDK"

BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
AJAR="$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
KS_PASS="${KS_PASS:-study123}"
KEY_PASS="${KEY_PASS:-$KS_PASS}"
KS_ALIAS="${KS_ALIAS:-sb}"

echo "== 1. 检查工具 =="
java -version 2>&1 | head -1
echo "build-tools: $BT"
echo "android.jar: $AJAR"
[ -d "$BT" ] || { echo "!! 缺 build-tools"; exit 1; }
[ -f "$AJAR" ] || { echo "!! 缺 android.jar"; exit 1; }

rm -rf build
mkdir -p build/classes build/gen build/dex

echo "== 2. 编译资源 =="
"$BT/aapt2$EXE" compile --dir res -o build/res.zip

echo "== 3. 链接资源与清单 =="
"$BT/aapt2$EXE" link -o build/base.apk \
  -I "$AJAR" \
  --manifest AndroidManifest.xml \
  -R build/res.zip \
  --java build/gen \
  --auto-add-overlay

echo "== 4. 编译 Java =="
find src build/gen -name "*.java" > build/sources.txt
rm -rf build/classes
mkdir -p build/classes
set +e
javac -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$AJAR" \
  -classpath "$AJAR" \
  -d build/classes \
  @build/sources.txt > build/javac.log 2>&1
JC=$?
set -e
if [ "$JC" -ne 0 ]; then
  echo "!! javac 失败（退出码 $JC），错误如下："
  grep -a -i "error" build/javac.log | head -30
  exit 1
fi
CLASSCOUNT=$(find build/classes -name "*.class" | wc -l)
echo "生成 class 文件: $CLASSCOUNT"
if [ "$CLASSCOUNT" -lt 5 ]; then echo "!! 编译失败，class 数量异常"; exit 1; fi

echo "== 5. 转 dex =="
find build/classes -name "*.class" > build/classlist.txt
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$AJAR" --min-api 21 --output build/dex @build/classlist.txt

echo "== 6. 打包 =="
cp build/base.apk build/unsigned.apk
"$PY" -c "
import zipfile, shutil, os
shutil.copy('build/base.apk','build/unsigned.apk')
with zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    z.write('build/dex/classes.dex','classes.dex')
print('dex 已加入 apk:', os.path.getsize('build/unsigned.apk'), 'bytes')
"

echo "== 7. 对齐 =="
"$BT/zipalign$EXE" -f 4 build/unsigned.apk build/aligned.apk

echo "== 8. 签名 =="
if [ ! -f keystore.jks ]; then
  if [ "${REQUIRE_KEYSTORE:-0}" = "1" ]; then
    echo "!! 缺少 keystore.jks，但 REQUIRE_KEYSTORE=1。拒绝自动生成（会造成签名不一致，设备无法覆盖安装）"
    exit 1
  fi
  echo "本地没有 keystore.jks，生成新签名（注意：与设备上已装版本的签名不同，将无法覆盖安装）"
  keytool -genkeypair -v -keystore keystore.jks -alias "$KS_ALIAS" -keyalg RSA \
    -keysize 2048 -validity 10000 -storepass "$KS_PASS" -keypass "$KEY_PASS" \
    -dname "CN=StudyBoard, OU=Home, O=Home, L=City, S=State, C=CN" 2>&1 | tail -1
fi
java -jar "$BT/lib/apksigner.jar" sign --ks keystore.jks --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --out StudyBoard.apk build/aligned.apk

echo "== 9. 验证 =="
java -jar "$BT/lib/apksigner.jar" verify --print-certs StudyBoard.apk 2>&1 | head -3
"$BT/aapt2$EXE" dump badging StudyBoard.apk 2>/dev/null | grep -E "^package:" | head -1
ls -lh StudyBoard.apk | awk '{print "产物:", $NF, $5}'
