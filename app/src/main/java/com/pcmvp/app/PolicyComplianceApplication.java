package com.pcmvp.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 政策合规智能体 MVP（单体应用，唯一部署单元）。
 * 六业务模块进程内聚合：policy / rule / audit / remediation / agent / llm。
 */
@SpringBootApplication(scanBasePackages = "com.pcmvp")
@MapperScan({"com.pcmvp.rule.mapper", "com.pcmvp.audit.mapper", "com.pcmvp.remediation.mapper",
        "com.pcmvp.policy.mapper", "com.pcmvp.agent.mapper", "com.pcmvp.llm", "com.pcmvp.system.mapper"})
public class PolicyComplianceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyComplianceApplication.class, args);
    }
}
