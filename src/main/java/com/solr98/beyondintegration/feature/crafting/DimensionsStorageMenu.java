package com.solr98.beyondintegration.feature.crafting;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import static com.wintercogs.beyonddimensions.common.init.BDMenus.Dimensions_Craft_Menu;

public class DimensionsStorageMenu extends DimensionsNetMenu {
    protected static final Field SLOT_X = ObfuscationReflectionHelper.findField(Slot.class, "f_40220_");
    protected static final Field SLOT_Y = ObfuscationReflectionHelper.findField(Slot.class, "f_40221_");
    public static void setSlotX(Slot s, int x) { try { SLOT_X.setInt(s, x); } catch (Exception ignored) {} }
    public static void setSlotY(Slot s, int y) { try { SLOT_Y.setInt(s, y); } catch (Exception ignored) {} }

    public DimensionsStorageMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(Dimensions_Craft_Menu.get(), id, playerInventory, new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }
    public DimensionsStorageMenu(MenuType<?> type, int id, Inventory playerInventory, AbstractUnorderedStackHandler data) {
        super(type, id, playerInventory, data);
    }

    public final List<Integer> customSlotIndices = new ArrayList<>();
    public int getPanelHeight() { return 62; }
    public int ey(int baseY) { return 68 + (getLines() - 2) * 18 + baseY + 1; }

    @Override protected void addPlayerInv(Inventory inv) {
        int ph = getPanelHeight();
        inventoryStartIndex = slots.size();
        for (int r = 0; r < 3; ++r) for (int c = 0; c < 9; ++c)
            addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 25 + ph + (getLines() - 1) * 18 + 26 + 6 + r * 18));
        for (int c = 0; c < 9; ++c)
            addSlot(new Slot(inv, c, 8 + c * 18, 25 + ph + (getLines() - 1) * 18 + 26 + 6 + 3 * 18 + 4));
        inventoryEndIndex = slots.size();
    }

    @Override public void rebuildSlots() {
        int ph = getPanelHeight();
        int n = 0;
        for (Slot s : slots) {
            if (s instanceof com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot ss) { ss.setActive(n / 9 < getLines()); n++; }
        }
        int i = inventoryStartIndex; n = 0;
        while (i < inventoryEndIndex) {
            Slot s = slots.get(i);
            setSlotY(s, 25 + ph + (getLines() - 1) * 18 + 26 + 6 + (n / 9 < 3 ? n / 9 * 18 : 3 * 18 + 4));
            i++; n++;
        }
    }

    @Override public boolean stillValid(@NotNull Player p) { return true; }
}
