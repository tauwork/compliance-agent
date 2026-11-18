package com.pcmvp.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pcmvp.audit.service.HashChainService;
import com.pcmvp.llm.DeepSeekClient;
import com.pcmvp.policy.domain.Clause;
import com.pcmvp.policy.domain.ControlPoint;
import com.pcmvp.policy.domain.Obligation;
import com.pcmvp.policy.domain.Policy;
import com.pcmvp.policy.mapper.ClauseMapper;
import com.pcmvp.policy.mapper.ControlPointMapper;
import com.pcmvp.policy.mapper.ObligationMapper;
import com.pcmvp.policy.mapper.PolicyMapper;
import com.pcmvp.rule.domain.Rule;
import com.pcmvp.rule.mapper.RuleMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 政策拆解流水线（M2核心，K1政策拆解五步法落地）：
 *
 *  ① STRUCTURING  全文结构化：按"第X条"正则切分条款（确定性）
 *  ② EXTRACTION   条款要素抽取：条款类型分类（duty/forbid/procedure/authorize）
 *  ③ OBLIGATION   义务清单：义务描述/责任部门/量化阈值/时限
 *  ④ CONTROL_POINT 控制点映射：业务环节（立项/评估/审批/交易）×检查方式（事前/事中/事后）
 *  ⑤ RULE_DRAFT   规则DSL草稿：可量化条款固化为JSON条件数组（status=draft）
 *  ⇒ PENDING_APPROVAL 人工门禁：审批通过→规则批量发布（clause_id回填）、政策置decomposed
 *
 * LLM策略：DeepSeek可用时按提示词抽取（结构化JSON）；不可用时逐级降级为确定性启发式
 * （关键词+正则模板），保证演示链路零外部依赖——判定环节本就不依赖LLM（K2原则）。
 * 每步产出标记 ai/human，人工修正入口见 PUT /api/obligations/{id}。
 */
@Service
public class DecomposeService {

    private final PolicyMapper policyMapper;
    private final ClauseMapper clauseMapper;
    private final ObligationMapper obligationMapper;
    private final ControlPointMapper controlPointMapper;
    private final RuleMapper ruleMapper;
    private final DeepSeekClient llm;
    private final HashChainService chain;
    private final ObjectMapper om = new ObjectMapper();

    public DecomposeService(PolicyMapper policyMapper, ClauseMapper clauseMapper,
                            ObligationMapper obligationMapper, ControlPointMapper controlPointMapper,
                            RuleMapper ruleMapper, DeepSeekClient llm, HashChainService chain) {
        this.policyMapper = policyMapper;
        this.clauseMapper = clauseMapper;
        this.obligationMapper = obligationMapper;
        this.controlPointMapper = controlPointMapper;
        this.ruleMapper = ruleMapper;
        this.llm = llm;
        this.chain = chain;
    }

