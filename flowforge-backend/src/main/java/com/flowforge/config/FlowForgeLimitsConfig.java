package com.flowforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "flowforge.limits")
public class FlowForgeLimitsConfig {

    private Map<String, TypeLimit> types = new HashMap<>();

    public Map<String, TypeLimit> getTypes() {
        return types;
    }

    public void setTypes(Map<String, TypeLimit> types) {
        this.types = types;
    }

    public static class TypeLimit {
        private Integer maxConcurrency;
        private Integer maxRatePerMinute;

        public Integer getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(Integer maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public Integer getMaxRatePerMinute() {
            return maxRatePerMinute;
        }

        public void setMaxRatePerMinute(Integer maxRatePerMinute) {
            this.maxRatePerMinute = maxRatePerMinute;
        }
    }
}
