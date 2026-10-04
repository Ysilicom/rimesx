package org.scholay.rimes.core;

/** Ordered results within one editor lease; revocation invalidates every outstanding request. */
public final class InputEpoch {
    private volatile long generation;
    private long issued, accepted;
    public void revoke() { generation++; issued=0; accepted=0; }
    public Ticket issue() { return new Ticket(generation,++issued); }
    public boolean accept(Ticket ticket) {
        if(ticket.generation!=generation || ticket.sequence<=accepted) return false;
        accepted=ticket.sequence;
        return true;
    }
    public boolean current(Ticket ticket) { return ticket.generation==generation; }
    public static final class Ticket {
        private final long generation, sequence;
        private Ticket(long generation,long sequence) { this.generation=generation; this.sequence=sequence; }
    }
}
