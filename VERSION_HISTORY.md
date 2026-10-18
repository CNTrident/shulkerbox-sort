# 版本记录与备份

从 2.1.0 起，每次继续开发前保留“修改前”源码备份；每个发布版本构建完成后另存一份发布备份。备份位于 `backups/`，压缩包不包含 Gradle 缓存和临时构建目录。

## 3.0.2 服务端版（2026-10-18）

- 将 Item Scroller 26.2-0.32.1 的默认分类与名称比较逻辑内置移植为服务端独立比较器，包含默认工具优先列表、分类顺序和收纳袋/潜影盒后置规则。
- 分类生成不打开界面，不修改客户端共享的创造物品缓存；在服务端启动和数据包重载完成后准备索引。
- 删除 Item Scroller 反射桥、客户端比较器调用、可选推荐项及相关仓库声明；客户端与服务端均无需 Item Scroller、MaLiLib 或 Quick Shulker。
- 命令与按钮统一采用服务端内置默认规则，v3 消息格式不变，但旧版客户端发送的自定义排序偏好不再参与布局。
- 模组名称标注 `(Server)`，中文描述及 README 明确“服务端模组；客户端未安装时可使用 /shulkersort；无需 OP”。命令只整理执行者自己的背包。
- 保留未开启堆叠时完成排序后警告、大宗物品分盒、完整组件隔离和物品守恒事务。
- 内置移植文件按上游 LGPL-3.0-only 保留许可与来源，原有代码仍为 MIT。JAR 附带 LGPL/GPL 文本、第三方说明和移植源码。
- `clean build` 成功，48 项测试通过；验证原生分类、默认工具优先、名称/组件排序、容器后置、旧偏好忽略和命令无 OP 条件。无 Item Scroller/MaLiLib 的专用服务端加载成功，JAR 外部 Masa 类引用为 0。

修改前备份：`backups/shulkerbox_sort-server-3.0.1-before-builtin-sort-20261018.zip`<br>
SHA-256：`3DF54542E74F44FC3DC28388CDA6F447FC418B4BCE285DA06A5AF5E8167F62ED`<br>
成品 JAR：`releases/shulkerbox_sort-server-fabric-26.2-3.0.2.jar`<br>
发布 JAR SHA-256：`10B55996D48205BD84B51FFC2C02F8CE4FAF44B9FA7846F9756D190D96D7EAF6`（保留用户修改后的模组描述，未重新构建）<br>
发布备份：`backups/shulkerbox_sort-server-3.0.2-release.zip`<br>
发布备份 SHA-256：`EA17855CADF2920DD2D6019C893681D8E774E77491339A7CEAF64378E572DDDE`

## 3.0.1 服务端版（2026-10-18）

- 明确客户端安装方式：只在服务端安装即可使用 `/shulkersort`，无需客户端模组与 OP；需要背包小按钮时才在客户端安装。
- 空盒堆叠上限为 1 时继续完成全局物品合并和排序，空盒保持分开，不因未开启堆叠而中止或回滚。
- 正常完成统计后追加黄色“未开启潜影盒堆叠”警告，命令和按钮两条入口一致。
- 使用原版系统消息及中文回退，未安装模组或仍使用 3.0.0 的客户端也能阅读警告；消息协议保持不变，新增中英文翻译。
- 依据实际空盒的堆叠上限判断；无空盒时检测原色盒默认上限，组件已允许堆叠、仅缺少匹配盒的情况不误报。失败时不显示完成警告。
- 新增未开启堆叠仍完成整理、默认规则检查、组件自定义上限与成功/失败消息分支测试。
- JDK 25.0.1 下 `clean build` 成功，41 项自动测试全部通过，0 失败、0 错误；原 3.0.0 JAR 保留且校验值未变化。

修改前备份：`backups/shulkerbox_sort-server-3.0.0-before-stacking-warning-20261018.zip`<br>
SHA-256：`18DB8C504659E19BE94024D88382F8B4D8E9A37574BB5F9C9FAA07BD8DC83ED5`<br>
成品 JAR：`releases/shulkerbox_sort-server-fabric-26.2-3.0.1.jar`<br>
JAR 校验值：`releases/shulkerbox_sort-server-fabric-26.2-3.0.1.jar.sha256`<br>
发布备份：`backups/shulkerbox_sort-server-3.0.1-release.zip`<br>
发布备份 SHA-256：`4D86A2B1651DF635E0160BB41DC0175CB2F80B796DFC5A1F6A469C5032F2FABD`

## 3.0.0 服务端版（2026-10-05）

