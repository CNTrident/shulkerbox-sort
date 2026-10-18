# 贡献指南

感谢参与 Shulker Box Sort。

## 开发环境

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.3（运行时最低声明 0.18.4）
- 与 Minecraft 26.2 匹配的 Fabric API
- 无需 Item Scroller、Quick Shulker 或 MaLiLib

## 提交流程

1. Fork 本仓库并从 `main` 创建功能分支。
2. 保持修改范围清晰，不提交 `.gradle/`、`.tools/`、`build/`、`run-client/` 或本地备份。
3. 涉及规划逻辑时补充确定性的单元测试。
4. 执行 `./gradlew clean build` 或 `.\gradlew.bat clean build`。
5. 在 Pull Request 中说明行为变化、安全影响和验证场景。

## 安全要求

- 所有潜影盒修改在服务端主线程执行；先验证完整组件和物品数量守恒，再提交。
- 不在光标持物时整理，不主动丢弃物品。
- 写入前检查背包快照，异常时恢复并核验原库存。
- 更改依赖范围时，应避免无必要地锁死补丁版本。

提交贡献即表示你有权提供相关代码，并同意其按相应文件的许可证发布：原有代码为 MIT，移植的 `DefaultItemOrder.java` 为 LGPL-3.0-only。修改移植部分时须保留来源与许可，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
