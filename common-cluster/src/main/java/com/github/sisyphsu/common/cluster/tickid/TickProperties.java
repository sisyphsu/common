package com.github.sisyphsu.common.cluster.tickid;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author sulin
 * @since 2019-04-15 20:20:47
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.tick")
public class TickProperties {
}
