package com.github.sisyphsu.common.cluster;

import com.github.sisyphsu.common.cluster.cid.ClusterID;
import com.github.sisyphsu.common.cluster.cid.ClusterIDImpl;
import com.github.sisyphsu.common.cluster.cid.ClusterIDProperties;
import com.github.sisyphsu.common.cluster.dlock.DistributedLock;
import com.github.sisyphsu.common.cluster.dlock.DistributedLockProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.AuthInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Execute auto configure for CuratorFramework, ClusterID, DistributedLock
 *
 * @author sulin
 * @since 2019-03-22 12:08:05
 */
@Slf4j
@ComponentScan
@Configuration
@Import({RedisAutoConfiguration.class})
@EnableAutoConfiguration
public class ClusterAutoConfiguration {

    @Autowired
    private ApplicationContext context;

    @Bean
    @Autowired
    @ConditionalOnBean(ZookeeperProperties.class)
    public CuratorFramework createCuratorFramework(ZookeeperProperties props) {
        if (StringUtils.isEmpty(props.getAddr())) {
            return null;
        }
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder();
        builder.connectString(props.getAddr());
        builder.namespace(StringUtils.isEmpty(props.getNamespace()) ? context.getId() : props.getNamespace());
        builder.retryPolicy(new RetryNTimes(props.getRetryTimes(), props.getRetryInterval()));
        builder.connectionTimeoutMs(props.getConnectionTimeoutMs());
        builder.sessionTimeoutMs(props.getSessionTimeoutMs());
        if (CollectionUtils.isNotEmpty(props.getAuthInfos())) {
            List<AuthInfo> authInfoList = new ArrayList<>();
            for (ZookeeperProperties.AuthInfo authInfo : props.getAuthInfos()) {
                authInfoList.add(new AuthInfo(authInfo.getScheme(), Base64.getDecoder().decode(authInfo.getAuth())));
            }
            builder.authorization(authInfoList);
        }
        // build && start
        CuratorFramework cf = builder.build();
        cf.start();
        return cf;
    }

    @Bean
    @Autowired
    @ConditionalOnBean(CuratorFramework.class)
    @ConditionalOnMissingBean(ClusterID.class)
    public ClusterID clusterID(CuratorFramework framework, ClusterIDProperties props) {
        return new ClusterIDImpl(framework, props);
    }

    @Bean
    @Autowired
    @ConditionalOnBean({StringRedisTemplate.class})
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock createDLock(StringRedisTemplate template, DistributedLockProperties props) {
        return new DistributedLock(template, props);
    }

}
