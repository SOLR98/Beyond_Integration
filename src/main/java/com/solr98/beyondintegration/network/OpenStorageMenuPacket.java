package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.*;
import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** 打开维度存储菜单请求包（C2S）：客户端请求服务端为玩家打开指定类型的维度机器菜单（存储/铁砧/切石/研磨/锻造/合成） */
public record OpenStorageMenuPacket(Type type) {
    /** 要打开的菜单类型 */
    public enum Type { STORAGE, ANVIL, CUT, GRIND, SMITH, CRAFT }
    /** 将菜单类型枚举写入缓冲区 */
    public static void encode(OpenStorageMenuPacket m, FriendlyByteBuf b){b.writeEnum(m.type);}
    /** 从缓冲区读取菜单类型并还原数据包 */
    public static OpenStorageMenuPacket decode(FriendlyByteBuf b){return new OpenStorageMenuPacket(b.readEnum(Type.class));}
    /** 服务端执行：获取玩家主网络，按类型打开对应菜单 */
    public static void handle(OpenStorageMenuPacket m, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->{
            ServerPlayer p=ctx.get().getSender();if(p==null)return;
            DimensionsNet net=DimensionsNet.getPrimaryNetFromPlayer(p);if(net==null)return;
            var s=net.getUnifiedStorage();
            p.openMenu(new MenuProvider(){
                @Override public Component getDisplayName(){return Component.translatable("menu.title.beyonddimensions.dimensionnetmenu");}
                @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl){
                    return switch(m.type){
                        case STORAGE -> new DimensionsStorageMenu(ModMenus.STORAGE.get(),id,inv,s);
                        case ANVIL -> new DimensionsAnvilMenu(ModMenus.ANVIL.get(),id,inv,s);
                        case CUT -> new DimensionsCutMenu(ModMenus.CUT.get(),id,inv,s);
                        case GRIND -> new DimensionsGrindMenu(ModMenus.GRIND.get(),id,inv,s);
                        case SMITH -> new DimensionsSmithMenu(ModMenus.SMITH.get(),id,inv,s);
                        case CRAFT -> new DimensionsCraftMenu(ModMenus.CRAFT.get(),id,inv,s);
                    };
                }
            });
        });ctx.get().setPacketHandled(true);
    }
}
