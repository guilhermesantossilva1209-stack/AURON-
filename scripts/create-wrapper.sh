#!/bin/sh
# Gera o wrapper oficial usando uma instalação LOCAL do Gradle 8.10.2.
# Este script não instala Gradle, não baixa arquivos e não configura o app.
set -eu

required_version=8.10.2
gradle_command=${GRADLE_COMMAND:-gradle}
if ! command -v "$gradle_command" >/dev/null 2>&1; then
    echo "Gradle 8.10.2 não está instalado. Instale-o ou defina GRADLE_COMMAND com o caminho do executável." >&2
    exit 1
fi

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
wrapper_tmp=$(mktemp -d "${TMPDIR:-/tmp}/auron-wrapper.XXXXXX")
trap 'rm -rf "$wrapper_tmp"' EXIT HUP INT TERM

# Um diretório de usuário temporário evita executar init scripts locais.
version_output=$("$gradle_command" --gradle-user-home "$wrapper_tmp/user-home" --version)
installed_version=$(printf '%s\n' "$version_output" | awk '/^Gradle / {print $2; exit}')
if [ "$installed_version" != "$required_version" ]; then
    echo "É necessário Gradle $required_version; encontrado: ${installed_version:-desconhecido}." >&2
    exit 1
fi

cat > "$wrapper_tmp/settings.gradle" <<'EOF'
rootProject.name = 'auron-wrapper-generator'
EOF
cat > "$wrapper_tmp/build.gradle" <<'EOF'
tasks.named('wrapper', Wrapper) {
    gradleVersion = '8.10.2'
    distributionType = Wrapper.DistributionType.BIN
    networkTimeout = 60000
    // Sem HEAD remoto durante a geração. A URL oficial é conhecida e fixa.
    validateDistributionUrl = false
}
EOF

"$gradle_command" --offline --no-daemon \
    --gradle-user-home "$wrapper_tmp/user-home" \
    --project-dir "$wrapper_tmp" wrapper

mkdir -p "$project_dir/gradle/wrapper"
cp "$wrapper_tmp/gradlew" "$project_dir/gradlew"
cp "$wrapper_tmp/gradlew.bat" "$project_dir/gradlew.bat"
cp "$wrapper_tmp/gradle/wrapper/gradle-wrapper.jar" "$project_dir/gradle/wrapper/gradle-wrapper.jar"
# A validação volta a ser habilitada para futuros usos normais do wrapper.
sed 's/^validateDistributionUrl=false$/validateDistributionUrl=true/' \
    "$wrapper_tmp/gradle/wrapper/gradle-wrapper.properties" \
    > "$project_dir/gradle/wrapper/gradle-wrapper.properties"
chmod +x "$project_dir/gradlew"
echo "Wrapper oficial do Gradle 8.10.2 gerado. A primeira compilação poderá baixar dependências."
