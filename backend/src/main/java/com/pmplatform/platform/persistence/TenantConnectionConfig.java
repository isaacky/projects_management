package com.pmplatform.platform.persistence;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wraps the application's connection pool in {@link TenantConnectionDataSource}. (On servers Flyway
 * has its own URL and owner credentials and builds a separate connection, which needs no tenant.)
 */
@Configuration
public class TenantConnectionConfig {

    @Bean
    static BeanPostProcessor tenantConnectionDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource ds && !(bean instanceof TenantConnectionDataSource)
                        ? new TenantConnectionDataSource(ds)
                        : bean;
            }
        };
    }
}
