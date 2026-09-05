#!/usr/bin/env bash
# GEO-analytics: JDK 25 / Maven 3.9.6 / Node 22 をユーザーローカルに導入する
#
# Why: root 権限を必要とせず、ディストリのパッケージ版に引きずられずに
#      pom.xml と README が要求する版を正確に揃えるため。
#      配置先は ~/.local/share/devtools、PATH は env.sh を経由して
#      .bashrc（対話シェル）と .profile（ログインシェル）の両方から読ませる。
#      Ubuntu の .bashrc は非対話シェルで早期 return するため、
#      片方だけではスクリプト実行時に PATH が通らない。
#
# 使い方: bash scripts/setup-toolchain.sh   （sudo 不要）
set -euo pipefail

JDK_VERSION=25
MAVEN_VERSION=3.9.6   # .mvn/wrapper/maven-wrapper.properties と一致させること
NODE_MAJOR=22

DEVTOOLS="$HOME/.local/share/devtools"
mkdir -p "$DEVTOOLS" "$HOME/.local/bin"
cd "$DEVTOOLS"

echo "==> 1/4 ダウンロードURLを解決"
JDK_URL=$(curl -fsSL "https://api.adoptium.net/v3/assets/latest/${JDK_VERSION}/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse" \
  | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['binary']['package']['link'])")
NODE_VER=$(curl -fsSL https://nodejs.org/dist/index.json \
  | python3 -c "import json,sys;print([x['version'] for x in json.load(sys.stdin) if x['version'].startswith('v${NODE_MAJOR}.')][0])")
MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
echo "    JDK  : $(basename "$JDK_URL")"
echo "    Node : ${NODE_VER}"
echo "    Maven: ${MAVEN_VERSION}"

echo "==> 2/4 ダウンロード"
curl -fsSL -o jdk.tar.gz   "$JDK_URL" &
curl -fsSL -o node.tar.xz  "https://nodejs.org/dist/${NODE_VER}/node-${NODE_VER}-linux-x64.tar.xz" &
curl -fsSL -o maven.tar.gz "$MAVEN_URL" &
wait

echo "==> 3/4 展開"
rm -rf jdk-25 node-22 "maven-${MAVEN_VERSION}"
mkdir -p jdk-25 node-22 "maven-${MAVEN_VERSION}"
tar -xzf jdk.tar.gz   -C jdk-25                  --strip-components=1
tar -xJf node.tar.xz  -C node-22                 --strip-components=1
tar -xzf maven.tar.gz -C "maven-${MAVEN_VERSION}" --strip-components=1
rm -f jdk.tar.gz node.tar.xz maven.tar.gz

echo "==> 4/4 PATH 設定"
cat > "$DEVTOOLS/env.sh" <<EOF
# GEO-analytics 開発ツールチェーン（JDK ${JDK_VERSION} / Maven ${MAVEN_VERSION} / Node ${NODE_MAJOR}）
export JAVA_HOME="\$HOME/.local/share/devtools/jdk-25"
export MAVEN_HOME="\$HOME/.local/share/devtools/maven-${MAVEN_VERSION}"
export NODE_HOME="\$HOME/.local/share/devtools/node-22"
case ":\$PATH:" in
  *":\$JAVA_HOME/bin:"*) ;;
  *) export PATH="\$JAVA_HOME/bin:\$MAVEN_HOME/bin:\$NODE_HOME/bin:\$HOME/.local/bin:\$PATH" ;;
esac
# Why: Maven 3.9.6 の jansi が Java 25 で restricted-method 警告を出すため明示的に許可する
export MAVEN_OPTS="\${MAVEN_OPTS:-} --enable-native-access=ALL-UNNAMED"
EOF

python3 - <<'PY'
import re, pathlib
block = ('\n# >>> GEO-analytics devtools >>>\n'
         '[ -f "$HOME/.local/share/devtools/env.sh" ] && . "$HOME/.local/share/devtools/env.sh"\n'
         '# <<< GEO-analytics devtools <<<\n')
for name in (".bashrc", ".profile"):
    p = pathlib.Path.home() / name
    s = p.read_text() if p.exists() else ""
    s = re.sub(r"\n?# >>> GEO-analytics devtools >>>.*?# <<< GEO-analytics devtools <<<\n?", "\n", s, flags=re.S)
    p.write_text(s.rstrip("\n") + "\n" + block)
PY

echo
echo "完了。新しいシェルを開くか、以下で今のシェルに反映してください:"
echo "  . ~/.local/share/devtools/env.sh"
echo
echo "確認: java -version / mvn -v / node -v"
