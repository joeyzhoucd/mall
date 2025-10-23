

package com.mall.common.validator.groupsequence;

import com.mall.common.validator.group.AddGroup;
import com.mall.common.validator.group.UpdateGroup;

import javax.validation.GroupSequence;


@GroupSequence({AddGroup.class, UpdateGroup.class})
public interface Group {

}
