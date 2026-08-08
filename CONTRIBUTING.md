# 贡献指南

感谢参与 Shulker Box Sort。

## 开发环境

- Minecraft 26.1.2
- Java 25
- Fabric Loader 0.19.1
- 与 Minecraft 26.1.x 匹配的 Fabric API
- Item Scroller 与 Quick Shulker

## 提交流程

1. Fork 本仓库并从 `main` 创建功能分支。
2. 保持修改范围清晰，不提交 `.gradle/`、`.tools/`、`build/`、`run-client/` 或本地备份。
3. 涉及规划逻辑时补充确定性的单元测试。
4. 执行 `./gradlew clean build` 或 `.\gradlew.bat clean build`。
5. 在 Pull Request 中说明行为变化、安全影响和验证场景。

## 安全要求

- 不直接伪造容器内容或潜影盒数据组件。
- 不在光标持物时关闭容器或主动丢弃物品。
- 新增点击路径必须校验容器、state ID、槽位内容和服务端同步。
- 更改依赖范围时，应避免无必要地锁死补丁版本。

提交贡献即表示你有权提供相关代码，并同意其按仓库的 MIT License 发布。
