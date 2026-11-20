package nz.kiwi.hmrandom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Harvest Moon GB (GBC) world definition.
 *
 * Step one of the project is the farmhouse dresser that holds grandpa's rainy
 * day money. The dresser is the check; the money (or anything else the
 * multiworld decides to put there) is the item.
 */
public final class HarvestMoonGb implements World {

    public static final String NAME = "Harvest Moon GB";

    private static final Location DRESSER = new Location("dresser", "Dresser", "Farmhouse");

    private final List<Item> items;
    private final List<Location> locations;

    public HarvestMoonGb() {
        items = new ArrayList<Item>();
        // The check's vanilla contents and a spread of stand-ins.
        items.add(Item.money("grandpa_rainy_day_money", "Grandpa's Rainy Day Money", 1000));
        items.add(Item.money("money_500", "500G", 500));
        items.add(Item.money("money_2000", "2000G", 2000));
        items.add(Item.money("money_5000", "5000G", 5000));
        items.add(Item.money("money_10000", "10000G", 10000));
        items.add(Item.item("turnip_seeds", "Turnip Seeds", RomData.ITEM_TURNIP_SEEDS));
        items.add(Item.item("potato_seeds", "Potato Seeds", RomData.ITEM_POTATO_SEEDS));
        items.add(Item.item("tomato_seeds", "Tomato Seeds", RomData.ITEM_TOMATO_SEEDS));
        items.add(Item.item("corn_seeds", "Corn Seeds", RomData.ITEM_CORN_SEEDS));
        items.add(Item.item("watering_can", "Watering Can", RomData.ITEM_WATERING_CAN));
        items.add(Item.item("sickle", "Sickle", RomData.ITEM_SICKLE));
        items.add(Item.item("hoe", "Hoe", RomData.ITEM_HOE));

        locations = new ArrayList<Location>();
        locations.add(DRESSER);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Item> items() {
        return items;
    }

    @Override
    public List<Location> locations() {
        return locations;
    }

    @Override
    public List<Patch> createPatches(Map<String, Item> assignment) {
        List<Patch> patches = new ArrayList<Patch>();

        Item reward = assignment.get(DRESSER.id());
        if (reward == null) {
            return patches;
        }

        if (reward.kind() == Item.Kind.MONEY) {
            patches.addAll(dresserMoneyPatches(reward.value()));
        } else {
            patches.addAll(dresserItemPatches(reward.value(), reward));
        }
        return patches;
    }

    // ------------------------------------------------------------------
    // Money reward: keep the game's own "Received 1000G!!" path and just
    // change the amount it adds and prints.
    // ------------------------------------------------------------------
    private static List<Patch> dresserMoneyPatches(int amount) {
        List<Patch> patches = new ArrayList<Patch>();

        byte[] immediate = new byte[] {
            (byte) (amount & 0xff),
            (byte) ((amount >> 8) & 0xff)
        };
        patches.add(new Patch(
            RomData.DRESSER_MONEY_IMMEDIATE,
            immediate,
            "dresser money amount = " + amount + "G"));

        String line = String.format("Received %4dG!!", amount);
        patches.add(new Patch(
            RomData.TEXT_RECEIVED,
            TextCodec.encode(line),
            "dresser text = \"" + line.trim() + "\""));
        return patches;
    }

    // ------------------------------------------------------------------
    // Item reward: inject a tiny "put item in first empty inventory slot"
    // routine into free space at the end of bank 2, then redirect the
    // dresser's reward stub to it.
    // ------------------------------------------------------------------
    private static List<Patch> dresserItemPatches(int itemId, Item item) {
        List<Patch> patches = new ArrayList<Patch>();

        // give_item:  ld c,a ; ld hl,sInventory ; ld b,3
        // .loop:     ld a,[hl] ; cp NO_ITEM ; jr z,.found
        //            inc hl ; dec b ; jr nz,.loop ; ret
        // .found:    ld a,c ; ld [hl],a ; ret
        byte[] routine = new byte[] {
            0x4F,             // ld c, a
            0x21, (byte) 0xF8, (byte) 0xB8, // ld hl, $B8F8 (sInventory)
            0x06, 0x03,       // ld b, 3
            0x7E,             // .loop: ld a, [hl]
            (byte) 0xFE, (byte) 0xFF, // cp $FF
            0x28, 0x05,       // jr z, .found
            0x23,             // inc hl
            0x05,             // dec b
            0x20, (byte) 0xF7,// jr nz, .loop
            (byte) 0xC9,      // ret
            0x79,             // .found: ld a, c
            0x77,             // ld [hl], a
            (byte) 0xC9       // ret
        };
        patches.add(new Patch(
            RomData.GIVE_ITEM_ROUTINE,
            routine,
            "inject give_item routine"));

        // Overwrite the 10-byte money stub with: ld a,item ; call give_item ; nop*5
        byte[] stub = new byte[RomData.DRESSER_REWARD_STUB_LEN];
        stub[0] = 0x3E;                                  // ld a, item
        stub[1] = (byte) itemId;
        stub[2] = (byte) 0xCD;                           // call
        stub[3] = (byte) (0x4000 + (RomData.GIVE_ITEM_ROUTINE - 0x8000)); // low
        stub[4] = (byte) ((0x4000 + (RomData.GIVE_ITEM_ROUTINE - 0x8000)) >> 8); // high
        for (int i = 5; i < stub.length; i++) {
            stub[i] = 0x00;                              // nop
        }
        patches.add(new Patch(
            RomData.DRESSER_REWARD_STUB,
            stub,
            "dresser gives item id " + itemId));

        // The text line is 16 columns; keep it inside that.
        String name = item.name();
        if (name.length() > 12) {
            name = name.substring(0, 12);
        }
        String line = "Got " + name + "!!";
        patches.add(new Patch(
            RomData.TEXT_RECEIVED,
            TextCodec.encode(line),
            "dresser text = \"" + line + "\""));
        return patches;
    }

    /** Exposed for the verification harness and tests. */
    public static List<String> dresserPatchOffsets() {
        return Arrays.asList(
            Integer.toHexString(RomData.DRESSER_MONEY_IMMEDIATE),
            Integer.toHexString(RomData.DRESSER_REWARD_STUB),
            Integer.toHexString(RomData.GIVE_ITEM_ROUTINE),
            Integer.toHexString(RomData.TEXT_RECEIVED));
    }
}
