package com.mingdy.coupon.infrastructure.messaging;

import com.mingdy.coupon.support.QueueFullException;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
public class InMemoryIssuanceQueue {

    private static final int CAPACITY = 10_000;
    private static final long POLL_TIMEOUT_MS = 100L;

    private final LinkedBlockingQueue<IssuanceRequested> queue = new LinkedBlockingQueue<IssuanceRequested>(CAPACITY);

    public void enqueue(IssuanceRequested event) {
        if(!queue.offer(event)) {
            throw new QueueFullException();
        }
    }

    public IssuanceRequested poll() throws InterruptedException {
        return queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    public int size() {
        return queue.size();
    }

}