- 新建独立工作文件夹 `shulkerbox sort-server`，原客户端项目未修改。2.x 以下为原版本记录，历史备份仍在原项目中。
- 增加 Fabric 通用初始化入口，在专用或内置服务端直接读取、规划和写回 `CONTAINER` 组件。
- 保留完整组件合堆、大宗物品分页、同类容量满盒锁定、全局页面匹配及原盒槽位。
- 删除客户端逐盒开关、等待同步、拾取交换、快捷栏寄存器及界面屏蔽流程；不需要空背包槽。
- 增加无需 OP 的 `/shulkersort` 命令，客户端可不装模组。只整理执行玩家自己的 36 格背包。
- 同一 JAR 可选客户端入口保留右上角 14×14 `⇅` 按钮；通过有界请求/结果消息与服务端通信。
- 客户端 Item Scroller 可选，仅读取比较器并发送槽位排序偏好；缺失时及命令入口使用物品 ID 顺序。服务端不需要 Item Scroller、Quick Shulker、MaLiLib。
- 结果副本与原快照进行内容及盒壳组件守恒验证，提交前核对全部 36 格，写入失败尝试恢复并核验原快照。500 ms 请求冷却。
- 空盒堆叠遵守服务端真实最大堆叠组件，并兼容 Carpet `shulkerBoxStackSize`；原版上限为 1 时不强行堆叠。
- 36 项自动测试全部通过，包括真实玩家库存与数据组件、36 个全满盒、随机数量守恒、异常写入回滚、第三方阻止回滚的明确错误提示、网络消息编码及长度限制。
- 专用服务端在无 Quick Shulker、Item Scroller、MaLiLib 环境下成功加载初始化。测试目录未接受 EULA，未启动实际联机世界；玩家按钮与整合包兼容性尚待实机验收。

修改前源码备份：`backups/shulkerbox_sort-2.2.2-client-original.zip`<br>
SHA-256：`0D5C8F754F06AE7BB570B83ACE30FDF2619382A0D5C049B4EFEBE6CC6E4F835B`<br>
成品 JAR：`releases/shulkerbox_sort-server-fabric-26.2-3.0.0.jar`<br>
JAR SHA-256：`3D5ECE76C86BB6283C3A55EF9D2C32ACA2EDF333F256ED8FFE1C7EFDF7603CCC`<br>
发布备份：`backups/shulkerbox_sort-server-3.0.0-release.zip`<br>
发布备份 SHA-256：`74BACC52D63E406FE72D19DB5ABEF65948D41FC9404E9F4D816D6C0965711EBF`

## 2.2.2

- 根据实际背包界面截图，将按钮从面板外侧调整到面板内部右上角、合成输出槽正上方的空白区域。
- 按钮相对背包面板左上角固定在 `(154, 3)`，窗口或 GUI 缩放变化时继续跟随面板。
- 移除 2.2.1 的双潜影盒像素图标，恢复原来的 `⇅` 图标；处理中仍显示 `…`。
- 按钮尺寸继续保持 14×14，点击行为、禁用状态和提示文本不变。
- Java 25 下执行 `clean build`，21 个规划器与扫描器测试全部通过。

修改前备份：`shulkerbox_sort-2.2.1-before-button-placement-revert.zip`<br>
SHA-256：`32D90D291FD7F21606A515A12AE3B1A61471A921C82720BE89A416EEE7EAE5CE`

发布备份：`shulkerbox_sort-2.2.2-release.zip`<br>
发布备份 SHA-256：`C594CD74A82D6F8A54B700F4DE47DECF6C988C6EA8795A1F0EA4751ADD866D3B`<br>
成品 JAR：`shulkerbox_sort-fabric-26.2-2.2.2.jar`<br>
JAR SHA-256：`5404AC572201D4BB94F2E69BCB9B4D441323777261CBAD7F8BDD62A83103C864`

后续开发前备份（评估移除 Item Scroller 前置）：`shulkerbox_sort-2.2.2-before-remove-itemscroller.zip`<br>
SHA-256：`40A8747C7370AF5F25552C9D3962A0D83626554730182F66DF55AB89565B79F0`

## 2.2.1

- 将整理按钮从配方书按钮右侧移动到背包面板右上角外侧，并保持 14×14 尺寸不变。
- 图标改为两个错位重叠的紫色潜影盒，并用一对方向相反的斜向箭头表示跨盒交换。
- 图标使用 GUI 像素绘制，不依赖缩放贴图；按钮禁用时整体变暗，仍保留原有提示文本和无障碍叙述。
- 新增 `topPos` 访问器，使按钮在 GUI 缩放或窗口尺寸变化时持续吸附于背包右上角。
- Java 25 下执行 `clean build`，21 个规划器与扫描器测试全部通过。

