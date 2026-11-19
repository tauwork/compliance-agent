package com.pcmvp.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcmvp.llm.DeepSeekClient;
import com.pcmvp.policy.domain.Clause;
import com.pcmvp.policy.domain.Obligation;
import com.pcmvp.policy.domain.Policy;
import com.pcmvp.policy.domain.PolicyQa;
import com.pcmvp.policy.mapper.ClauseMapper;
import com.pcmvp.policy.mapper.ObligationMapper;
import com.pcmvp.policy.mapper.PolicyMapper;
import com.pcmvp.policy.mapper.PolicyQaMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话式合规助手（M3）：条款级问答，答案溯源到原文条号。
 * 检索：MySQL关键词召回（LIKE，MVP简化RAG；向量化进二期）。
 * 生成：DeepSeek可用→基于召回条款组织回答；降级→直接返回条款原文（保证可溯源）。
 */
@Service
public class ChatService {

    private final ClauseMapper clauseMapper;
    private final PolicyMapper policyMapper;
    private final ObligationMapper obligationMapper;
    private final PolicyQaMapper qaMapper;
    private final DeepSeekClient llm;

    public ChatService(ClauseMapper clauseMapper, PolicyMapper policyMapper,
                       ObligationMapper obligationMapper, PolicyQaMapper qaMapper, DeepSeekClient llm) {
        this.clauseMapper = clauseMapper;
        this.policyMapper = policyMapper;
        this.obligationMapper = obligationMapper;
        this.qaMapper = qaMapper;
        this.llm = llm;
    }

    public Map<String, Object> chat(String question) {
        // 1. 关键词召回（按条款内容LIKE，取前5条）
        List<Clause> hits = recall(question);
        List<PolicyQa> qaHits = recallQa(question);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("question", question);

        if (hits.isEmpty() && qaHits.isEmpty()) {
            out.put("answer", "未在政策库中检索到相关条款。请换一个问法，或联系合规部门确认。");
            out.put("sources", List.of());
            return out;
        }

        List<Map<String, String>> sources = new ArrayList<>();
        StringBuilder ctx = new StringBuilder();
        for (Clause c : hits) {
            Policy p = policyMapper.selectById(c.getPolicyId());
            List<Obligation> obs = obligationMapper.selectList(
                    new LambdaQueryWrapper<Obligation>().eq(Obligation::getClauseId, c.getId()));
            String policyTitle = p == null ? "" : p.getTitle();
            sources.add(Map.of("clauseNo", c.getClauseNo(), "policy", policyTitle,
                    "content", c.getContent()));
            ctx.append("【").append(policyTitle).append(" ").append(c.getClauseNo()).append("】")
                    .append(c.getContent()).append("\n");
            for (Obligation o : obs) {
                ctx.append("（义务：").append(o.getDescription())
                        .append(o.getThreshold() != null && !o.getThreshold().isBlank() ? "；阈值：" + o.getThreshold() : "")
                        .append("）\n");
            }
        }
        for (PolicyQa qa : qaHits) {
            Policy p = policyMapper.selectById(qa.getPolicyId());
            String policyTitle = p == null ? "" : p.getTitle();
            sources.add(Map.of("clauseNo", "问答", "policy", policyTitle, "content", qa.getAnswer()));
            ctx.append("【").append(policyTitle).append(" 问答】问：").append(qa.getQuestion())
                    .append(" 答：").append(qa.getAnswer()).append("\n");
        }

        String answer;
        String mode = "deepseek";
        String llmOut = null;
        if (llm.isConfigured()) {
            try {
                llmOut = llm.chat("chat",
                        "你是政策合规问答助手。仅依据给出的政策条款回答，不得编造；回答末尾不要自行罗列来源。",
                        "问题：" + question + "\n\n相关条款：\n" + ctx);
            } catch (Exception ignored) {
                llmOut = null;
            }
        }
        if (llmOut != null && !llmOut.startsWith("[LLM_UNAVAILABLE]")) {
            answer = llmOut;
        } else {
            mode = "demo-fallback";
            if (!hits.isEmpty()) {
                Clause top = hits.get(0);
                Policy p = policyMapper.selectById(top.getPolicyId());
                answer = "根据《" + (p == null ? "" : p.getTitle()) + "》" + top.getClauseNo() + "："
                        + top.getContent();
            } else {
                PolicyQa top = qaHits.get(0);
                Policy p = policyMapper.selectById(top.getPolicyId());
                answer = "根据《" + (p == null ? "" : p.getTitle()) + "》问答：" + top.getAnswer();
            }
        }
        out.put("answer", answer);
        out.put("sources", sources);
        out.put("mode", mode);
        return out;
    }

    private List<Clause> recall(String q) {
        // 抽取问题中的关键词做LIKE召回，按命中关键词数排序（多命中优先）
        List<String> keys = new ArrayList<>();
        for (String w : List.of("进场", "挂牌", "评估", "决议", "成交", "转让", "处置", "担保",
                "租赁", "豁免", "批准", "备案", "有效期", "账面价值", "非公开协议", "重新评估", "重新决策",
                "不进场", "免于进场", "交易机构", "产权交易")) {
            if (q.contains(w) && !keys.contains(w)) keys.add(w);
        }
        if (keys.isEmpty()) {
            for (int i = 0; i + 2 <= q.length() && keys.size() < 2; i++) {
                keys.add(q.substring(i, i + 2));
            }
        }
        Map<Clause, Integer> score = new LinkedHashMap<>();
        for (String k : keys) {
            for (Clause c : clauseMapper.selectList(new LambdaQueryWrapper<Clause>()
                    .like(Clause::getContent, k).last("LIMIT 20"))) {
                score.merge(c, 1, Integer::sum);
            }
        }
        return score.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<PolicyQa> recallQa(String q) {
        List<String> keys = new ArrayList<>();
        for (String w : List.of("进场", "挂牌", "评估", "决议", "担保", "租赁", "有效期", "竞价", "租金", "反担保")) {
            if (q.contains(w)) keys.add(w);
        }
        if (keys.isEmpty() && q.length() >= 2) keys.add(q.substring(0, Math.min(4, q.length())));
        Map<PolicyQa, Integer> score = new LinkedHashMap<>();
        for (String k : keys) {
            for (PolicyQa qa : qaMapper.selectList(new LambdaQueryWrapper<PolicyQa>()
                    .like(PolicyQa::getQuestion, k).or().like(PolicyQa::getAnswer, k).last("LIMIT 10"))) {
                score.merge(qa, 1, Integer::sum);
            }
        }
        return score.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
    }
}
