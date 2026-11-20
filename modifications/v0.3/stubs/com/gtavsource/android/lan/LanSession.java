package com.gtavsource.android.lan;

public final class LanSession {
    public static State state() { throw new UnsupportedOperationException(); }
    public static final class State {
        public boolean active, busy, cleanup, host, ready, virtualNetwork;
        public int generation, players, playerLimit;
        public LanTransport.Member[] members;
    }
}
