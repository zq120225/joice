/**
 * 深圳金融电子结算中心
 * Copyright (c) 1995-2017 All Rights Reserved.
 */
package org.joice.cache.map;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.joice.cache.Cache;
import org.joice.cache.config.CacheConfig;
import org.joice.cache.to.CacheKey;
import org.joice.cache.to.CacheWrapper;
import org.joice.cache.util.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 使用Map实现本地缓存
 * @author HuHui
 * @version $Id: MapCache.java, v 0.1 2017年10月18日 下午8:33:26 HuHui Exp $
 */
public class MapCache implements Cache {

    private static final Logger                     logger = LoggerFactory.getLogger(MapCache.class);

    /** 缓存配置类 */
    private final CacheConfig                       config;

    private final MapCacheDaemon                    daemon;

    private final ConcurrentHashMap<String, Object> cache;

    private final MapCacheChangeListener            listener;

    private Thread                                  daemonThread;

    private static final Long                       zero   = 0L;

    private static final Long                       one    = 1L;

    public MapCache(CacheConfig config) {
        LogUtil.info(logger, "Map Cache initing...");

        this.config = config;
        cache = new ConcurrentHashMap<String, Object>(config.getMaxCacheNums());
        daemon = new MapCacheDaemon(this, config);

        //读取磁盘缓存
        daemon.readCacheFromDisk();

        //启动守护线程
        startDaemon();

        //创建缓存更改监听器
        listener = new MapCacheChangeListener(config, this);

        LogUtil.info(logger, "Map Cache init success!");
    }

    @Override
    public void set(CacheKey cacheKey, CacheWrapper wrapper) {
        String key;
        if (cacheKey == null || StringUtils.isEmpty(key = cacheKey.getKey()) || wrapper == null) {
            return;
        }
        cache.put(key, wrapper);

        if (cache.size() > config.getMaxCacheNums()) {
            listener.discard();
        }
    }

    @Override
    public CacheWrapper get(CacheKey cacheKey) {
        String key;
        if (cacheKey == null || StringUtils.isEmpty(key = cacheKey.getKey())) {
            return null;
        }

        CacheWrapper wrapper = null;
        Object value = cache.get(key);
        if (value != null) {
            try {
                wrapper = (CacheWrapper) value;
                wrapper.setLastAccessTime(System.currentTimeMillis());
            } catch (Exception e) {
                LogUtil.error(e, logger, "获取Map缓存异常,cacheKey={0}", cacheKey);
                return null;
            }
        }

        if (wrapper != null && wrapper.isExpire()) {
            cache.remove(key);
            return null;
        }

        return wrapper;
    }

    @Override
    public Long delete(CacheKey cacheKey) {
        String key;
        if (cacheKey == null || StringUtils.isEmpty(key = cacheKey.getKey())) {
            return zero;
        }
        Object obj = cache.remove(key);

        return obj == null ? zero : one;
    }

    public ConcurrentHashMap<String, Object> getCache() {
        return cache;
    }

    @Override
    public void clear() {
        if (cache != null) {
            cache.clear();
        }
    }

    @Override
    public void shutdown() {
        interruptDaemon();
        daemon.persistCache();
        clear();
    }

    private synchronized void startDaemon() {
        if (daemonThread == null) {
            daemonThread = new Thread(daemon, "map-cache-daemon");
            daemonThread.setDaemon(true);
            daemonThread.start();
        }
    }

    private synchronized void interruptDaemon() {
        daemon.setRun(false);
        if (daemonThread != null) {
            daemonThread.interrupt();
        }
    }

    public CacheConfig getConfig() {
        return config;
    }

}