

package io.renren.common.validator;

import io.renren.common.exception.RRException;
import io.renren.common.utils.Constant;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;


public class ValidatorUtils {
    private static Validator validator;

    static {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    
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
