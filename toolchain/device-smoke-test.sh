#!/usr/bin/env bash
# Pack the toolchain for Android, push it to a connected device and rebuild the
# exact runtime layout the APK will use (native libs + symlink farm + assets
# copied into the private dir), then smoke-test the tools the way codex-core
# will invoke them (bash + GNU userland (coreutils/grep/find/procps), git, rg,
# curl, python, bun plus the analysis/edit tools: clang-format, diff/patch,
# zstd, yq, shfmt, gofmt, ruff, ast-grep, fd).
#
# The APK toolchain package is produced by Gradle (`:toolchain:packJniLibs`).
# Usage: ./device-smoke-test.sh [abi]
set -euo pipefail

ABI="${1:-arm64-v8a}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE=/data/local/tmp/codex-native
ASSETS=/data/local/tmp/codex-assets
FARM=/data/local/tmp/codex-root

command -v adb >/dev/null || { echo "adb not found" >&2; exit 1; }

(cd "$ROOT/.." && ./gradlew --console=plain :toolchain:packJniLibs)

echo ">> push jniLibs/assets -> $NATIVE / $ASSETS"
adb shell "rm -rf $NATIVE $ASSETS $FARM"
adb push "$ROOT/out/android/$ABI/jniLibs" "$NATIVE" >/dev/null
adb push "$ROOT/out/android/$ABI/assets/toolchain" "$ASSETS" >/dev/null
adb push "$ROOT/out/android/$ABI/native-manifest.txt" "$NATIVE/native-manifest.txt" >/dev/null

adb shell "sh -s" <<EOS
set -e
NATIVE=$NATIVE
ASSETS=$ASSETS
FARM=$FARM

# 1. data files are copied straight into the private dir (like assets
#    unpacking inside the app) ...
cp -R "\$ASSETS/." "\$FARM/"

# 2. ... then executables/dlopen-ables become symlinks into the native lib dir.
while IFS='|' read -r kind rel val; do
    case "\$kind" in
        abi|data) continue ;;
    esac
    mkdir -p "\$FARM/\$(dirname "\$rel")"
    if [ -e "\$FARM/\$rel" ]; then rm -rf "\$FARM/\$rel"; fi
    if [ "\$kind" = file ]; then
        ln -s "\$NATIVE/\$val" "\$FARM/\$rel"
    else
        ln -s "\$val" "\$FARM/\$rel"
    fi
done < "\$NATIVE/native-manifest.txt"

export PATH="\$FARM/bin:/system/bin"
export GIT_EXEC_PATH="\$FARM/libexec/git-core"
export GIT_TEMPLATE_DIR="\$FARM/share/git-core/templates"
export TMPDIR="\$FARM/tmp"
export PYTHONHOME="\$FARM"
export MAGIC="\$FARM/share/misc/magic"
export PYTHONDONTWRITEBYTECODE=1
export CURL_CA_BUNDLE="\$FARM/share/cacert.pem"
export SSL_CERT_FILE="\$FARM/share/cacert.pem"
export GIT_SSL_CAINFO="\$FARM/share/cacert.pem"
mkdir -p "\$TMPDIR"

echo "[layout] bin: \$(ls "\$FARM/bin" | tr '\n' ' ')"
echo "[bash] \$("bash" -c 'echo \$BASH_VERSION')"
bash -c 'cat <<EOF
[bash] heredoc ok
EOF'

WORK="\$FARM/work"
rm -rf "\$WORK" && mkdir -p "\$WORK" && cd "\$WORK"

git init -q repo
cd repo
git config user.email test@example.com
git config user.name Test

echo one > a.txt
git add a.txt
git commit -q -m init
echo "[git] log: \$(git log --oneline | head -1)"
echo "[git] root: \$(git rev-parse --show-toplevel)"

echo two >> a.txt
echo "[git] status: \$(git status --short | tr '\n' ' ')"
echo "[git] diff lines: \$(git diff -- a.txt | wc -l)"

