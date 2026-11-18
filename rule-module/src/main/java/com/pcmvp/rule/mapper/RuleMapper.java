package com.pcmvp.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcmvp.rule.domain.Rule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuleMapper extends BaseMapper<Rule> {
}
