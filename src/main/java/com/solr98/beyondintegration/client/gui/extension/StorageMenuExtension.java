package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.core.util.WSStateHelper;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public class StorageMenuExtension implements IDimensionsNetGUIExtension {
    private int bx,by;
    @Override public int priority(){return 0;}
    @Override public void onInit(DimensionsNetGUI<?> g){bx=g.getGuiLeft()-18;by=BDGUIExtensionRegistry.getSlotY(g.getGuiTop(),1);}
    @Override public void onRender(DimensionsNetGUI<?> g, GuiGraphics gr, int mx, int my, float pt){
        boolean h=mx>=bx&&mx<bx+16&&my>=by&&my<by+16;
        gr.blit(ResourceLocation.tryParse(h?"beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png":"beyonddimensions:textures/gui/sprites/widget/slot_button.png"),bx,by,0,0,16,16,16,16);
        var p=gr.pose();p.pushPose();p.translate(bx+1,by+1,1);p.scale(0.85f,0.85f,1);gr.renderFakeItem(new ItemStack(Items.ANVIL),0,0);p.popPose();
        if(h)gr.renderTooltip(Minecraft.getInstance().font, List.of(Component.translatable("gui.beyond_integration.open_storage")),ItemStack.EMPTY.getTooltipImage(),ItemStack.EMPTY,mx,my);
    }
    @Override public boolean onMouseClicked(DimensionsNetGUI<?> g, double mx, double my, int btn){
        if(mx>=bx&&mx<bx+16&&my>=by&&my<by+16){
            long win=Minecraft.getInstance().getWindow().getWindow();
            double[] wx=new double[1],wy=new double[1];
            GLFW.glfwGetCursorPos(win,wx,wy);
            WSStateHelper.mouseX=wx[0];WSStateHelper.mouseY=wy[0];WSStateHelper.lineData=((com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu)g.getMenu()).lineData;
            WSStateHelper.pendingRestore=true;
            PacketHandler.sendToServer(new OpenStorageMenuPacket(OpenStorageMenuPacket.Type.STORAGE));
            return true;
        }
        return false;
    }
}
