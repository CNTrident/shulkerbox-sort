# 版本记录与备份

从 2.1.0 起，每次继续开发前保留“修改前”源码备份；每个发布版本构建完成后另存一份发布备份。备份位于 `backups/`，压缩包不包含 Gradle 缓存和临时构建目录。

## 2.1.4

- 全局填充完成后，逐个打开所有非空、默认色、普通潜影盒并调用 Item Scroller 的真实 `sortInventory`。
- 为隐藏容器设置确定的潜影盒内部悬停槽，使 Item Scroller 只整理盒内 27 格。
- 等待 Item Scroller 的可选服务端同步回调、容器 state ID 和服务端槽位更新稳定后才关闭盒子。
- 若一轮盒内整理合堆后产生新的空槽，重新扫描虚拟大表并补充，再执行下一轮逐盒整理。
- 只有完整一轮没有产生新空槽时才恢复快捷栏、合并空盒并报告完成。
- 重复轮次以占用堆数减少作为进展条件，并设置有限上限，避免异常配置导致无限循环。

修改前备份：`shulkerbox_sort-2.1.3-before-per-box-itemscroller-finalize.zip`  
SHA-256：`730EBDB95D4A38359F0D59B9FD6FFFB20400FE3CA319C2BD0C6DE281B49A128A`

发布备份：`shulkerbox_sort-2.1.4-release.zip`  
成品 JAR：`shulkerbox_sort-fabric-26.1.2-2.1.4.jar`  
JAR SHA-256：`CCB6A7270DA1631B49421CF4912F99477D157D8B48DB4F1B3583C2523685FF3A`

## 2.1.3

- 整理运行期间拦截原版玩家背包界面，按背包键时提示“潜影盒整理期间背包已禁用”。
- 只禁用 `InventoryScreen`，不拦截整个按键处理流程，因此移动和切换视角继续可用。
- 潜影盒关闭后只要客户端已同步回玩家菜单，便在同一 tick 开始下一次开盒，去掉每次关盒固定等待 1 tick 的开销。
- 继续保持一次只有一个未确认物品操作，不用多次同 tick 点击换取不安全的表面速度。

修改前备份：`shulkerbox_sort-2.1.2-before-inventory-lock-and-speed.zip`  
SHA-256：`F1E85867B462F9C0702485D90E43C8D5A1EEB8B6F1428AE4D973C167A84BCD2A`

发布备份：`shulkerbox_sort-2.1.3-release.zip`  
成品 JAR：`shulkerbox_sort-fabric-26.1.2-2.1.3.jar`  
JAR SHA-256：`363A574910BBA03F12AD47624771AA44E0F5CBDB3ED7DFB378FB172B681940D5`

## 2.1.2

- 点击开始时明确提示“整理期间请勿移动背包内任何物品”。
- 移除 2.1.1 中打开背包或容器后暂停、关闭后尝试续跑的实验性状态机。
- 不重新禁用游戏按键；切换视角不影响整理，打开其他界面会安全中止当前任务，不再进入无法恢复的暂停状态。

修改前备份：`shulkerbox_sort-2.1.1-before-remove-screen-pause.zip`  
SHA-256：`ABD476E2CD3B9F7F81170948200D912CE99EAB020EC74A47E132BA32C27F06A3`

发布备份：`shulkerbox_sort-2.1.2-release.zip`  
成品 JAR：`shulkerbox_sort-fabric-26.1.2-2.1.2.jar`  
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

发布备份：`shulkerbox_sort-2.1.1-release.zip`  
成品 JAR：`shulkerbox_sort-fabric-26.1.2-2.1.1.jar`  
JAR SHA-256：`7AD413F5180BC0CF342098410BA31CD83183BC1EAAC8BFFB899C7F3137DF5E95`

## 2.1.0

- Quick Shulker 仍建立真实服务端容器，但隐藏自动打开的潜影盒画面，实现后台整理。
- 放宽 Fabric Loader、Quick Shulker 和 Item Scroller 的声明限制；后两者改为软依赖并通过兼容层检测。
- 移除直接 MaLiLib 依赖，使用原版容器数据组件读取潜影盒内容。

修改前备份：`shulkerbox_sort-2.1.0-before-stack-order-fix.zip`  
SHA-256：`4C19B1FF78E6C8187284526690FE76A0263A9D8DCDD3F92F3E033C99705486BB`

## 2.0.1

- 单一物品且 27 格容量全满的潜影盒锁定，不参与全局重排。
- 六组及以上物品优先分页，大宗物品先形成整页，再用小宗物品填充空位。

修改前备份：`shulkerbox_sort-2.0.1-before-headless-mode.zip`  
SHA-256：`99BE85317C30AF10D335B23300D82DE8FB38A4AB5E79598765983E314523A6A3`

## 2.0.0

- 将所有符合条件的原色潜影盒合并为虚拟大表。
- 复用 Item Scroller 当前比较规则排序并按 27 格分页。
- 使用最大权重匹配将页面乱序分配给最适合的物理潜影盒。
- 增加跨盒合并、置换环和快捷栏寄存器执行逻辑。

修改前备份：`shulkerbox_sort-2.0.0-before-bulk-pagination-fix.zip`  
SHA-256：`70A36EB4A9E1FC3AE0529E99984AC2699FD1ACCDCD14A7EF03D175D06BCA75EF`

## 1.3.2

- 完成默认色潜影盒合并整理。
- 使用多个空背包槽批量暂存同一来源盒中的物品。
- 将 14×14 小按钮吸附到原版配方书按钮右侧。

修改前备份：`shulkerbox_sort-1.3.2-before-global-sort.zip`  
SHA-256：`F56F17E55FDE53A3B6ADC9F5B5C4226C5F5A95940D19C3BAB96B9B9D5BFB15`