    /** 触发拆解：同步执行五步（MVP演示同步即可，量级小） */
    @Transactional
    public Map<String, Object> decompose(Long policyId) {
        Policy p = policyMapper.selectById(policyId);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "政策不存在");
        if (p.getSourceText() == null || p.getSourceText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "政策全文为空，无法拆解");
        }
        // 幂等：重复拆解先清旧产物（保留已发布规则）
        clearDecomposeProducts(policyId);

        p.setStatus("decomposing");
        policyMapper.updateById(p);

        // ① 全文结构化（确定性）
        List<Clause> clauses = structuring(p);
        // ②+③+④ 要素抽取/义务/控制点（LLM优先，降级启发式）
        String mode = "deepseek";
        boolean llmOk = false;
        if (llm.isConfigured()) {
            try {
                llmDecompose(p, clauses);
                llmOk = true;
            } catch (Exception e) {
                llmOk = false; // 结构化输出解析失败等 → 降级
            }
        }
        if (!llmOk) {
            mode = "demo-fallback";
            heuristicDecompose(clauses);
        }
        // ⑤ 规则草稿（同模式）
        List<Rule> drafts = mode.equals("deepseek")
                ? ruleDraftsFromLlm(p, clauses)
                : ruleDraftsHeuristic(p, clauses);
        for (Rule r : drafts) {
            ruleMapper.insert(r);
        }

        p.setStatus("decomposed"); // 五级产物齐备，进入待审批（门禁在规则维度）
        policyMapper.updateById(p);

        chain.append("POLICY-" + policyId, "POLICY_DECOMPOSE", "decompose", String.valueOf(policyId),
                Map.of("clauses", clauses.size(), "ruleDrafts", drafts.size(), "mode", mode));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyId", policyId);
        out.put("mode", mode);
        out.put("clauses", clauses.size());
        out.put("obligations", obligationMapper.selectCount(new LambdaQueryWrapper<Obligation>()
                .in(Obligation::getClauseId, clauses.stream().map(Clause::getId).toList())));
        out.put("ruleDrafts", drafts.size());
        out.put("message", "拆解完成，规则草稿待人工门禁审批后发布");
        return out;
    }

    /** 人工门禁审批：approved=true → 全部草稿发布（clause_id回填）；false → 草稿驳回 */
    @Transactional
    public Map<String, Object> approve(Long policyId, boolean approved, String comment) {
        Policy p = policyMapper.selectById(policyId);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "政策不存在");
        List<Rule> drafts = ruleMapper.selectList(new LambdaQueryWrapper<Rule>()
                .eq(Rule::getStatus, "draft").like(Rule::getCode, "D-" + policyId + "-"));
        int published = 0;
        for (Rule r : drafts) {
            r.setStatus(approved ? "published" : "rejected");
            r.touch();
            ruleMapper.updateById(r);
            if (approved) published++;
        }
        chain.append("POLICY-" + policyId, "POLICY_DECOMPOSE", approved ? "approve" : "reject",
                String.valueOf(policyId), Map.of("rules", drafts.size(), "comment", comment == null ? "" : comment));
        return Map.of("policyId", policyId, "approved", approved, "rulesHandled", drafts.size(), "published", published);
    }

    // ---------------- ① 全文结构化（确定性） ----------------

    private static final Pattern CLAUSE_P = Pattern.compile("(第[一二三四五六七八九十百零]+条)");

    List<Clause> structuring(Policy p) {
        List<Clause> out = new ArrayList<>();
        String[] parts = CLAUSE_P.split(p.getSourceText());
        Matcher m = CLAUSE_P.matcher(p.getSourceText());
        List<String> nos = new ArrayList<>();
        while (m.find()) nos.add(m.group(1));
        // parts[0]=前言；nos[i]对应parts[i+1]
        for (int i = 0; i < nos.size(); i++) {
            String content = i + 1 < parts.length ? parts[i + 1].trim() : "";
            content = content.replaceAll("\\s+", " ");
            if (content.isEmpty()) continue;
            Clause c = new Clause();
            c.setPolicyId(p.getId());
            c.setClauseNo(nos.get(i));
            c.setContent(content);
            c.setClauseType(classifyHeuristic(content));
            c.setRevisedBy("ai");
            clauseMapper.insert(c);
            out.add(c);
        }
        return out;
    }

    private void clearDecomposeProducts(Long policyId) {
        List<Clause> old = clauseMapper.selectList(
                new LambdaQueryWrapper<Clause>().eq(Clause::getPolicyId, policyId));
        for (Clause c : old) {
            obligationMapper.delete(new LambdaQueryWrapper<Obligation>().eq(Obligation::getClauseId, c.getId()));
        }
        controlPointMapper.delete(null); // 控制点由义务级联重建（MVP简化：全量重建演示库）
        clauseMapper.delete(new LambdaQueryWrapper<Clause>().eq(Clause::getPolicyId, policyId));
    }

    // ---------------- ②③④ LLM路径 ----------------

    private void llmDecompose(Policy p, List<Clause> clauses) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是《").append(p.getTitle()).append("》的条款列表，请逐条抽取义务并输出JSON数组，每项格式：\n");
        sb.append("{\"clauseNo\":\"第X条\",\"type\":\"duty|forbid|procedure|authorize\",\"obligation\":\"义务描述\",\"dept\":\"责任部门\",\"threshold\":\"量化阈值（无则空）\",\"deadline\":\"时限（无则空）\",\"bizStage\":\"立项|评估|审批|交易|签约\",\"checkMode\":\"事前|事中|事后\"}\n");
        sb.append("只输出JSON数组，不要其他文字。条款列表：\n");
        for (Clause c : clauses) {
            sb.append(c.getClauseNo()).append("：").append(c.getContent()).append("\n");
        }
        String resp = llm.chat("decompose",
                "你是国资监管政策拆解专家，输出严格的JSON，不要markdown代码块。", sb.toString());
        if (resp.startsWith("[LLM_UNAVAILABLE]")) throw new IllegalStateException("llm unavailable");
        JsonNode arr = parseJsonArray(resp);
        if (arr == null) throw new IllegalStateException("llm output not json");
        for (JsonNode n : arr) {
            Clause c = findByNo(clauses, n.path("clauseNo").asText());
            if (c == null) continue;
            if (!n.path("type").asText("").isBlank()) {
                c.setClauseType(n.path("type").asText());
                clauseMapper.updateById(c);
            }
            Obligation o = new Obligation();
            o.setClauseId(c.getId());
            o.setDescription(n.path("obligation").asText(c.getContent()));
            o.setDept(n.path("dept").asText("资产管理部"));
            o.setThreshold(n.path("threshold").asText(""));
            o.setDeadline(n.path("deadline").asText(""));
            o.setRevisedBy("ai");
            obligationMapper.insert(o);
            ControlPoint cp = new ControlPoint();
            cp.setObligationId(o.getId());
            cp.setBizStage(n.path("bizStage").asText("交易"));
            cp.setCheckMode(n.path("checkMode").asText("事前"));
            cp.setBindFields("");
            controlPointMapper.insert(cp);
        }
    }

    private JsonNode parseJsonArray(String s) {
        s = s.trim();
        int a = s.indexOf('[');
        int b = s.lastIndexOf(']');
        if (a < 0 || b <= a) return null;
        try {
            return om.readTree(s.substring(a, b + 1));
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- ②③④ 启发式降级路径 ----------------

    void heuristicDecompose(List<Clause> clauses) {
        for (Clause c : clauses) {
            Obligation o = new Obligation();
            o.setClauseId(c.getId());
            o.setDescription(c.getContent().length() > 200 ? c.getContent().substring(0, 200) : c.getContent());
            o.setDept(c.getContent().contains("决议") || c.getContent().contains("决策") ? "董事会办公室" : "资产管理部");
            o.setThreshold(extract(c.getContent(), "(\\d+(?:\\.\\d+)?)万元"));
            o.setDeadline(extract(c.getContent(), "(\\d+日内|\\d+年内|成交前|交易前|申报前|挂牌前)"));
            o.setRevisedBy("ai");
            obligationMapper.insert(o);
            ControlPoint cp = new ControlPoint();
            cp.setObligationId(o.getId());
            String stage = c.getContent().contains("评估") ? "评估"
                    : (c.getContent().contains("决议") || c.getContent().contains("决策")) ? "审批" : "交易";
            cp.setBizStage(stage);
            cp.setCheckMode("成交价".equals("") || c.getContent().contains("成交") ? "事后" : "事前");
            cp.setBindFields("");
            controlPointMapper.insert(cp);
        }
    }

    private String extract(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(1) : "";
    }

    // ---------------- ⑤ 规则草稿 ----------------

    private List<Rule> ruleDraftsFromLlm(Policy p, List<Clause> clauses) {
        List<Rule> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("以下是条款列表。请仅对可量化判定的条款生成规则DSL草稿，输出JSON数组，每项：\n");
        sb.append("{\"clauseNo\":\"第X条\",\"name\":\"规则名\",\"severity\":\"红|黄|蓝\",\"dsl\":[{\"field\":\"字段名\",\"op\":\"eq|neq|gt|gte|lt|lte|in|lt_field\",\"value\":值}]}\n");
        sb.append("字段与取值域（value必须严格取下列枚举值，禁止自造取值）：\n");
        sb.append("- disposal_mode：字符串枚举，只能取「进场挂牌转让」或「非公开协议转让」\n");
        sb.append("- exemption：布尔\n");
        sb.append("- book_value/appraised_value/listing_price/deal_price：数值（万元）\n");
        sb.append("- resolution_no：字符串，空串表示缺失\n");
        sb.append("- days_since_assessment：数值（天数）\n");
        sb.append("- deal_discount：数值（成交价/挂牌价比值，如0.9）\n");
        sb.append("lt_field的value为另一字段名。\n\n");
        sb.append("【关键】规则DSL描述的是「违规命中条件」——规则命中即预警。示例：\n");
        sb.append("条款「账面价值达到80万元的应当进场交易」的违规情形是非公开协议转让且无豁免且金额达标，故：\n");
        sb.append("{\"clauseNo\":\"第一条\",\"name\":\"应进场未进场\",\"severity\":\"红\",\"dsl\":[{\"field\":\"disposal_mode\",\"op\":\"eq\",\"value\":\"非公开协议转让\"},{\"field\":\"exemption\",\"op\":\"eq\",\"value\":false},{\"field\":\"book_value\",\"op\":\"gte\",\"value\":80}]}\n");
        sb.append("条款「评估结果有效期1年」的违规情形是超期，故：\n");
        sb.append("{\"clauseNo\":\"第二条\",\"name\":\"评估报告超有效期\",\"severity\":\"红\",\"dsl\":[{\"field\":\"days_since_assessment\",\"op\":\"gt\",\"value\":365}]}\n");
        sb.append("条款「应当形成书面决议」的违规情形是决议编号缺失，故：\n");
        sb.append("{\"clauseNo\":\"第三条\",\"name\":\"决策程序要件缺失\",\"severity\":\"黄\",\"dsl\":[{\"field\":\"resolution_no\",\"op\":\"eq\",\"value\":\"\"}]}\n");
        sb.append("注意：合规情形（如已进场、在有效期内、决议齐全）绝不能作为命中条件。只输出JSON数组。\n条款：\n");
        for (Clause c : clauses) sb.append(c.getClauseNo()).append("：").append(c.getContent()).append("\n");
        String resp = llm.chat("decompose", "你是规则引擎专家，输出严格JSON数组，不要markdown。", sb.toString());
        if (resp.startsWith("[LLM_UNAVAILABLE]")) return ruleDraftsHeuristic(p, clauses);
        JsonNode arr = parseJsonArray(resp);
        if (arr == null) return ruleDraftsHeuristic(p, clauses);
        int i = 1;
        for (JsonNode n : arr) {
            Clause c = findByNo(clauses, n.path("clauseNo").asText());
            if (!validateDsl(n.path("dsl"))) {
                continue; // 值域校验不过（LLM幻觉取值）→ 丢弃该草稿
            }
            Rule r = new Rule();
            r.setCode("D-" + p.getId() + "-" + (i++));
            r.setName(n.path("name").asText("规则草稿"));
            r.setSeverity(n.path("severity").asText("黄"));
            try {
                r.setDsl(om.writeValueAsString(n.path("dsl")));
            } catch (Exception e) {
                continue;
            }
            r.setClauseRef(p.getTitle() + c.getClauseNo());
            r.setClauseId(c == null ? null : c.getId());
            r.setScenario("asset");
            r.setStatus("draft");
            r.setEnabled(true);
            r.setVersion(1L);
            out.add(r);
        }
        return out;
    }

    /** 确定性降级：关键词模板生成规则草稿（演示政策全部覆盖） */
    List<Rule> ruleDraftsHeuristic(Policy p, List<Clause> clauses) {
        List<Rule> out = new ArrayList<>();
        int i = 1;
        for (Clause c : clauses) {
            String s = c.getContent();
            Rule r = null;
            if (s.contains("进场") && s.contains("账面价值")) {
                Matcher m = Pattern.compile("账面价值(达到|超过)?(\\d+)万").matcher(s);
                long amount = m.find() ? Long.parseLong(m.group(2)) : 50;
                r = draft(p, c, "D-" + p.getId() + "-" + i, "应进场未进场", "红",
                        List.of(Map.of("field", "disposal_mode", "op", "eq", "value", "非公开协议转让"),
                                Map.of("field", "exemption", "op", "eq", "value", false),
                                Map.of("field", "book_value", "op", "gte", "value", amount)));
            } else if (s.contains("评估") && (s.contains("有效期") || s.contains("1年"))) {
                r = draft(p, c, "D-" + p.getId() + "-" + i, "评估报告超有效期", "红",
                        List.of(Map.of("field", "days_since_assessment", "op", "gt", "value", 365)));
            } else if (s.contains("挂牌价") && s.contains("评估结果")) {
                r = draft(p, c, "D-" + p.getId() + "-" + i, "挂牌价低于评估结果", "红",
                        List.of(Map.of("field", "listing_price", "op", "lt_field", "value", "appraised_value")));
            } else if (s.contains("决议")) {
                r = draft(p, c, "D-" + p.getId() + "-" + i, "决策程序要件缺失", "黄",
                        List.of(Map.of("field", "resolution_no", "op", "eq", "value", "")));
            } else if (s.contains("成交") && s.contains("90%")) {
                r = draft(p, c, "D-" + p.getId() + "-" + i, "成交价显著低于挂牌价", "黄",
                        List.of(Map.of("field", "deal_discount", "op", "lt", "value", 0.9)));
            }
            if (r != null) {
                out.add(r);
                i++;
            }
        }
        return out;
    }

    /**
     * LLM规则草稿确定性校验（防幻觉兜底）：
     * 字段必须在值域表内；disposal_mode的eq/neq取值必须在枚举内；
     * lt_field的value必须是合法字段名。校验不过的草稿不入库。
     */
    private static final java.util.Set<String> FIELDS = java.util.Set.of(
            "disposal_mode", "exemption", "book_value", "appraised_value", "listing_price",
            "deal_price", "resolution_no", "days_since_assessment", "deal_discount");
    private static final java.util.Set<String> DISPOSAL_MODES = java.util.Set.of("进场挂牌转让", "非公开协议转让");

    boolean validateDsl(JsonNode dsl) {
        if (dsl == null || !dsl.isArray() || dsl.isEmpty()) return false;
        for (JsonNode c : dsl) {
            String field = c.path("field").asText();
            String op = c.path("op").asText();
            JsonNode v = c.path("value");
            if (!FIELDS.contains(field)) return false;
            if ("lt_field".equals(op)) {
                if (!FIELDS.contains(v.asText())) return false;
                continue;
            }
            if ("in".equals(op)) {
                if (!v.isArray()) return false;
                continue;
            }
            if ("disposal_mode".equals(field) && ("eq".equals(op) || "neq".equals(op))) {
                if (!DISPOSAL_MODES.contains(v.asText())) return false;
            }
            if (v.isNull() || v.isMissingNode()) return false;
        }
        return true;
    }

    private Rule draft(Policy p, Clause c, String code, String name, String severity, List<Map<String, Object>> dsl) {
        Rule r = new Rule();
        r.setCode(code);
        r.setName(name);
        r.setSeverity(severity);
        try {
            r.setDsl(om.writeValueAsString(dsl));
        } catch (Exception e) {
            r.setDsl("[]");
        }
        r.setClauseRef(p.getTitle() + " " + c.getClauseNo());
        r.setClauseId(c.getId());
        r.setScenario("asset");
        r.setStatus("draft");
        r.setEnabled(true);
        r.setVersion(1L);
        return r;
    }

    private Clause findByNo(List<Clause> clauses, String no) {
        return clauses.stream().filter(c -> c.getClauseNo().equals(no)).findFirst().orElse(null);
    }

    private String classifyHeuristic(String s) {
        if (s.contains("不得") || s.contains("禁止")) return "forbid";
        if (s.contains("决议") || s.contains("程序") || s.contains("决策")) return "procedure";
        if (s.contains("可以") || s.contains("经批准")) return "authorize";
        return "duty";
    }
}
