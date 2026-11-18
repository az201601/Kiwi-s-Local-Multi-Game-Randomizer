# Harvest Moon GB Randomizer — Java 补丁工具（第一步：爷爷开场白）

用 Java 修改牧场物语 GB（Harvest Moon GB USA）的爷爷开场信件，把原版剧情台词改成欢迎玩家进入 **RANDOMIZER** 的开场白。

原版开场从 `{PLAYER} WAKE UP {PLAYER}` 开始（`{PLAYER}` 是玩家名字占位符），共 22 个对话页，ROM 里有两份相同文本（Bank 10 和 Bank 42），本工具会同时修改两份，并自动修复 GB 校验和。

## 环境要求

- JDK 8 或更高（`java -version` 能跑就行）
- 原版 ROM：`Harvest Moon GB (USA) (SGB Enhanced).gbc`（1 MB，放在仓库根目录）

## 快速开始

```bash
cd java-randomizer
chmod +x build.sh
./build.sh
java -jar out/randomizer.jar --rom "../Harvest Moon GB (USA) (SGB Enhanced).gbc" --out "../Harvest Moon GB Randomizer.gbc" --text grandpa_welcome.txt
```

Windows：双击 `build.bat`，或在 cmd 里运行：

```bat
cd java-randomizer
build.bat
java -jar out\randomizer.jar --rom "..\Harvest Moon GB (USA) (SGB Enhanced).gbc" --out "..\Harvest Moon GB Randomizer.gbc" --text grandpa_welcome.txt
```

生成 `Harvest Moon GB Randomizer.gbc` 后，先用模拟器（mGBA / SameBoy / Gambatte）测试开场，再上实机。

> 不传 `--text` 时会用内置的欢迎词（和 `grandpa_welcome.txt` 相同）。

## 参数

| 参数 | 说明 | 默认值 |
|---|---|---|
| `--rom <path>` | 原版 .gbc | `Harvest Moon GB (USA) (SGB Enhanced).gbc` |
| `--out <path>` | 输出 .gbc | `Harvest Moon GB Randomizer.gbc` |
| `--text <path>` | 欢迎词文本文件，一行 = 一个对话页 | 内置欢迎词 |
| `--dump <path>` | 额外把原版信件解码导出到此文件 | 不导出 |

导出原版对照：

```bash
java -jar out/randomizer.jar --dump grandpa_original.txt
```

## 欢迎词文件怎么写

`grandpa_welcome.txt` 一行就是一个对话页，每行最多 **31 个编码字节**：

- 每个字母、空格、标点 = 1 字节
- `{PLAYER}` = 4 字节（玩家名字）
- 超过 31 字节会报错，请自己缩短

支持的字符：`A-Z`（小写会自动转大写）、`0-9`、空格、`.` `,` `'` `'S` `(` `)` `!` 和 `{PLAYER}`。其他字符会报错。

示例：

```text
{PLAYER} WAKE UP {PLAYER} WELCOME TO
HARVEST MOON RANDOMIZER EDITION
EVERYTHING IS RANDOM EVERY RUN
```

`#` 开头的行和空行会被忽略。改完保存，直接重新运行上面的命令即可（不用改 Java 代码）。

## 技术说明（给后续 Randomizer 开发）

- **文本编码**（`HarvestMoonCodec.java`）：不是 ASCII。`A-Z` 用 `0x1A-0x33`（普通字库），`0x00-0x19` 是同一套字形的另一字库；空格 `0xEF`，句号 `0x4C`，逗号 `0x8E`，撇号 `0x4B`，`'S` `0x53`，数字 `0x34-0x3D`，`{PLAYER}` `A0 A1 A2 A3`。编码器统一用普通字库，游戏能正常显示。
- **条目结构**（`GrandpaPatcher.java`）：每页 = 4 字节头 `BC 54 00 00` + 31 字节文本 + 2 字节结束符 + 6 字节 `00` 填充 = 43 字节。补丁只改 31 字节文本区，不动头和结束符，所以不需要改指针，长度必须保持不变。
- **校验和**：补丁后自动重算 Header checksum（`0x14D`）和 Global checksum（`0x14E-0x14F`）。
- **两份文本**：Bank 10（文件偏移 `0x29110`）和 Bank 42（`0xA9110`）各有一份信件，工具会找到并同时修改。

## 文件

```text
java-randomizer/
  build.sh / build.bat          编译脚本
  grandpa_welcome.txt           默认欢迎词（可直接改）
  grandpa_original.txt          原版信件解码对照
  src/main/java/com/kiwi/randomizer/
    HarvestMoonCodec.java       文本编解码
    GrandpaPatcher.java         定位 + 补丁 + 校验和
    Main.java                   命令行入口
```

## 注意事项

- 先备份原版 ROM，补丁工具只读原版、写新文件，不会覆盖原版。
- 第一次先在模拟器里确认开场白显示正确，再考虑实机。
- 后续做真正的 Randomizer（随机作物/动物/商店等）时，可以复用这里的编解码和校验和逻辑。
