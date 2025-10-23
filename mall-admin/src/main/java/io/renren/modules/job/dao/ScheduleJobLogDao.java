

package io.renren.modules.job.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.job.entity.ScheduleJobLogEntity;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface ScheduleJobLogDao extends BaseMapper<ScheduleJobLogEntity> {
	
}
