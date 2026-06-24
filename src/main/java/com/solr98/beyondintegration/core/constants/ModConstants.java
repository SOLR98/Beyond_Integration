package com.solr98.beyondintegration.core.constants;

public final class ModConstants {

    private ModConstants() {
        throw new AssertionError("No instances");
    }

    // ─── Network ID ───────────────────────────────────────────
    /** Maximum number of dimension networks that can exist (0 to MAX_NETWORK_ID-1) */
    public static final int MAX_NETWORK_ID = 10000;
    /** Command argument upper bound (inclusive) for network ID parameters */
    public static final int MAX_NETWORK_ID_ARG = 9999;

    // ─── Extract / Craft Stack Sizes ──────────────────────────
    /** Default item count extracted on single click */
    public static final int DEFAULT_EXTRACT_COUNT = 64;
    /** Item count extracted on shift-click */
    public static final int SHIFT_EXTRACT_COUNT = 256;
    /** Maximum stack size for vanilla item inventory operations */
    public static final int MAX_STACK_SIZE = 64;
    /** Max craft count for shift-click in gun smith table */
    public static final int MAX_CRAFT_STACK = 64;

    // ─── Sentry ───────────────────────────────────────────────
    /** Maximum ammo count to reload into a sentry per operation */
    public static final int MAX_SENTRY_RELOAD_AMMO = 9000;

    // ─── Enchantment Separation ───────────────────────────────
    /** XP points (mB) to fluid mB multiplier for enchantment separation */
    public static final int XP_TO_MB_MULTIPLIER = 20;
    /** Maximum enchantments applied to a generated item */
    public static final int MAX_GENERATED_ENCHANTMENTS = 5;

    // ─── Fluid / Energy Conversion ────────────────────────────
    /** Millibuckets per 1 fuel unit */
    public static final int MB_PER_FUEL_UNIT = 1000;
    /** Default FE to fuel conversion divisor */
    public static final int DEFAULT_FE_TO_FUEL_DIVISOR = 1000;
    /** Default charge rate in FE per interval */
    public static final int DEFAULT_CHARGE_RATE_FE = 500000;

    // ─── Cache TTL Defaults (ms) ──────────────────────────────
    /** YWZJ vehicle overlay data expires after 2 minutes */
    public static final long CACHE_TTL_VEHICLE = 120_000L;
    /** SW vehicle ammo overlay TTL: 2 minutes */
    public static final long CACHE_TTL_SUPERB_VEHICLE = 120_000L;
    /** Maid network ID lookup cache TTL: 5 seconds */
    public static final long CACHE_TTL_MAID = 5_000L;
    /** Full ammo count request minimum interval: 6 seconds */
    public static final long CACHE_TTL_TACZ_AMMO_FULL = 6_000L;
    /** Toast display duration: 2.5 seconds */
    public static final long TOAST_DISPLAY_MS = 2_500L;
    /** Notification cooldown for "using network ammo" messages: 5 minutes */
    public static final long NOTIFICATION_COOLDOWN_MS = 300_000L;

    // ─── Sync Intervals (ms) ──────────────────────────────────
    /** YWZJ vehicle data sync interval: 2 seconds */
    public static final long SYNC_INTERVAL_VEHICLE = 2_000L;
    /** Force re-sync interval for player ammo: 5 seconds */
    public static final long SYNC_INTERVAL_FORCE = 5_000L;

    // ─── Game Time ────────────────────────────────────────────
    /** Minecraft ticks per second */
    public static final int TICKS_PER_SECOND = 20;
    /** Time thresholds for color formatting in player-readable output */
    public static final int SHORT_TIME_THRESHOLD_SECONDS = 30;
    public static final int MEDIUM_TIME_THRESHOLD_SECONDS = 300;
    /** Maximum charge interval in ticks: 60 seconds */
    public static final int MAX_CHARGE_INTERVAL_TICKS = 1200;

    // ─── Config ───────────────────────────────────────────────
    /** Command OP level required for admin commands */
    public static final int OP_LEVEL = 2;
    /** Maximum networks per page in command output */
    public static final int MAX_PAGE_SIZE = 100;
    /** Default networks per page */
    public static final int DEFAULT_PAGE_SIZE = 10;

    // ─── NBT / Data ───────────────────────────────────────────
    /** NBT size warning threshold in bytes */
    public static final int NBT_SIZE_WARNING_THRESHOLD = 10240;

    // ─── HUD / GUI Layout ─────────────────────────────────────
    public static final int SLOT_SIZE = 18;
    public static final int SLOT_GAP = 2;
    public static final float GUN_HUD_NET_TEXT_SCALE = 0.8f;
    public static final float AMMO_COUNT_TEXT_SCALE = 0.666f;
    public static final float SMITH_NET_COUNT_SCALE = 0.5f;
    public static final int MIN_SHOOT_AMMO = 1;
}
