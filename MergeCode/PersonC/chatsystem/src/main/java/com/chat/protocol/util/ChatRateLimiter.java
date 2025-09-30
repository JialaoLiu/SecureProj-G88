package com.chat.protocol.util;

import com.google.common.util.concurrent.RateLimiter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ChatRateLimiter {
    private static final Logger LOG = Logger.getLogger(RateLimiter.class.getName());
    private static final double MAX_RATE = 10.0;
    private static final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    public static boolean acquire(String peerId) {
        if (peerId == null || peerId.trim().isEmpty()) {
            LOG.warning("Invalid peerId for rate limiting");
            return false;
        }
        RateLimiter limiter = limiters.computeIfAbsent(peerId, k -> RateLimiter.create(MAX_RATE));
        boolean allowed = limiter.tryAcquire();
        if (!allowed) {
            LOG.log(Level.WARNING, "Rate limit exceeded for peer: {0}", peerId);
        }
        return allowed;
    }

    public static void remove(String peerId) {
        if (peerId != null) {
            limiters.remove(peerId);
            LOG.log(Level.INFO, "Removed rate limiter for peer: {0}", peerId);
        }
    }
}