

package io.renren.modules.job.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.job.entity.ScheduleJobEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;


@Mapper
public interface ScheduleJobDao extends BaseMapper<ScheduleJobEntity> {
	
	
	int updateBatch(Map<String, Object> map);
}
