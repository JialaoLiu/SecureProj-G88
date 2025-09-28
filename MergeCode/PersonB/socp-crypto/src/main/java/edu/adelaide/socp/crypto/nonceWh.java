package edu.adelaide.socp.crypto;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


final class nonceWh {
    private static final long Allowtime = HeaderUtil.Maxtime;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> old = new ConcurrentHashMap<>(); //以前的消息

    boolean checkAndstore(String senderID, String nonce, Long time) {
        long timeNow = System.currentTimeMillis();
        if (Math.abs(timeNow - time) > Allowtime)
            return false;
        var perSender = old.computeIfAbsent(senderID, k -> new ConcurrentHashMap<>());
        Long preValue = perSender.putIfAbsent(nonce, time);
        cleanup(perSender, timeNow);
        return preValue == null;
    }

    private void cleanup(ConcurrentHashMap<String, Long> perSender, long timeNow) {
        for (Map.Entry<String, Long> e : perSender.entrySet()) {
            if (timeNow - e.getValue() > Allowtime) {
                perSender.remove(e.getKey(), e.getValue());

            }
        }
    }
}
