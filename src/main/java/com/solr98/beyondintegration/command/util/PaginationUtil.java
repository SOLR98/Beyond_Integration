package com.solr98.beyondintegration.command.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class PaginationUtil {

    public static MutableComponent createPagination(String commandPrefix, int currentPage, int totalPages, int totalItems) {
        MutableComponent nav = Component.literal("");

        if (currentPage > 0) {
            nav = nav.append(Component.literal("[◀ Prev]")
                    .withStyle(s -> s
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    commandPrefix + " " + (currentPage - 1)))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Page " + currentPage)))
                            .withColor(ChatFormatting.GREEN)));
            nav = nav.append(Component.literal(" "));
        }

        nav = nav.append(Component.literal("[" + (currentPage + 1) + "/" + totalPages + "]")
                .withStyle(ChatFormatting.YELLOW));

        if (currentPage < totalPages - 1) {
            nav = nav.append(Component.literal(" "));
            nav = nav.append(Component.literal("[Next ▶]")
                    .withStyle(s -> s
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    commandPrefix + " " + (currentPage + 1)))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Page " + (currentPage + 2))))
                            .withColor(ChatFormatting.GREEN)));
        }

        nav = nav.append(Component.literal(" §7(" + totalItems + " total)"));
        return nav;
    }
}
