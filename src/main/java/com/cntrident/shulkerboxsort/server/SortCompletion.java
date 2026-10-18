package com.cntrident.shulkerboxsort.server;

import com.cntrident.shulkerboxsort.network.SortResultPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.Optional;

/** The warning uses vanilla chat, so unmodded and older clients can read it. */
public record SortCompletion(SortResultPayload result, boolean emptyBoxStackingDisabled) {
    public static final String COMPLETED_KEY = "message.shulkerbox_sort.server_completed";
    public static final String WARNING_KEY = "message.shulkerbox_sort.stacking_disabled_warning";
    public static final String WARNING_FALLBACK =
            "警告：未开启潜影盒堆叠。物品整理与排序已完成，空潜影盒未进行堆叠。";

    public Optional<Component> warning() {
        if (!emptyBoxStackingDisabled || !COMPLETED_KEY.equals(result.messageKey())) return Optional.empty();
        return Optional.of(Component.translatableWithFallback(WARNING_KEY, WARNING_FALLBACK)
                .withStyle(ChatFormatting.YELLOW));
    }
}
