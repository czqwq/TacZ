<p align="center">
    <b>Timeless and Classics Guns: Zero</b><br>
    永恒枪械工坊：Zero · Minecraft 1.7.10 非官方移植版
</p>
<hr>

Timeless and Classics Guns Zero（永恒枪械工坊：Zero）是一款为 Minecraft 提供高自由度枪械系统的模组。
本仓库是它的 **Minecraft 1.7.10 / Forge 非官方移植版**，基于上游 1.2.0（Minecraft 1.20.1）源码移植，
不是官方发布，与上游作者无关。上游仓库：[MCModderAnchor/TACZ](https://github.com/MCModderAnchor/TACZ)。

## 项目状态

| Minecraft 版本 | Mod Loader | 状态 |
| --- | --- | --- |
| 1.7.10 | Forge 10.13.4.1614 | 非官方移植 · 维护中 |

主要功能已完成移植，正在持续实机验证与修复细节；上游 1.2.0（Minecraft 1.20.1）为移植基线。

## 玩法简介

- **枪械**：基岩版 JSON 模型驱动的枪械，自带开火、换弹、瞄准、检视、近战等完整的第一/第三人称动画，
  并支持开火模式、点射/蓄力、后坐力、散布、弹道下坠、穿透、击退、爆炸与点燃等参数化表现。
- **配件**：瞄具（含倍率与准星）、枪口、握把、枪托、激光、扩容弹匣，按枪械兼容性装配与拆卸。
- **弹药**：按口径区分的多种弹药，可装入弹匣或弹药盒；铁/金/钻石三级弹药库可大容量存储并在配枪时快速补给。
- **工作台**：枪械工作台合成枪械，弹药装配台与配件工作台分别合成弹药和配件，配方全部由枪包数据定义。
- **靶场与装饰**：人形标靶、标靶车、石像、挂板（含发光变体）、枪械展示柜等，可用于试枪与展示收藏。
- **枪包**：数据驱动的枪包系统，沿用上游 1.20.1 的枪包与资源格式，可加载自定义枪包。
- **联动**：支持 Yes Steve Model Unofficial（YSMU）的第三人称动作联动；本移植还接入了 NEI，可按 R/U 查看枪械、
  弹药、配件的配方与兼容性（需自行安装 NEI）。

## 说明

- 前置：需要 [GTNHLib](https://github.com/GTNewHorizons/GTNHLib)；NEI、YSM 为可选联动。
- 使用上游 1.20.1 格式的枪包；加密/受保护的枪包（如 Arcana 保护包）无法读取，这与上游一致，并非移植缺陷。
- 问题反馈请前往 [Issues](https://github.com/czqwq/TacZ/issues)。

## 作者

- 上游 TACZ 作者：
  - 程序：`286799714`、`TartaricAcid`、`F1zeiL`、`xjqsh`、`ClumsyAlien`
  - 美术：`NekoCrane`、`Receke`、`Pos_2333`
- 1.7.10 移植：`czqwq` 及 [贡献者](https://github.com/czqwq/TacZ/graphs/contributors)

## 鸣谢

- 上游 [Timeless and Classics Zero](https://github.com/MCModderAnchor/TACZ) 全体成员，以及 TACZ Wiki 与社区
- GTNH 团队（GTNHLib 与构建工具链）
- CodeChickenCore / NotEnoughItems
- 所有提供帮助的玩家与你

## License

- 代码：[GNU GPL 3.0](https://www.gnu.org/licenses/gpl-3.0.txt)
- 美术资源：[CC BY-NC-ND 4.0](https://creativecommons.org/licenses/by-nc-nd/4.0/)
