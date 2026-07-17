package com.fdimo.metrovillagers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Constants {

	public static final String MOD_ID = "metro_villagers";
	public static final String MOD_NAME = "Metro Villagers";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
	
	private static final AtomicInteger threadId = new AtomicInteger(1);
    public static final ThreadPoolExecutor ASYNC_POOL = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        r -> {
            Thread t = new Thread(r, "MetroVillagers-Async-" + threadId.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    );
}