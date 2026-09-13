package ua.coffeetamine.common.identity;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Spring caching wiring for {@link CachingUserIdentityResolver}. Caffeine handles eviction (LRU +
 * TTL); {@link TransactionAwareCacheManagerProxy} defers every {@code cache.put} to the surrounding
 * transaction's {@code afterCommit} hook when the resolution call originates inside an outer
 * transaction — without it, a rolled-back outer transaction would leak an {@code app_user_id} into
 * the cache for a row that no longer exists, and the next request for the same {@code (iss, sub)}
 * would return a stale id pointing at nothing.
 */
@Configuration
@EnableCaching
public class IdentityCacheConfig {

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager underlying =
        new CaffeineCacheManager(CachingUserIdentityResolver.CACHE_NAME);
    underlying.setCaffeine(
        Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats());
    return new TransactionAwareCacheManagerProxy(underlying);
  }
}
