# YiShape Math HPC（`yishape-math-hpc`）

在 JVM 上通过 **Java Foreign Function & Memory API（FFM）** 调用与本项目配套的 **Rust 原生库** `yishape_math_rust`，提供稠密/稀疏线性代数等算力的可选加速实现。业务代码请使用 **`com.yishape.lab.math.hpc.YishapeHpc`** 作为入口；矩阵约定为 **行主序 `double[][]`**（与 yishape-math 一致）。

## 要求

- **Java 25+**（使用 `java.lang.foreign`）
- 运行/JUnit 测试时需为 JVM 开启本机访问，例如在 Surefire 中已配置：`--enable-native-access=ALL-UNNAMED`（你自身应用的启动参数需按同样原则配置）

## Maven 坐标

```xml
<dependency>
    <groupId>com.yishape.lab</groupId>
    <artifactId>yishape-math-hpc</artifactId>
    <version>0.5.0</version>
</dependency>
```

若尚未发布到 Maven Central，请先在本地 `mvn install` 或配置指向你们自己的制品库。

## 原生库从哪里来

JAR 内资源路径为 **`META-INF/native-libs/<classifier>/`**，其中 **`classifier`** 与运行时 `HpcNativePlatform` 一致（例如 `windows-x86_64`、`linux-x86_64`、`osx-aarch_64`）。加载顺序见 `HpcNativeLoader`：系统属性 **`yishape.hpc.library.path`**（指向具体库文件）→ Classpath 内上述资源 → `System.loadLibrary("yishape_math_rust")`。

构建时：

1. **预编译**：将各平台产物放到仓库中的 **`libs/`** 下，布局与说明见 **`libs/README.txt`**（含 `osx-*` 与 `mac-*` 等别名行为）。
2. **本机构建**：在默认路径或 `-Dyishape.hpc.rust.crate.dir=` 所指目录中存在 **`Cargo.toml`** 时，`mvn package` 会对**当前平台**执行 `cargo build --release` 并把结果合并进同一 JAR。
3. **仅预编译、无 Rust 源码**：若 `libs/` 已提供至少一个原生文件，即使找不到 crate，也会**自动跳过 cargo**；仍可用 `-DskipNativeBuild=true` 显式禁止 cargo。

常用属性（详见 `pom.xml` 中注释）：

| 属性 | 含义 |
|------|------|
| `-DskipNativeBuild=true` | 不执行 cargo |
| `-Dyishape.hpc.prebuilt.libs.dir=` | 预编译库根目录（默认 `${project.basedir}/libs`） |
| `-Dyishape.hpc.library.path=` | 运行时覆盖库文件路径 |

## 构建与测试

```bash
mvn clean verify
```

发布前需保证 JAR 内已打入至少一个 `*.dll` / `*.so` / `*.dylib`，否则会于 `prepare-package` 阶段失败（可用 `-Dyishape.hpc.skipBundledNativeCheck=true` 跳过检查，**不建议**对发布制品使用）。

## 文档索引

| 文档 | 内容 |
|------|------|
| [`libs/README.txt`](libs/README.txt) | 预编译目录结构、平台 classifier、属性覆盖 |
| [`docs/maven-central.md`](docs/maven-central.md) | 向 **Maven Central** 发布前的检查清单与流程说明 |

## 仓库与协议

- 上游项目见 `pom.xml` 中的 **`url` / `scm`**。
- 许可证：**MIT**（见本仓库根目录 [`LICENSE`](LICENSE)）。