# shell-script command from libexec (exercises the /system/bin/sh shebang)
git stash -q
echo "[git] stash list: \$(git stash list | wc -l)"
git stash pop -q

git worktree add -q --detach "\$WORK/wt" HEAD
echo "[git] worktree head: \$(git -C "\$WORK/wt" rev-parse --short HEAD)"

cat > "\$TMPDIR/c.patch" <<'PATCH'
diff --git a/c.txt b/c.txt
new file mode 100644
index 0000000..ce01362
--- /dev/null
+++ b/c.txt
@@ -0,0 +1 @@
+hello
PATCH
git apply "\$TMPDIR/c.patch"
echo "[git] applied c.txt: \$(cat c.txt)"

if command -v rg >/dev/null 2>&1; then
    echo "[rg] match: \$(rg -n hello c.txt)"
fi

echo "[coreutils] \$(ls --version | head -1 | cut -d' ' -f1,4) / \$(date +%s) / \$(stat -c %s c.txt) bytes"
echo "[coreutils] \$(echo abc | tr a-z A-Z) \$(printf 'a\nb\n' | sort -r | tr '\n' ' ')"
echo "[sed] \$(echo abc | sed 's/a/A/') / \$(sed --version 2>/dev/null | head -1 | cut -d' ' -f1,4)"
echo "[awk] \$(echo '1 2' | awk '{print \$2 \$1}') / \$(awk --version 2>/dev/null | head -1)"
echo "[grep] \$(grep --version | head -1 | cut -d' ' -f1,4) / \$(echo hello | grep -E '^h.*o$')"
echo "[find] \$(find --version | head -1 | cut -d' ' -f1,4) / \$(find . -name c.txt) / \$(printf 'x\0y' | xargs -0 -n1 echo | tr '\n' ' ')"
echo "[tree] \$(tree -a . | head -1)"
echo "[which] \$(which rg)"
echo "[ps] \$(ps --version | head -1) / \$(ps -o pid,comm | wc -l) lines"
echo "[bc] \$(echo '2^10' | bc)"
echo "[xxd] \$(printf hi | xxd -p)"
echo "[tar] \$(tar --version | head -1)"
echo "[gzip] \$(echo hi | gzip -c | gzip -dc)"
echo "[bzip2] \$(echo hi | bzip2 -c | bzip2 -dc)"
echo "[xz] \$(echo hi | xz -c | xz -d -c)"
echo "[unzip] \$(unzip -v 2>&1 | head -1 | cut -c1-30)"
printf 'zipped' > "\$TMPDIR/z.txt"
(cd "\$TMPDIR" && zip -q -FS z.zip z.txt)
echo "[zip] \$(unzip -p "\$TMPDIR/z.zip" z.txt)"
echo "[7zz] \$(7zz 2>&1 | sed -n 2p)"
printf '{"a":1}\n' > "\$TMPDIR/x.json"
echo "[jq] \$(jq -r .a "\$TMPDIR/x.json")"
echo "[file] \$(file "\$FARM/bin/bash" | cut -d: -f2-)"
echo "[sqlite3] \$(sqlite3 :memory: 'select 1+1;')"
echo "[make] \$(make --version | head -1)"
echo "[readelf] \$(readelf -h "\$FARM/bin/bash" | grep Machine | tr -s ' ')"
echo "[objdump] \$(objdump --version | head -1)"
echo "[nm/strings] \$(nm -D "\$FARM/bin/bash" 2>/dev/null | head -1 | cut -c1-24) / \$(strings "\$FARM/bin/bash" | head -1 | cut -c1-20)"
echo "[ssh] \$(ssh -V 2>&1)"
if command -v uv >/dev/null 2>&1; then
    echo "[uv] \$(uv --version)"
fi

if command -v curl >/dev/null 2>&1; then
    echo "[curl] \$(curl --version | head -1)"
    if curl -fsS --max-time 20 https://example.com >/dev/null 2>&1; then
        echo "[curl] https ok"
    else
        echo "[curl] https FAILED (offline?)"
    fi
