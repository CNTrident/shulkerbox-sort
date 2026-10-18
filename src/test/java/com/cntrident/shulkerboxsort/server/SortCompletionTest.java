package com.cntrident.shulkerboxsort.server;

import com.cntrident.shulkerboxsort.network.SortResultPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SortCompletionTest {
    @BeforeAll static void bootstrap() { ServerInventorySorterTest.bootstrap(); }
    @Test void successfulSortProvidesYellowWarningReadableWithoutClientMod() {
        var completion = new SortCompletion(result(SortCompletion.COMPLETED_KEY), true);
        var warning = completion.warning().orElseThrow();
        var contents = assertInstanceOf(TranslatableContents.class, warning.getContents());
        assertEquals(SortCompletion.WARNING_KEY, contents.getKey());
        assertEquals(SortCompletion.WARNING_FALLBACK, contents.getFallback());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW), warning.getStyle().getColor());
    }
    @Test void enabledStackingAndFailedSortsHaveNoDisabledWarning() {
        assertTrue(new SortCompletion(result(SortCompletion.COMPLETED_KEY), false).warning().isEmpty());
        assertTrue(new SortCompletion(result("message.shulkerbox_sort.transaction_failed"), true).warning().isEmpty());
        assertTrue(new SortCompletion(result("message.shulkerbox_sort.cursor_not_empty"), true).warning().isEmpty());
    }
    private static SortResultPayload result(String messageKey) {
        return new SortResultPayload(1, messageKey, 3, 2, 1, 0, 100);
    }
}
