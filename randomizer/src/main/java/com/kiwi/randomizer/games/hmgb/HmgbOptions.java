package com.kiwi.randomizer.games.hmgb;

/**
 * Configuration options for generating a Harvest Moon GBC randomized ROM.
 * Designed to grow cleanly as we research and add crops, shops, animals, tools, and multi-game items.
 */
public final class HmgbOptions {

    /**
     * Default Grandpa's Spirit welcome text — opens with the iconic "{PLAYER}...Wake up, {PLAYER}!"
     * and welcomes the player into Kiwi's Local Multi-Game Randomizer.
     */
    public static final String DEFAULT_GRANDPA_WELCOME =
            "{PLAYER}...Wake up,\n"
            + "{PLAYER}!\n"
            + "\n"
            + "Welcome to\n"
            + "Kiwi's Local\n"
            + "Multi-Game\n"
            + "Randomizer!\n"
            + "\n"
            + "My old ranch is full of surprises now! Crops, animals, tools, and items can change on every single run!\n"
            + "\n"
            + "Restore my ranch and surpass me as Ranch Master! At the end of winter, I will come check on you.\n"
            + "\n"
            + "Good luck, {PLAYER}!\n"
            + "Happy farming! {HEART}";

    /**
     * Shorter 4-box preset for quick-start runs.
     */
    public static final String SHORT_GRANDPA_WELCOME =
            "{PLAYER}...Wake up, {PLAYER}! Welcome to Kiwi's Randomizer!\n"
            + "\n"
            + "Restore my ranch and become a true Ranch Master!\n"
            + "\n"
            + "Good luck, {PLAYER}!\n"
            + "Happy farming! {HEART}";

    private boolean patchGrandpaText = true;
    private boolean manualBoxMode = false;
    private String grandpaText = DEFAULT_GRANDPA_WELCOME;

    public boolean isPatchGrandpaText() {
        return patchGrandpaText;
    }

    public void setPatchGrandpaText(boolean patchGrandpaText) {
        this.patchGrandpaText = patchGrandpaText;
    }

    public boolean isManualBoxMode() {
        return manualBoxMode;
    }

    public void setManualBoxMode(boolean manualBoxMode) {
        this.manualBoxMode = manualBoxMode;
    }

    public String getGrandpaText() {
        return grandpaText;
    }

    public void setGrandpaText(String grandpaText) {
        this.grandpaText = grandpaText;
    }
}
