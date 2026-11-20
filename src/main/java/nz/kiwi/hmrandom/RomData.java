package nz.kiwi.hmrandom;

/**
 * Reverse-engineered constants for Harvest Moon GB (USA/Europe, SGB Enhanced).
 *
 * Every value here was located by searching the raw ROM and confirmed against
 * the game's behaviour in PyBoy. Addresses are ROM file offsets unless noted.
 *
 * ROM md5: 498c0a50a5e5cde16127617a97ad6162
 */
public final class RomData {

    private RomData() {
    }

    public static final String ROM_TITLE = "HARVEST-MOON GB";
    public static final int ROM_SIZE = 1 << 20; // 1 MiB, 64 banks of 0x4000

    // ------------------------------------------------------------------
    // The dresser ("grandpa's rainy day money") check
    // ------------------------------------------------------------------

    /** ROM offset of DresserInteract. Bank 2, GB address 0x59C8. */
    public static final int DRESSER_ROUTINE = 0x99C8;

    /**
     * ROM offset of the 10-byte reward stub inside DresserInteract:
     *   ld hl, sPlayerMoney      ; 21 EF B8
     *   ld bc, 1000              ; 01 E8 03
     *   call AddSignedBCToHL     ; CD BB 0C
     *   call UpdatePlayerMoneyTileData ; CD 56 10
     */
    public static final int DRESSER_REWARD_STUB = 0x99D3;
    public static final int DRESSER_REWARD_STUB_LEN = 10;

    /** ROM offset of the 16-bit money immediate inside the stub. */
    public static final int DRESSER_MONEY_IMMEDIATE = 0x99D6;

    /**
     * Free space at the end of bank 2 (0xFF padding, no labels reference it).
     * Used to inject the item-granting routine. GB address 0x7F7C.
     */
    public static final int GIVE_ITEM_ROUTINE = 0xBF7C;
    public static final int GIVE_ITEM_ROUTINE_LEN = 19;

    /** ROM offset of the "Received 1000G!!" text (16 bytes, one text line). */
    public static final int TEXT_RECEIVED = 0x1C70F;
    public static final int TEXT_RECEIVED_LEN = 16;

    // ------------------------------------------------------------------
    // SRAM (save RAM) addresses. These are GB addresses in the 0xA000-0xBFFF window.
    // ------------------------------------------------------------------
    public static final int S_PLAYER_MONEY = 0xB8EF;          // 3 bytes, little endian
    public static final int S_FOUND_MONEY_IN_DRESSER = 0xB8FF; // 1 = already looted
    public static final int S_INVENTORY = 0xB8F8;              // 3 slots, 0xFF = empty

    // ------------------------------------------------------------------
    // Item IDs (from the game's own item constants)
    // ------------------------------------------------------------------
    public static final int ITEM_COW_MEDICINE = 0x00;
    public static final int ITEM_M_POTION = 0x01;
    public static final int ITEM_COW_BELL = 0x02;
    public static final int ITEM_GRASS_SEEDS = 0x03;
    public static final int ITEM_TURNIP_SEEDS = 0x04;
    public static final int ITEM_POTATO_SEEDS = 0x05;
    public static final int ITEM_TOMATO_SEEDS = 0x06;
    public static final int ITEM_CORN_SEEDS = 0x07;
    public static final int ITEM_MILKER = 0x08;
    public static final int ITEM_BRUSH = 0x09;
    public static final int ITEM_WATERING_CAN = 0x0a;
    public static final int ITEM_SICKLE = 0x0b;
    public static final int ITEM_HOE = 0x0c;
    public static final int ITEM_HAMMER = 0x0d;
    public static final int ITEM_AX = 0x0e;
    public static final int ITEM_COW_FEED = 0x10;
    public static final int ITEM_CHICKEN_FEED = 0x11;
    public static final int ITEM_SPRINKLER = 0x12;
    public static final int ITEM_SUPER_SICKLE = 0x13;
    public static final int ITEM_SUPER_HOE = 0x14;
    public static final int ITEM_SUPER_AX = 0x15;
    public static final int ITEM_SUPER_HAMMER = 0x16;
    public static final int ITEM_NO_ITEM = 0xff;

    // ------------------------------------------------------------------
    // WRAM addresses (used by the PyBoy verification harness)
    // ------------------------------------------------------------------
    public static final int W_PLAYER_X = 0xC606;
    public static final int W_PLAYER_Y = 0xC608;
    public static final int W_PLAYER_FACING = 0xC60D;
    public static final int W_DOMINANT_FACING_TILE_ID = 0xCB33;
    public static final int W_DOMINANT_FACING_TILE_PROPERTY = 0xCB34;
    public static final int W_TEXT_ID = 0xCB52;
    public static final int W_PLAYER_IS_INSIDE_OR_AT_TOWN = 0xCB81;
    public static final int W_COLLISION_NO_MOVEMENT = 0xC90E;
    public static final int W_FREEZE_PLAYER_IN_TEXT = 0xCB56;
    public static final int W_PLAYER_IDLE_WHEN_EXITING = 0xCB4F;
    public static final int W_PLAYER_HOLDING_PET = 0xCB8B;
}
