package org.scholay.rimes.core;

import java.util.ArrayList;
import java.util.List;

/** An ephemeral, target-bound queue. No text is persisted or sent automatically. */
public final class BufferSession {
    public static final int MAX_CHARACTERS = 16 * 1024;
    private final List<String> blocks = new ArrayList<>();
    private long target;
    private long revision;
    private boolean permitted;
    private boolean enabled;

    public void beginTarget(boolean allowBuffer) {
        target++;
        permitted = allowBuffer;
        enabled = false;
        clear();
    }

    public void finishTarget() { beginTarget(false); }
    public boolean isPermitted() { return permitted; }
    public boolean isEnabled() { return enabled; }
    public int blockCount() { return blocks.size(); }
    public String text() { return String.join("", blocks); }

    public boolean setEnabled(boolean value) {
        if (value && !permitted) return false;
        enabled = value;
        revision++;
        return true;
    }

    public void clear() { blocks.clear(); revision++; }

    /** Literal English forms words; separators remain verbatim attached to the previous word. */
    public boolean appendLiteral(String text) {
        if (!enabled || !permitted || text == null || text.isEmpty()) return false;
        if (text().length() + text.length() > MAX_CHARACTERS) return false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String part = new String(Character.toChars(codePoint));
            offset += Character.charCount(codePoint);
            int last = blocks.size() - 1;
            if (last < 0) {
                blocks.add(part);
            } else {
                String previous = blocks.get(last);
                int tail = previous.codePointBefore(previous.length());
                if (Character.isLetterOrDigit(codePoint) && !Character.isLetterOrDigit(tail)) {
                    blocks.add(part);
                } else {
                    blocks.set(last, previous + part);
                }
            }
        }
        revision++;
        return true;
    }

    public void deleteLastBlock() {
        if (!enabled || blocks.isEmpty()) return;
        blocks.remove(blocks.size() - 1);
        revision++;
    }

    public Delivery prepare(boolean all) {
        if (!enabled || !permitted || blocks.isEmpty()) return null;
        int count = all ? blocks.size() : 1;
        return new Delivery(this, target, revision, String.join("", blocks.subList(0, count)), count);
    }

    public boolean isCurrent(Delivery delivery) {
        return delivery != null && delivery.owner == this && enabled && permitted
                && delivery.target == target && delivery.revision == revision;
    }

    /** Call only after commitText accepted this exact, still-current delivery. */
    public boolean acknowledge(Delivery delivery) {
        if (!isCurrent(delivery)) return false;
        blocks.subList(0, delivery.count).clear();
        revision++;
        return true;
    }

    public static final class Delivery {
        private final BufferSession owner;
        private final long target;
        private final long revision;
        private final int count;
        public final String text;
        private Delivery(BufferSession owner, long target, long revision, String text, int count) {
            this.owner = owner;
            this.target = target;
            this.revision = revision;
            this.text = text;
            this.count = count;
        }
    }
}
