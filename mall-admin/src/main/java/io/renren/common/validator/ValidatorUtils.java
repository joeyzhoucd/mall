/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 * <p>
 * https://www.renren.io
 * <p>
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.common.validator;

import io.renren.common.exception.RRException;
import io.renren.common.utils.Constant;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

/**
 * hibernate-validatoræ ¡éªŒå·¥å…·ç±»
 *
 * å‚è€ƒæ–‡æ¡£ï¼šhttp://docs.jboss.org/hibernate/validator/5.4/reference/en-US/html_single/
 *
 * @author Mark sunlightcs@gmail.com
 */
public class ValidatorUtils {
    private static Validator validator;

    static {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /**
     * æ ¡éªŒå¯¹è±¡
     * @param object        å¾…æ ¡éªŒå¯¹è±¡
     * @param groups        å¾…æ ¡éªŒçš„ç»„
     * @throws RRException  æ ¡éªŒä¸é€šè¿‡ï¼Œåˆ™æŠ¥RRExceptionå¼‚å¸¸
     */
    public static void validateEntity(Object object, Class<?>... groups)
            throws RRException {
        Set<ConstraintViolation<Object>> constraintViolations = validator.validate(object, groups);
        if (!constraintViolations.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            for (ConstraintViolation<Object> constraint : constraintViolations) {
                msg.append(constraint.getMessage()).append("<br>");
            }
            throw new RRException(msg.toString());
        }
    }

    public static void validateEntity(Object object, Constant.CloudService type) {
        validateEntity(object, type.getValidatorGroupClass());
    }
}
