package com.github.sisyphsu.common.cluster.tickid;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TickID's configuration.
 *
 * @author sulin
 * @since 2019-04-15 20:20:47
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.tick")
public class TickProperties {

    /**
     * the default size of every bucket of tick.
     */
    private int defaultBatchSize = 96;
    /**
     * the prefix of zk/redis key.
     */
    private String prefix = "tick";

}
