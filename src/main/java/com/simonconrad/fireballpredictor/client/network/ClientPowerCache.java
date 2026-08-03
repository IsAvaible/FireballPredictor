package com.simonconrad.fireballpredictor.client.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientPowerCache {
    public static final Map<Integer, Float> POWER_CACHE = new ConcurrentHashMap<>();
}