修改前备份：`shulkerbox_sort-2.2.0-before-top-right-icon.zip`<br>
SHA-256：`8C14D4397D45D0B768490DDCE691E925146AE97E7748430E95CAF59B22D23AAC`

发布备份：`shulkerbox_sort-2.2.1-release.zip`<br>
发布备份 SHA-256：`25C189588121BD33E76012AE79652D02A7507CF7816114B2ADF4DF41E1E4758D`<br>
成品 JAR：`shulkerbox_sort-fabric-26.2-2.2.1.jar`<br>
JAR SHA-256：`D99D45C99751BC982E4346AB51F7D9E7FCA22EDBD14E509912472E8A0CFE31C7`

## 2.2.0

- 将目标 Minecraft 版本从 26.1.2 升级到 26.2，模组声明范围调整为 `>=26.2 <26.3`。
- 构建环境升级到 Fabric Loader `0.19.3`、Fabric API `0.156.0+26.2` 和 Item Scroller `0.32.1`。
- Quick Shulker 建议版本升级为 `3.0.2`，并核对 `3.0.2-26.2` 仍提供 `ClientUtil.CheckAndSend(ItemStack, int)`。
- 适配 26.2 GUI 重组：当前界面读取和切换改走 `Minecraft.gui`，后台隐藏容器的 Mixin 改为注入 `Gui.setScreen`。
- 保持 Fabric Loader 的运行时最低版本声明为 `>=0.18.4`，避免无必要地锁死整合包中的补丁版本。
- 使用 Item Scroller `0.32.1` 核对比较器、真实整理入口和同步等待字段仍与兼容层匹配。
- Java 25 下执行 `clean build`，全部规划器与扫描器测试通过。

修改前备份：`shulkerbox_sort-2.1.4-before-minecraft-26.2.zip`<br>
SHA-256：`FAEF369ED10D0E27D69ADF37D79689D33450DA2A7E510DB2512EA22A4068219F`

发布备份：`shulkerbox_sort-2.2.0-release.zip`<br>
发布备份 SHA-256：`9D01A2F7D5BC6DF92CAA066678775190B4519EFDB535C4F4599D6A7EF677B94A`<br>
成品 JAR：`shulkerbox_sort-fabric-26.2-2.2.0.jar`<br>
JAR SHA-256：`FCAB6BAE0345F15A282553D6F54B382F131CAD723E2A0D60D3154C60CA316B9B`

## 2.1.4

- 全局填充完成后，逐个打开所有非空、默认色、普通潜影盒并调用 Item Scroller 的真实 `sortInventory`。
- 为隐藏容器设置确定的潜影盒内部悬停槽，使 Item Scroller 只整理盒内 27 格。
- 等待 Item Scroller 的可选服务端同步回调、容器 state ID 和服务端槽位更新稳定后才关闭盒子。
- 若一轮盒内整理合堆后产生新的空槽，重新扫描虚拟大表并补充，再执行下一轮逐盒整理。
- 只有完整一轮没有产生新空槽时才恢复快捷栏、合并空盒并报告完成。
- 重复轮次以占用堆数减少作为进展条件，并设置有限上限，避免异常配置导致无限循环。

修改前备份：`shulkerbox_sort-2.1.3-before-per-box-itemscroller-finalize.zip`<br>
SHA-256：`730EBDB95D4A38359F0D59B9FD6FFFB20400FE3CA319C2BD0C6DE281B49A128A`

发布备份：`shulkerbox_sort-2.1.4-release.zip`<br>
成品 JAR：`shulkerbox_sort-fabric-26.1.2-2.1.4.jar`<br>
JAR SHA-256：`CCB6A7270DA1631B49421CF4912F99477D157D8B48DB4F1B3583C2523685FF3A`

## 2.1.3

- 整理运行期间拦截原版玩家背包界面，按背包键时提示“潜影盒整理期间背包已禁用”。
- 只禁用 `InventoryScreen`，不拦截整个按键处理流程，因此移动和切换视角继续可用。
- 潜影盒关闭后只要客户端已同步回玩家菜单，便在同一 tick 开始下一次开盒，去掉每次关盒固定等待 1 tick 的开销。
- 继续保持一次只有一个未确认物品操作，不用多次同 tick 点击换取不安全的表面速度。

修改前备份：`shulkerbox_sort-2.1.2-before-inventory-lock-and-speed.zip`<br>
SHA-256：`F1E85867B462F9C0702485D90E43C8D5A1EEB8B6F1428AE4D973C167A84BCD2A`

