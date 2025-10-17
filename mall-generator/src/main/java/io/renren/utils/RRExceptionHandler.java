package io.renren.utils;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * å¼‚å¸¸å¤„ç†å™¨
 * 
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2016å¹´10æœˆ27æ—¥ ä¸‹åˆ10:16:19
 */
@Component
public class RRExceptionHandler implements HandlerExceptionResolver {
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	@Override
	public ModelAndView resolveException(HttpServletRequest request,
			HttpServletResponse response, Object handler, Exception ex) {
		R r = new R();
		try {
			response.setContentType("application/json;charset=utf-8");
			response.setCharacterEncoding("utf-8");
			
			if (ex instanceof RRException) {
				r.put("code", ((RRException) ex).getCode());
				r.put("msg", ((RRException) ex).getMessage());
			}else if(ex instanceof DuplicateKeyException){
				r = R.error("æ•°æ®åº“ä¸­å·²å­˜åœ¨è¯¥è®°å½•");
			}else{
				r = R.error();
			}
			
			//è®°å½•å¼‚å¸¸æ—¥å¿—
			logger.error(ex.getMessage(), ex);
			
			String json = JSON.toJSONString(r);
			response.getWriter().print(json);
		} catch (Exception e) {
			logger.error("RRExceptionHandler å¼‚å¸¸å¤„ç†å¤±è´¥", e);
		}
		return new ModelAndView();
	}
}
