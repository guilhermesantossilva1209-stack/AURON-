# Gerar o Gradle Wrapper

O projeto ainda não inclui `gradlew`, `gradlew.bat` nem
`gradle/wrapper/gradle-wrapper.jar`: o download da distribuição oficial não
foi possível neste ambiente. O arquivo `.properties` sozinho não constitui
um wrapper executável. Nenhum JAR substituto foi criado.

Com **Gradle 8.10.2 e JDK 17 instalados**, execute da raiz do projeto:

```sh
./scripts/create-wrapper.sh
```

Se o Gradle não estiver no PATH, use `GRADLE_COMMAND=/caminho/gradle`.
O script verifica a versão e usa a tarefa oficial `wrapper` em um projeto
vazio, temporário e offline. Ele copia os scripts e o JAR reais para este
projeto. Não baixa nem instala ferramentas. A primeira compilação normal
precisa de Android SDK 35 e acesso aos repositórios de dependências.

Também é possível compilar diretamente com o Gradle instalado:

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

O workflow em `.github/workflows/android.yml` instala explicitamente Gradle
8.10.2 no runner e usa `gradle` diretamente, sem depender do wrapper ausente.
Sua execução exige que o projeto seja colocado em um repositório GitHub com
Actions habilitado. O workflow foi preparado, mas não executado neste ambiente.
