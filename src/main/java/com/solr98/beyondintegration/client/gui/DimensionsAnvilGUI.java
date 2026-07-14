package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.feature.crafting.DimensionsAnvilMenu;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SetAnvilNamePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class DimensionsAnvilGUI extends DimensionsStorageGUI<DimensionsAnvilMenu> {
    private static final ResourceLocation BG = ResourceLocation.tryParse("beyond_integration:textures/gui/anvil.png");
    private static final ResourceLocation RN = ResourceLocation.tryParse("beyond_integration:textures/gui/anvil_rename.png");
    private EditBox nameField;
    private ItemStack lastInput = ItemStack.EMPTY;
    private boolean hadOutput = false;

    public DimensionsAnvilGUI(DimensionsAnvilMenu c, Inventory p, Component t) { super(c,p,t); }

    @Override protected void init() {
        super.init();
        if(nameField==null){
            nameField=new EditBox(Minecraft.getInstance().font,this.leftPos+61,getGapY()+20,110,12,Component.translatable("container.repair"));
            nameField.setCanLoseFocus(false);nameField.setTextColor(-1);nameField.setTextColorUneditable(-1);
            nameField.setBordered(false);nameField.setMaxLength(50);
            nameField.setResponder(t->{if(!t.equals(this.menu.anvName)){this.menu.anvName=t;PacketHandler.sendToServer(new SetAnvilNamePacket(this.menu.containerId, t));}});
            addRenderableWidget(nameField);
        }
        ItemStack input = this.menu.delegate.getSlot(0).getItem();
        if (!input.isEmpty()) {
            String name = input.getHoverName().getString();
            this.menu.anvName = name;
            nameField.setValue(name);
            PacketHandler.sendToServer(new SetAnvilNamePacket(this.menu.containerId, name));
        }
    }

    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy=getGapY();g.blit(BG,this.leftPos,gy,0,0,176,62,176,62);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.anvil"), this.leftPos + 6, gy - 7, 0x404040, false);
        boolean h=!this.menu.delegate.getSlot(0).getItem().isEmpty();
        g.blit(RN,this.leftPos+59,gy+15,0,h?0:16,110,16,110,32);
        nameField.setX(this.leftPos+61);nameField.setY(gy+20);
        if(this.menu.anvLevel>0&&!this.menu.getOutput().isEmpty()){
            int c=8453920;if(this.menu.anvLevel>=40&&!Minecraft.getInstance().player.getAbilities().instabuild)c=16736352;
            String tx=Component.translatable("container.repair.cost",this.menu.anvLevel).getString();
            var font = Minecraft.getInstance().font;
            g.drawString(font,tx,this.leftPos+134+8-font.width(tx)/2,gy+50,c);
        }
    }
    @Override public boolean keyPressed(int k,int s,int m){if(nameField!=null&&nameField.keyPressed(k,s,m))return true;return super.keyPressed(k,s,m);}
    @Override public boolean charTyped(char c,int m){if(nameField!=null&&nameField.charTyped(c,m))return true;return super.charTyped(c,m);}
    @Override public void containerTick(){
        super.containerTick();
        if(nameField==null)return;
        nameField.tick();
        ItemStack input = this.menu.delegate.getSlot(0).getItem();
        if (!ItemStack.matches(lastInput, input)) {
            boolean wasEmpty = lastInput.isEmpty();
            lastInput = input.copy();
            if (wasEmpty && !input.isEmpty()) {
                String newName = input.getHoverName().getString();
                if (!newName.equals(this.menu.anvName)) {
                    this.menu.anvName = newName;
                    nameField.setValue(newName);
                    PacketHandler.sendToServer(new SetAnvilNamePacket(this.menu.containerId, newName));
                }
            }
        }
        ItemStack output = this.menu.getOutput();
        if (hadOutput && output.isEmpty()) {
            this.menu.anvName = "";
            nameField.setValue("");
            PacketHandler.sendToServer(new SetAnvilNamePacket(this.menu.containerId, ""));
        }
        hadOutput = !output.isEmpty();
    }
}
