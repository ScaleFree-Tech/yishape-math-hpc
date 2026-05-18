# 向 Maven Central 发布前的检查清单

本文说明在 [Maven Central Requirements](https://central.sonatype.org/publish/requirements/) 语境下，本模块 **通常** 还需要完成的事项。具体以 [Sonatype OSSRH / Central Portal](https://central.sonatype.org/) 当前文档为准。

## 1. 命名空间与账号（一次性）

- 在 Sonatype 注册并申请 **`com.yishape.lab`**（或你们实际使用的 `groupId`）的发布权限。
- 按向导完成 **域名或源码托管** 验证（例如 GitHub 组织/仓库与 `groupId` 的对应关系）。

## 2. `pom.xml` 元数据（本仓库已部分具备）

Central 要求 POM 中具备可读的项目信息，例如：

- **坐标**：`groupId`、`artifactId`、`version`（发布版一般为 **三位版本号**，避免 `-SNAPSHOT` 进入 Central 的正式 release 流程错误环节）。
- **`name`、`description`、`url`**：便于索引与展示。
- **`licenses`**：与仓库根目录 [**`LICENSE`**](../LICENSE) 及实际分发内容一致（本仓库为 **MIT**）。
- **`scm`**：`connection`、`developerConnection`、`url`；发版时 **tag** 建议与 Git 标签一致，勿长期写死为 `HEAD`。
- **`developers`**：至少一名维护者（`id`、`name` 等）；建议补充 **`email`** 或组织 **`url`**，便于 Central/用户联系（可按你们隐私策略填写公共邮箱或工单入口）。

若 `groupId` 指向的父工程在别的仓库，请确保 **本模块 POM 中的 `url`/`scm` 与真实可溯源的源码位置一致**（单仓多模块时尤其注意）。

## 3. 必须随主 JAR 发布的构件

对典型 Java 库，Central 期望一次部署至少包含：

| 构件 | 本仓库情况 |
|------|------------|
| 主 JAR | `maven-jar-plugin` 产出 |
| **`-sources.jar`** | 已配置 `maven-source-plugin`（`attach-sources`） |
| **`-javadoc.jar`** | 已配置 `maven-javadoc-plugin`（`attach-javadocs`） |
| **POM** | 随部署上传 |

发版前在本地执行：

```bash
mvn -DskipTests clean package
```

检查 `target/` 下是否存在 `*-sources.jar`、`*-javadoc.jar`，并确认 **Javadoc 无失败**（本 POM 使用 `doclint=none` 以减轻噪音，仍需保证内容合理）。

## 4. GPG 签名（release 惯例）

本仓库提供 profile **`release-sign`**（`maven-gpg-plugin`，`verify` 阶段签名）。

- 维护者需自用 **GPG 密钥**，并在 `~/.m2/settings.xml` 中配置 **`server` id** 与 OSSRH 凭证（通常与 `distributionManagement` 里 id **`ossrh`** 一致）。
- 典型命令示例：

```bash
mvn clean deploy -Prelease-sign -DskipTests
```

**注意**：GPG 与 passphrase 属敏感信息，仅适合在安全环境中执行；CI 环境需使用 CI 推荐方式注入密钥（例如专用 action / secret）。

## 5. 部署到 OSSRH 暂存库（Staging）

本 POM 已配置 **`distributionManagement`**，使用 **S01 OSSRH**：

- Snapshot：`https://s01.oss.sonatype.org/content/repositories/snapshots/`
- Staging：`https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/`

**发布流程（传统 OSSRH）** 一般为：

1. `mvn deploy`（带签名）将构件上传到 **Staging**。
2. 登录 **Sonatype Nexus**（或你们已迁移到的 **Central Publisher** / Portal）对本次 **Staging Repository** 执行 **Close** → **Release**（或自动 Close/Release，视账号与插件配置而定）。
3. 等待同步到 **repo1.maven.org**（一般数十分钟到数小时）。

若你们已迁移到 **Central Portal** 与新的 **`central-publishing-maven-plugin`**，请按官方文档替换/补充 `deploy` 流程；本仓库 **未** 强制内置该插件，以免与旧账号行为冲突。

可选：为 Staging 增加 **`nexus-staging-maven-plugin`**（`serverId`=`ossrh`，`nexusUrl`=`https://s01.oss.sonatype.org/`），用于命令行 Close/Release 自动化——需按团队习惯单独配置。

## 6. 本机库（native）与合规

本 JAR 可能在 **`META-INF/native-libs/`** 下附带 **预编译动态库**。发布前请确认：

- 这些二进制与 **MIT** 许可及 **第三方依赖**（Rust crate 等）的许可证 **兼容**，并在 README 或独立文档中说明 **来源与构建方式**（便于使用者做合规审计）。
- 不包含私有或不可再分发的闭源组件，除非许可明确允许。

## 7. 发版前自检清单（摘要）

- [ ] Sonatype 上 **`groupId`** 已获批。
- [ ] **`LICENSE`** 与 `pom.xml` 中 `licenses` 一致。
- [ ] **`scm` / `developers` / `url`** 准确；发版时 **`scm.tag`** 与 Git 标签一致。
- [ ] `mvn clean package`：**sources、javadoc、主 JAR** 齐；测试与 Javadoc 策略满足你们质量门禁。
- [ ] `mvn deploy -Prelease-sign`：签名与 OSSRH 凭证正确；Staging **Close/Release** 成功。
- [ ] 在 [Central Search](https://central.sonatype.com/) 或 `repo1` 上能检索到新版本。

更多细节见：**[Publishing to Central](https://central.sonatype.org/publish/publish-guide/)**。
