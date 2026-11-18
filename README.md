# Kiwi's Local Multi-Game Randomizer

本地多游戏 Randomizer（目前先做牧场物语 GB / Harvest Moon GB）。

## 当前进度：第一步 — 爷爷开场白

用 Java 把爷爷开场信件改成欢迎玩家进入 Randomizer 的开场白。原版开场从 `{PLAYER} Wake Up {PLAYER}` 开始（`{PLAYER}` 是玩家名字占位符）。

### 原版爷爷的话（已解码，22 页）

原版信件解码后是（完整对照见 `java-randomizer/grandpa_original.txt`）：

```text
{PLAYER}... WAKE UP, {PLAYER}. I HAVE A
FAVOR TO ASK OF YOU. MY RANCH
HAS BEEN NEGLECTED BECAUSE
I DID NOT HAVE A SUCCESSOR SO,
I WANT YOU TO TAKE MY PLACE
TO RESTORE MY RANCH AND
SURPASS ME AS RANCH MASTER
UNFORTUNATELY, I CANNOT HELP
YOU BECAUSE I AM A SPIRIT.
AT THE END OF WINTER, I CAN
COME AND CHECK UP ON YOU.
I MUST BE GOING NOW. GOOD LUCK
FUTURE RANCH MASTER
OH, I FORGOT TO TELL YOU THAT
THE CROPS WILL DIE WHEN THE
SEASONS CHANGE BE SURE TO
CHECK THE DATE ON THE
CALENDAR IT IS ESSENTIAL FOR
GROWING CROPS
THANK YOU.
THANK YOU VERY MUCH
WE WILL ALSO TRY TO HELP YOU.
```

> 注：原版 ROM 里有几处缺空格（如 `ASUCCESSOR`、`ICAN`、`AMA`），解码时如实保留。游戏里全大写显示，和原版一致。

### 新的 Randomizer 欢迎词（21 页）

现在 `java-randomizer/grandpa_welcome.txt` 里的是（`Main.java` 里有相同内置版）：

```text
{PLAYER} WAKE UP {PLAYER} WELCOME TO
HARVEST MOON RANDOMIZER EDITION
EVERYTHING IS RANDOM EVERY RUN
CROPS ANIMALS AND ITEMS CHANGE
ITEMS MAY APPEAR IN OTHER GAMES
ITEMS MAY COME FROM OTHER GAMES
NO TWO FARMS ARE EVER THE SAME
PLANT ANYTHING ANYWHERE ANYTIME
MEET NEW FRIENDS NEW CHALLENGES
THE HARVEST GODDESS WATCHES YOU
GOOD LUCK FUTURE RANCH MASTER
OH I FORGOT TO TELL YOU THAT
THE SEASONS WILL SURPRISE YOU
CHECK THE CALENDAR EVERY DAY
IT IS ESSENTIAL FOR CROPS
THANKS FOR PLAYING RANDOMIZER
HAVE FUN AND ENJOY YOUR FARM
GOOD LUCK AND HAPPY FARMING
MAY YOUR HARVEST BE BOTH RANDOM
AND BOUNTIFUL EVERY SINGLE DAY
WELCOME TO YOUR NEW RANDOM FARM
```

其中第 5–6 页提到了跨游戏物品（物品可能出现在别的游戏里、可能拿到别的游戏的物品），对应 Multi-Game 的方向。

### 爷爷信件在 ROM 里的位置和结构

- ROM：`Harvest Moon GB (USA) (SGB Enhanced).gbc`（1 MB，64 banks，标题 `HARVEST-MOON GB`）
- 信件有两份相同文本（工具会同时修改两份）：
  - Bank 10：文件偏移 `0x29110`
  - Bank 42：文件偏移 `0xA9110`
- 每份 22 个条目（81–102），每个条目固定 43 字节：
  - 4 字节头：`BC 54 00 00`
  - 31 字节文本（用 `0xEF` 补齐）
  - 2 字节结束符：`E9 FE` 或 `EF FF`
  - 6 字节 `00` 填充
- 补丁只改 31 字节文本区，不动头和结束符，所以不需要改指针；改完自动重算 Header checksum（`0x14D`）和 Global checksum（`0x14E–0x14F`）。

### 文本编码（不是 ASCII）

- `A-Z` 普通字库：`0x1A–0x33`（`0x1A='A'` … `0x33='Z'`）；`0x00–0x19` 是同一套字形的另一字库，原版混用，编码器统一用普通字库
- 空格/分隔：`0xEF`；页内换行：`0x40`；词内断行（不可见）：`0x44`
- `.` = `0x4C`，`!` = `0x41`，`,` = `0x8E`，`'` = `0x4B`，`'S` = `0x53`，`0-9` = `0x34–0x3D`，`(` = `0x3E`，`)` = `0x3F`
- `{PLAYER}`（玩家名字插入）：`A0 A1 A2 A3`（4 字节）
- 每页最多 31 个编码字节（字母/空格/标点各 1 字节，`{PLAYER}` 4 字节）

### 怎么用 Java 工具

需要 JDK 8+。详见 `java-randomizer/README.md`，快速版：

```bash
cd java-randomizer
./build.sh
java -jar out/randomizer.jar --rom "../Harvest Moon GB (USA) (SGB Enhanced).gbc" --out "../Harvest Moon GB Randomizer.gbc" --text grandpa_welcome.txt
```

Windows 用 `build.bat`。生成的 `Harvest Moon GB Randomizer.gbc` 先在模拟器里测试。

## 仓库结构

```text
.
├── Harvest Moon GB (USA) (SGB Enhanced).gbc  原版 ROM（只读，不覆盖）
├── README.md                                  本文件
└── java-randomizer/
    ├── README.md                              Java 工具说明
    ├── build.sh / build.bat                   编译脚本
    ├── grandpa_welcome.txt                    欢迎词（一行一页，可直接改）
    ├── grandpa_original.txt                   原版信件解码对照
    └── src/main/java/com/kiwi/randomizer/
        ├── HarvestMoonCodec.java              文本编解码
        ├── GrandpaPatcher.java                定位 + 补丁 + 校验和
        └── Main.java                          命令行入口
```

## 下一步（待做）

- [ ] 在模拟器里确认开场白显示正常
- [ ] 随机种子 / 作物 / 动物 / 商店 / 价格等（复用这里的编解码和校验和逻辑）
- [ ] 跨游戏物品池（`ITEMS MAY APPEAR / COME FROM OTHER GAMES` 的实际实现）
