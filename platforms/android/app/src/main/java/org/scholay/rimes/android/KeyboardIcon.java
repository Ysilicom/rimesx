package org.scholay.rimes.android;

import android.content.Context;
import android.graphics.drawable.Drawable;

/** Native vectors converted from a pinned official Lucide subset; see resources/icons/. */
enum KeyboardIcon {
    SETTINGS(R.drawable.rimes_icon_settings),
    SLIDERS(R.drawable.rimes_icon_sliders),
    STACK_LAYERS(R.drawable.rimes_icon_stack_layers),
    GRID_9(R.drawable.rimes_icon_grid_9),
    PAPER_PLANE(R.drawable.rimes_icon_paper_plane),
    SEND_ALL(R.drawable.rimes_icon_send_all),
    SHIFT(R.drawable.rimes_icon_shift),
    SHIFT_FILL(R.drawable.rimes_icon_shift_fill),
    DELETE(R.drawable.rimes_icon_delete),
    SMILE(R.drawable.rimes_icon_smile),
    GLOBE(R.drawable.rimes_icon_globe),
    TRANSLATE(R.drawable.rimes_icon_translate),
    CHAT_QUESTION(R.drawable.rimes_icon_chat_question),
    MAGIC_WAND(R.drawable.rimes_icon_magic_wand),
    BOOK(R.drawable.rimes_icon_book),
    PLAY(R.drawable.rimes_icon_play),
    PAUSE(R.drawable.rimes_icon_pause),
    STOP(R.drawable.rimes_icon_stop),
    CHEVRON_LEFT(R.drawable.rimes_icon_chevron_left),
    CHEVRON_RIGHT(R.drawable.rimes_icon_chevron_right),
    CHECK(R.drawable.rimes_icon_check),
    APPEARANCE(R.drawable.rimes_icon_appearance),
    SPACE(R.drawable.rimes_icon_space),
    KEYBOARD(R.drawable.rimes_icon_keyboard),
    HIDE_KEYBOARD(R.drawable.rimes_icon_hide_keyboard),
    CLEAR(R.drawable.rimes_icon_clear),
    WRITE(R.drawable.rimes_icon_write);

    private final int resource;
    KeyboardIcon(int resource) { this.resource=resource; }
    Drawable drawable(Context context) { return context.getDrawable(resource).mutate(); }
}
