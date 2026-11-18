package com.pcmvp.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcmvp.rule.domain.Rule;
import com.pcmvp.rule.mapper.RuleMapper;
import com.pcmvp.rule.model.RuleHit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 规则评估服务：对规则DSL（JSON条件数组，全部AND）做进程内确定性解释执行。
 *
 * 实现说明（M1架构决策）：
 *  - 原方案为 DSL→DRL→Drools（RETE）。开发环境JDK 23下 Drools 7.74 对 Map 事实
 *    内联谓词编译存在确定性缺陷（RuleBuildContext.getDialect() NPE，java/mvel方言均复现），
 *    故M1改为内置DSL解释器直接执行同一份JSON DSL。
 *  - 判定语义完全等价且严格确定：同一数据+同一规则必得同一结论（K2原则），无LLM参与；
 *  - DSL算子：eq/neq/gt/gte/lt/lte/in/lt_field（字段间比较）；条件为空视为不命中；
 *  - null字段不命中任何比较条件（guard语义，与原Drools生成逻辑一致）；
 *  - 后续如需RETE性能优化，可在JDK 21运行时环境下恢复Drools内核，DSL无需变更。
 */
@Service
public class DroolsEvalService {

    private final RuleMapper ruleMapper;
    private final ObjectMapper om = new ObjectMapper();

    public DroolsEvalService(RuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    private List<Rule> effectiveRules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<Rule>()
                .eq(Rule::getStatus, "published")
                .eq(Rule::getEnabled, true));
    }

    /** 场景过滤：仅评估该场景的已发布规则（null=全部场景，遗留接口兼容） */
    private List<Rule> effectiveRules(String scenario) {
        if (scenario == null || scenario.isBlank()) {
            return effectiveRules();
        }
        return ruleMapper.selectList(new LambdaQueryWrapper<Rule>()
                .eq(Rule::getStatus, "published")
                .eq(Rule::getEnabled, true)
                .eq(Rule::getScenario, scenario));
    }

    /** 兼容既有热更新语义：规则库变更后无需失效缓存（每次评估实时读库+解释执行） */
    public void invalidate() {
        // no-op：内置解释器无编译缓存
    }

    /** 对一批事实执行全部已发布规则（全场景，遗留兼容） */
    public List<RuleHit> eval(List<Map<String, Object>> facts) {
        return eval(facts, null);
    }

    /** 对一批事实执行指定场景的已发布规则（场景隔离） */
    public List<RuleHit> eval(List<Map<String, Object>> facts, String scenario) {
        List<RuleHit> hits = new ArrayList<>();
        if (facts == null || facts.isEmpty()) {
            return hits;
        }
        List<Rule> rules = effectiveRules(scenario);
        for (Rule r : rules) {
            List<Map<String, Object>> conds;
            try {
                conds = om.readValue(r.getDsl(), new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                throw new IllegalStateException("规则 " + r.getCode() + " DSL解析失败: " + e.getMessage());
            }
            for (int i = 0; i < facts.size(); i++) {
                if (match(conds, facts.get(i))) {
                    hits.add(new RuleHit(r.getCode(), r.getName(), r.getSeverity(), i));
                }
            }
        }
        return hits;
    }

    /** 全部条件AND；任一字段为null则该条件不命中 */
    boolean match(List<Map<String, Object>> conds, Map<String, Object> fact) {
        for (Map<String, Object> c : conds) {
            if (!matchOne(c, fact)) {
                return false;
            }
        }
        return !conds.isEmpty();
    }

    private boolean matchOne(Map<String, Object> c, Map<String, Object> fact) {
        String field = String.valueOf(c.get("field"));
        String op = String.valueOf(c.get("op"));
        Object value = c.get("value");
        Object v = fact.get(field);
        if (v == null) {
            return false;
        }
        return switch (op) {
            case "eq" -> eq(v, value);
            case "neq" -> !eq(v, value);
            case "gt" -> cmp(v, value) > 0;
            case "gte" -> cmp(v, value) >= 0;
            case "lt" -> cmp(v, value) < 0;
            case "lte" -> cmp(v, value) <= 0;
            case "lt_field" -> {
                Object other = fact.get(String.valueOf(value));
                yield other != null && cmp(v, other) < 0;
            }
            case "in" -> {
                if (!(value instanceof List<?> vals)) yield true;
                yield vals.stream().anyMatch(x -> eq(v, x));
            }
            default -> throw new IllegalArgumentException("不支持的算子: " + op);
        };
    }

    /** 相等比较：数值统一转double比较，其余equals */
    private boolean eq(Object v, Object value) {
        if (v instanceof Number a && value instanceof Number b) {
            return a.doubleValue() == b.doubleValue();
        }
        return v.equals(value);
    }

    /** 数值比较（BigDecimal/Integer/Double统一转double） */
    @SuppressWarnings("unchecked")
    private int cmp(Object v, Object value) {
        if (v instanceof Number a && value instanceof Number b) {
            return Double.compare(a.doubleValue(), b.doubleValue());
        }
        if (v instanceof Comparable a && value instanceof Comparable b
                && v.getClass() == value.getClass()) {
            return a.compareTo(b);
        }
        throw new IllegalArgumentException("不可比较的类型: " + v + " vs " + value);
    }
}