发布备份：`shulkerbox_sort-2.1.3-release.zip`<br>
成品 JAR：`shulkerbox_sort-fabric-26.1.2-2.1.3.jar`<br>
JAR SHA-256：`363A574910BBA03F12AD47624771AA44E0F5CBDB3ED7DFB378FB172B681940D5`

## 2.1.2

- 点击开始时明确提示“整理期间请勿移动背包内任何物品”。
- 移除 2.1.1 中打开背包或容器后暂停、关闭后尝试续跑的实验性状态机。
- 不重新禁用游戏按键；切换视角不影响整理，打开其他界面会安全中止当前任务，不再进入无法恢复的暂停状态。

修改前备份：`shulkerbox_sort-2.1.1-before-remove-screen-pause.zip`<br>
SHA-256：`ABD476E2CD3B9F7F81170948200D912CE99EAB020EC74A47E132BA32C27F06A3`

发布备份：`shulkerbox_sort-2.1.2-release.zip`<br>
成品 JAR：`shulkerbox_sort-fabric-26.1.2-2.1.2.jar`<br>
JAR SHA-256：`8FF45524DD05B1B45B058CA93C30AF5AF85751791F4C63F924F9BF0B40EAC9BC`

## 2.1.1

- 修复同类物品不满组因数量排序而逐格向后移动的问题。
- 物品超过一盒时，从尾部来源直接填满靠前的不满组，并优先生成由满组组成的整页。
- 物品不超过一盒、合并又不能减少占用格数时，保持同类堆的首次出现顺序，不为把不满组移到末格而搬运。
- 保持六组及以上物品的“大宗优先分页”；在不增加非空盒数量的前提下尽量不拆分大宗物品。
- 继续使用所有初始空背包槽作为并行中转空间，依赖关系不冲突时一次暂存多组物品。
- 不再整体拦截游戏按键，允许打开背包、打开其他容器和切换视角。
- 玩家界面打开期间暂停后台点击和超时计时；背包内容未变化时关闭界面后重新打开任务盒继续，背包内容变化时安全中止。
- 新增“不满组直供”和“单盒不作无效归位”回归测试。

发布备份：`shulkerbox_sort-2.1.1-release.zip`<br>
成品 JAR：`shulkerbox_sort-fabric-26.1.2-2.1.1.jar`<br>
JAR SHA-256：`7AD413F5180BC0CF342098410BA31CD83183BC1EAAC8BFFB899C7F3137DF5E95`

## 2.1.0

- Quick Shulker 仍建立真实服务端容器，但隐藏自动打开的潜影盒画面，实现后台整理。
- 放宽 Fabric Loader、Quick Shulker 和 Item Scroller 的声明限制；后两者改为软依赖并通过兼容层检测。
- 移除直接 MaLiLib 依赖，使用原版容器数据组件读取潜影盒内容。

修改前备份：`shulkerbox_sort-2.1.0-before-stack-order-fix.zip`<br>
SHA-256：`4C19B1FF78E6C8187284526690FE76A0263A9D8DCDD3F92F3E033C99705486BB`

## 2.0.1

- 单一物品且 27 格容量全满的潜影盒锁定，不参与全局重排。
- 六组及以上物品优先分页，大宗物品先形成整页，再用小宗物品填充空位。

修改前备份：`shulkerbox_sort-2.0.1-before-headless-mode.zip`<br>
SHA-256：`99BE85317C30AF10D335B23300D82DE8FB38A4AB5E79598765983E314523A6A3`

## 2.0.0

- 将所有符合条件的原色潜影盒合并为虚拟大表。
- 复用 Item Scroller 当前比较规则排序并按 27 格分页。
- 使用最大权重匹配将页面乱序分配给最适合的物理潜影盒。
- 增加跨盒合并、置换环和快捷栏寄存器执行逻辑。

修改前备份：`shulkerbox_sort-2.0.0-before-bulk-pagination-fix.zip`<br>
SHA-256：`70A36EB4A9E1FC3AE0529E99984AC2699FD1ACCDCD14A7EF03D175D06BCA75EF`

## 1.3.2

- 完成默认色潜影盒合并整理。
- 使用多个空背包槽批量暂存同一来源盒中的物品。
- 将 14×14 小按钮吸附到原版配方书按钮右侧。

修改前备份：`shulkerbox_sort-1.3.2-before-global-sort.zip`<br>
SHA-256：`F56F17E55FDE53A3B6ADC9F5B5C4226C5F5A95940D19C3BAB96B9B9D5BFB15`
