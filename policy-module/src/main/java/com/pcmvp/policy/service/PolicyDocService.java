package com.pcmvp.policy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pcmvp.policy.domain.Policy;
import com.pcmvp.policy.domain.PolicyChunk;
import com.pcmvp.policy.domain.PolicyQa;
import com.pcmvp.policy.mapper.PolicyChunkMapper;
import com.pcmvp.policy.mapper.PolicyMapper;
import com.pcmvp.policy.mapper.PolicyQaMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 政策文档的分片与问答。分片按「第X条」切开，没有条款号时按段落切。
 * 问答从分片标题和正文生成，人工录入的问答在重新生成时保留。
 */
@Service
public class PolicyDocService {

    private static final Pattern CLAUSE_START = Pattern.compile("(?m)(?=第[一二三四五六七八九十百零]+条)");
    private static final Pattern THEME = Pattern.compile("第[一二三四五六七八九十百零]+条[（(]([^）)]+)[）)]");

    private final PolicyMapper policyMapper;
    private final PolicyChunkMapper chunkMapper;
    private final PolicyQaMapper qaMapper;

    public PolicyDocService(PolicyMapper policyMapper, PolicyChunkMapper chunkMapper, PolicyQaMapper qaMapper) {
        this.policyMapper = policyMapper;
        this.chunkMapper = chunkMapper;
        this.qaMapper = qaMapper;
    }

    @Transactional
    public List<PolicyChunk> chunks(Long policyId) {
        requirePolicy(policyId);
        List<PolicyChunk> rows = listChunks(policyId);
        if (rows.isEmpty()) {
            rebuild(policyId);
            rows = listChunks(policyId);
        }
        return rows;
    }

    @Transactional
    public List<PolicyChunk> rebuild(Long policyId) {
        Policy policy = requirePolicy(policyId);
        chunkMapper.delete(new LambdaQueryWrapper<PolicyChunk>().eq(PolicyChunk::getPolicyId, policyId));
        qaMapper.update(null, new LambdaUpdateWrapper<PolicyQa>()
                .eq(PolicyQa::getPolicyId, policyId)
                .set(PolicyQa::getChunkId, null));
        int seq = 1;
        for (String piece : split(policy.getSourceText())) {
            PolicyChunk chunk = new PolicyChunk();
            chunk.setPolicyId(policyId);
            chunk.setSeq(seq++);
            chunk.setHeading(headingOf(piece));
            chunk.setContent(piece);
            chunk.setCharCount(piece.length());
            chunk.setEnabled(true);
            chunk.setCreatedAt(LocalDateTime.now());
            chunkMapper.insert(chunk);
        }
        return listChunks(policyId);
    }

    @Transactional
    public PolicyChunk saveChunk(Long policyId, Long chunkId, String content, Boolean enabled) {
        PolicyChunk chunk = requireChunk(policyId, chunkId);
        if (content != null) {
            chunk.setContent(content);
            chunk.setHeading(headingOf(content));
            chunk.setCharCount(content.length());
        }
        if (enabled != null) chunk.setEnabled(enabled);
        chunkMapper.updateById(chunk);
        return chunk;
    }

    @Transactional
    public void deleteChunk(Long policyId, Long chunkId) {
        requireChunk(policyId, chunkId);
        chunkMapper.deleteById(chunkId);
        qaMapper.update(null, new LambdaUpdateWrapper<PolicyQa>()
                .eq(PolicyQa::getChunkId, chunkId)
                .set(PolicyQa::getChunkId, null));
    }

    public List<PolicyQa> qas(Long policyId) {
        requirePolicy(policyId);
        return qaMapper.selectList(new LambdaQueryWrapper<PolicyQa>()
                .eq(PolicyQa::getPolicyId, policyId)
                .orderByAsc(PolicyQa::getId));
    }

    /** replace=true 时清掉 AI 生成的问答再按当前分片重写，人工问答保留。 */
    @Transactional
    public List<PolicyQa> generateQas(Long policyId, boolean replace) {
        Policy policy = requirePolicy(policyId);
        List<PolicyChunk> chunks = chunks(policyId);
        if (replace) {
            qaMapper.delete(new LambdaQueryWrapper<PolicyQa>()
                    .eq(PolicyQa::getPolicyId, policyId)
                    .eq(PolicyQa::getSource, "ai"));
        } else if (!qas(policyId).isEmpty()) {
            return qas(policyId);
        }
        for (PolicyChunk chunk : chunks) {
            if (!Boolean.TRUE.equals(chunk.getEnabled())) continue;
            String theme = themeOf(chunk.getHeading());
            PolicyQa qa = new PolicyQa();
            qa.setPolicyId(policyId);
            qa.setChunkId(chunk.getId());
            qa.setQuestion("《" + policy.getTitle() + "》中，" + theme + "是如何规定的？");
            qa.setAnswer(trim(chunk.getContent(), 800));
            qa.setSource("ai");
            qa.setCreatedAt(LocalDateTime.now());
            qaMapper.insert(qa);
        }
        return qas(policyId);
    }

    @Transactional
    public PolicyQa addQa(Long policyId, String question, String answer) {
        requirePolicy(policyId);
        if (question == null || question.isBlank() || answer == null || answer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题和答案都要填");
        }
        PolicyQa qa = new PolicyQa();
        qa.setPolicyId(policyId);
        qa.setQuestion(question.trim());
        qa.setAnswer(answer.trim());
        qa.setSource("human");
        qa.setCreatedAt(LocalDateTime.now());
        qaMapper.insert(qa);
        return qa;
    }

    @Transactional
    public void deleteQa(Long policyId, Long qaId) {
        PolicyQa qa = qaMapper.selectById(qaId);
        if (qa == null || !policyId.equals(qa.getPolicyId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问答不存在");
        }
        qaMapper.deleteById(qaId);
    }

    private List<PolicyChunk> listChunks(Long policyId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<PolicyChunk>()
                .eq(PolicyChunk::getPolicyId, policyId)
                .orderByAsc(PolicyChunk::getSeq));
    }

    private Policy requirePolicy(Long id) {
        Policy p = policyMapper.selectById(id);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "政策不存在");
        return p;
    }

    private PolicyChunk requireChunk(Long policyId, Long chunkId) {
        PolicyChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null || !policyId.equals(chunk.getPolicyId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分片不存在");
        }
        return chunk;
    }

    static List<String> split(String text) {
        List<String> segs = new ArrayList<>();
        if (text == null || text.isBlank()) return segs;
        for (String part : CLAUSE_START.split(text)) {
            if (part != null && !part.isBlank()) segs.add(part.trim());
        }
        if (segs.size() > 1) return segs;
        List<String> paras = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String line : text.split("\\n")) {
            if (line.isBlank() && buf.length() > 0) {
                paras.add(buf.toString().trim());
                buf.setLength(0);
            } else {
                buf.append(line).append('\n');
            }
        }
        if (buf.length() > 0) paras.add(buf.toString().trim());
        return paras.size() > 1 ? paras : segs;
    }

    private static String headingOf(String piece) {
        String line = piece.lines().findFirst().orElse("分片").trim();
        return line.length() > 80 ? line.substring(0, 80) : line;
    }

    private static String themeOf(String heading) {
        if (heading == null || heading.isBlank()) return "本条";
        Matcher m = THEME.matcher(heading);
        if (m.find()) return m.group(1);
        String stripped = heading.replaceFirst("^第[一二三四五六七八九十百零]+条", "").trim();
        return stripped.isBlank() ? heading : stripped;
    }

    private static String trim(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }
}
