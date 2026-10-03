package org.scholay.rimes.core;

import java.util.Objects;

/** Target-bound plugin output. Call from one serial owner; network callbacks carry Request. */
public final class PluginSession {
    /** Bound the immutable output block in the same UTF-16 units as the source Buffer. */
    public static final int MAX_OUTPUT_UNITS = BufferSession.MAX_CHARACTERS;
    public enum Status { IDLE, RUNNING, READY, ERROR }

    private String plugin;
    private long serial;
    private Request request;
    private Status status = Status.IDLE;
    private String output = "";
    private String message = "";

    /** Re-selecting the same plugin in its settings keeps an active request or result. */
    public void select(String id) {
        if (id != null && blank(id)) throw new IllegalArgumentException("Plugin ID must not be blank");
        if (Objects.equals(plugin, id)) return;
        invalidate();
        plugin = id;
    }

    /** Cancel a request/result while preserving the selected plugin and source Buffer. */
    public void invalidate() {
        serial++;
        request = null;
        status = Status.IDLE;
        output = "";
        message = "";
    }

    /** Hide, input target retirement or a private field also revokes the selected plugin. */
    public void clear() { invalidate(); plugin = null; }

    /** A second Run while running does not launch a second network request. */
    public Request start(BufferSession buffer) {
        reconcile(buffer);
        if (plugin == null || status == Status.RUNNING) return null;
        BufferSession.Capture source = buffer == null ? null : buffer.capture();
        if (source == null) return null;
        invalidate();
        request = new Request(this, serial, plugin, source);
        status = Status.RUNNING;
        return request;
    }

    /** text is the complete accumulated output, not an increment; partial output is never sendable. */
    public boolean update(Request current, BufferSession buffer, String text, boolean complete) {
        if (!accepts(current, buffer) || text == null) return false;
        if (text.length() > MAX_OUTPUT_UNITS)
            return fail(current, buffer, "输出超过 16384 个 UTF-16 单元，原文已保留。");
        if (complete && text.isEmpty()) {
            status = Status.ERROR;
            output = "";
            message = "Plugin returned no output";
            return true;
        }
        output = text;
        message = "";
        if (complete) status = Status.READY;
        return true;
    }

    public boolean fail(Request current, BufferSession buffer, String failure) {
        if (!accepts(current, buffer)) return false;
        status = Status.ERROR;
        // A partial stream is not a successfully generated output block.
        output = "";
        message = failure == null || blank(failure) ? "Plugin request failed" : failure;
        return true;
    }

    public Snapshot snapshot(BufferSession buffer) {
        reconcile(buffer);
        return new Snapshot(plugin, serial, status, request == null ? "" : request.source.text, output, message);
    }

    /** The generated result is exactly one confirmed block, regardless of its whitespace. */
    public Delivery prepare(BufferSession buffer) {
        reconcile(buffer);
        if (status != Status.READY || request == null || output.isEmpty()) return null;
        return new Delivery(this, request, output);
    }

    /** Check immediately before committing to the current host connection. */
    public boolean isCurrent(Delivery delivery, BufferSession buffer) {
        reconcile(buffer);
        return delivery != null && delivery.owner == this && status == Status.READY
                && delivery.request == request && delivery.text.equals(output);
    }

    /** A failed host commit does not call this, so both output and source remain available. */
    public boolean acknowledge(Delivery delivery, BufferSession buffer) {
        if (!isCurrent(delivery, buffer) || !buffer.acknowledge(delivery.request.source)) return false;
        invalidate();
        return true;
    }

    private boolean accepts(Request current, BufferSession buffer) {
        reconcile(buffer);
        return current != null && current.owner == this && current == request && status == Status.RUNNING
                && current.id == serial && Objects.equals(current.plugin, plugin);
    }

    private void reconcile(BufferSession buffer) {
        if (request != null && (buffer == null || !buffer.isCurrent(request.source))) invalidate();
    }

    /** String.isBlank() is unavailable on the supported Android 8 runtime. */
    private static boolean blank(String text) {
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (!Character.isWhitespace(codePoint)) return false;
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    public static final class Request {
        private final PluginSession owner;
        public final long id;
        public final String plugin;
        public final BufferSession.Capture source;
        private Request(PluginSession owner, long id, String plugin, BufferSession.Capture source) {
            this.owner = owner; this.id = id; this.plugin = plugin; this.source = source;
        }
    }

    public static final class Snapshot {
        public final String plugin;
        public final long requestSerial;
        public final Status status;
        public final String source;
        public final String output;
        public final String message;
        private Snapshot(String plugin, long requestSerial, Status status, String source, String output, String message) {
            this.plugin = plugin; this.requestSerial = requestSerial; this.status = status;
            this.source = source; this.output = output; this.message = message;
        }
    }

    public static final class Delivery {
        private final PluginSession owner;
        private final Request request;
        public final String text;
        private Delivery(PluginSession owner, Request request, String text) {
            this.owner = owner; this.request = request; this.text = text;
        }
    }
}
