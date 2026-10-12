package dev.stagelock.api.restriction;

/** Things an item restriction can block. */
public enum ItemAction {
    /** Picking the item up from the ground. */
    PICKUP,
    /** Right-click use: on air, on blocks, on entities, and continuous use (eating, bows, shields). */
    USE,
    /** Placing the item as a block. */
    PLACE,
    /** Hitting entities while holding it. */
    ATTACK,
    /** Mining blocks while holding it. */
    MINE,
    /** Wearing it in an armor slot. */
    EQUIP,
    /** Keeping it in the inventory at all. */
    HOLD,
    /** Crafting it (any recipe whose output it is). */
    CRAFT,
    /** Showing it in JEI / REI / EMI. */
    VIEWER
}
