预编译原生库放置处（构建时自动打进 JAR 的 META-INF/native-libs/）。项目概览与 Maven Central 说明见仓库根目录 README.md、docs/maven-central.md。

目录结构（一级子目录名 = classifier，与 HpcNativePlatform / os-maven-plugin 一致）：

  libs/<classifier>/<native-filename>

示例：

  libs/windows-x86_64/yishape_math_rust.dll
  libs/linux-x86_64/libyishape_math_rust.so
  libs/linux-aarch_64/libyishape_math_rust.so
  libs/osx-x86_64/libyishape_math_rust.dylib
  libs/osx-aarch_64/libyishape_math_rust.dylib

有多少子目录就打多少进同一个 JAR；运行时按当前 OS/CPU 选择对应 classifier。

也可使用下列布局（构建时会映射到上面的 classifier）：
  • 目录名 mac-*、darwin-*、macos-* 会自动映射为 osx-*（与 HpcNativePlatform 一致）。
  • 简短目录：libs/windows 或 libs/win → windows-x86_64（可用 -Dyishape.hpc.prebuilt.short.windows.classifier= 覆盖）；
    libs/linux → linux-x86_64（同理 yishape.hpc.prebuilt.short.linux.classifier）；
    libs/mac、darwin、macos、osx → 默认 osx-aarch_64（同理 yishape.hpc.prebuilt.short.mac.classifier，Intel Mac 可设为 osx-x86_64）。

仅使用预编译、不跑 cargo 时：mvn -DskipNativeBuild=true package

自定义预编译目录：-Dyishape.hpc.prebuilt.libs.dir= 绝对路径

注意：运行时与 os-maven-plugin 使用 osx-*；若你把预编译放在 mac-* / darwin-* / macos-* 目录下，Maven 打包时会自动改为 osx-*。
