# MarCore

---

## Dependencies

### `JDK` 11 or newer

We recommend using Java 11 or later `LTS` releases.
While Chisel itself works with Java 8,
our preferred build tool Mill requires Java 11.
You can install the `JDK` as your operating system recommends,
or use the `prebuilt` binaries from [`Adoptium`](https://adoptium.net/)
(formerly `AdoptOpenJDK`).

### `SBT` or `mill`

`SBT` is the most common build tool in the Scala community.
You can download it [here](https://www.scala-sbt.org/download.html).
`mill` is another Scala/Java build tool without obscure DSL like `SBT`.
You can download it [here](https://github.com/com-lihaoyi/mill/releases)

### `Verilator`

The test with `svsim` needs `Verilator` installed.
See `Verilator` installation instructions [here](https://verilator.org/guide/latest/install.html).

## How to get started

### Install Dependencies

#### Install git hooks

```bash
make install-hooks
```

#### Install mill

```bash
sudo sh -c "curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.12/0.11.12 > /usr/local/bin/mill && chmod +x /usr/local/bin/mill"
mill
```

Verify your version, make sure the output is same

```bash
mill --version
  Mill Build Tool version 0.11.12
  Java version: 18.0.2-ea, vendor: Private Build, runtime: /usr/lib/jvm/java-18-openjdk-amd64
  Default locale: zh_CN, platform encoding: UTF-8
  OS name: "Linux", version: 6.8.0-52-generic, arch: amd64
```

#### Install `Verilator`

Follow the installation step in [here](https://verilator.org/guide/latest/install.html).
You need install `5.008` version by git.

Verify your version, make sure the output is same

```bash
verilator -version
  Verilator 5.008 2023-03-04 rev v5.008
```

---

# 开发者赠言

## 基于Chisel的开发流程

建议使用Nvim进行开发控制。在Mason中下载scala相关控件，并且基于<https://get-coursier.io/docs/cli-install> 下载cs，通过cs下载相关更多控件即可。

使用Nvim及其脚本开发可以强制用户自行配置环境（不是）。

## 协作开发流程

在参与项目协作时，请按照以下步骤进行操作：

1. `Fork` 和 `Clone` 仓库
首先， `Fork` [中心仓库](TODO)到自己的 `GitHub` 账号下，并 `Clone` 到本地环境。在后续开发中，基于开发分支进行协作，所有的 `Pull Request(PR)` 和合并操作都将在该分支上进行。

2. 提出想法并讨论（建议）

在正式实现前，可以在 `QQ` 群中提出自己的想法，与其他开发者进行讨论或协商，以确保思路清晰并避免重复开发。

3. 本地实现与测试

根据讨论结果，在本地进行功能的开发与实现，并确保经过充分的本地测试，确保代码质量和功能的正确性。

4. 同步中心仓库并解决冲突

在提交 `PR` 之前，确保自己的 `Fork` 仓库与中心仓库保持同步。可以通过以下步骤实现：

* 从中心仓库拉取最新代码，并在本地进行 `Rebase`：

``` bash
git pull --rebase
 ```

* 如果存在冲突，解决冲突并重新测试代码。

5. 推送到 `Fork` 仓库并提交 `PR`

将修改后的代码推送到自己 `Fork` 的仓库：

``` bash
git push
```

随后，在 `GitHub` 上提交 `Pull Request` 到中心仓库的 `dev_la` 分支，并等待代码审查和合并。确保协作开发的有序性和代码库的一致性。

## Commit 提交信息标准手册

### 提交信息格式

```plaintext
<type>(<scope>): <subject>

<body> (可选)

<footer> (可选，通常用于 BREAKING CHANGE 或 关联 issue)
```

* <type>：提交的类型（必填），如 `feat`、 `fix`、 `refactor` 等。
* <scope>：影响的范围（可选），如 `core`、 `ui`、 `auth` 等。
* <subject>：简要描述（必填），建议不超过 `72` 个字符，使用动词原型（如 "`add`"、"`fix`"）。
* <body>：详细描述（可选），可分多行，说明为什么要做这个改动、解决了什么问题、实现方式等。
* <footer>：额外信息（可选），包括 BREAKING CHANGE（破坏性变更）或 关联 issue（Fixes #123）。

### Commit 类型约定

* `feat` 🌟 新增功能（ `feature`），比如新增 `API`、 `UI` 组件
* `fix` 🐛 修复 `Bug`，比如修复崩溃、逻辑错误
* `refactor` 🔨 重构，即不影响功能的代码优化
* `perf` 🚀 性能优化，如优化算法、减少耗时
* `docs` 📚 文档修改，如修改 `README`、注释
* `style` 🎨 代码格式调整（无功能变更），如空格、分号、 `Lint` 修复
* `test` ✅ 添加或修改测试，如单元测试、集成测试
* `chore` 🏡 杂项，如构建系统、 `CI/CD` 变更
* `build` 🏗 构建相关，如修改 `package.json`、 `Webpack` 配置
* `ci` 🤖 CI/CD 相关，如 GitHub Actions、Jenkins
* `revert` ⏪ 撤销提交，用于回滚某次更改
* `breaking` ⚠️ 重大变更，如 `API` 变更导致不兼容

### 🔥 最佳实践

* 保持一致的格式：所有 commit 都应遵循 type(scope): subject 格式。
* 简明扼要：subject 不超过 72 字符，body 可以详细描述。
* 使用动词原型：如 "add"、"fix"、"refactor"，避免 "added"、"fixed"。
* 关联 Issue：如 Fixes #123，让 GitHub/GitLab 自动关闭问题。
* 不要提交无意义信息：如 "update"、"fix bug" 这样的信息毫无价值。
* 如果是 WIP（Work in Progress）：可以用 wip: 前缀，但正式合并前应 squash 或改成规范格式。