fi
if command -v openssl >/dev/null 2>&1; then
    echo "[openssl] \$(openssl version)"
fi

if command -v python3 >/dev/null 2>&1; then
    cat > "\$TMPDIR/pytest.py" <<'PY'
import sys, ssl, zlib, ctypes, hashlib, json, decimal, csv, sqlite3
print(f"[python] {sys.version.split()[0]} home={sys.prefix}")
print(f"[python] ssl={ssl.OPENSSL_VERSION} zlib={zlib.ZLIB_VERSION}")
print(f"[python] sha256={hashlib.sha256(b'x').hexdigest()[:16]} json={json.dumps({'ok': True})}")
print(f"[python] ctypes sizeof(c_int)={ctypes.sizeof(ctypes.c_int)} sqlite={sqlite3.sqlite_version}")
PY
    python3 "\$TMPDIR/pytest.py" || echo "[python] FAILED"
fi

if command -v bun >/dev/null 2>&1; then
    echo "[bun] \$(bun --version) (\$(bun -e 'console.log(process.platform + " " + process.arch)'))"
    echo "[bun] spawn sh: \$(bun -e 'const{execFileSync}=require("child_process");process.stdout.write(execFileSync("sh",["-c","echo ok"]).toString().trim())')"
    if command -v bunx >/dev/null 2>&1; then
        echo "[bunx] \$(bunx --version 2>&1 | head -1)"
    fi
fi

# --- analysis / edit tools ---
if command -v git >/dev/null 2>&1; then
    if git ls-remote https://github.com/octocat/Hello-World HEAD >/dev/null 2>&1; then
        echo "[git] https ok"
    else
        echo "[git] https FAILED (offline or CA bundle?)"
    fi
fi
if command -v diff >/dev/null 2>&1; then
    echo "[diff] \$(diff --version | head -1)"
fi
if command -v patch >/dev/null 2>&1; then
    echo "[patch] \$(patch --version | head -1)"
    printf 'one\n' > "\$TMPDIR/patch-in.txt"
    printf 'two\n' > "\$TMPDIR/patch-new.txt"
    diff -u "\$TMPDIR/patch-in.txt" "\$TMPDIR/patch-new.txt" > "\$TMPDIR/p.diff" || true
    patch -s "\$TMPDIR/patch-in.txt" < "\$TMPDIR/p.diff"
    echo "[patch] applied: \$(cat "\$TMPDIR/patch-in.txt")"
fi
if command -v zstd >/dev/null 2>&1; then
    echo "[zstd] \$(echo hi | zstd -q -c | zstd -q -dc) (\$(zstd --version | head -1))"
fi
if command -v clang-format >/dev/null 2>&1; then
    printf 'int  main(){return 0;}\n' > "\$TMPDIR/fmt.c"
    echo "[clang-format] \$(clang-format "\$TMPDIR/fmt.c")"
fi
if command -v yq >/dev/null 2>&1; then
    echo "[yq] \$(printf 'a: 1\n' | yq -r .a)"
fi
if command -v shfmt >/dev/null 2>&1; then
    echo "[shfmt] \$(printf 'if [ 1 = 1 ];then echo a;fi\n' | shfmt - | tr '\n' ';')"
fi
if command -v gofmt >/dev/null 2>&1; then
    echo "[gofmt] \$(printf 'package main\nfunc  f(){}\n' | gofmt | tail -1)"
fi
if command -v ruff >/dev/null 2>&1; then
    echo "[ruff] \$(ruff --version)"
fi
if command -v ast-grep >/dev/null 2>&1; then
    echo "[ast-grep] \$(ast-grep --version | head -1) / sg: \$(sg --version 2>&1 | head -1)"
fi
if command -v fd >/dev/null 2>&1; then
    echo "[fd] \$(fd --version)"
fi

echo SMOKE_OK
EOS
