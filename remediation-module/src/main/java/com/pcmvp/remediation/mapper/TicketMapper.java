package com.pcmvp.remediation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcmvp.remediation.domain.Ticket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
}
