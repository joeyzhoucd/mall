package com.mall.common.validator.groupsequence;

import com.mall.common.validator.group.UpdateGroup;

import javax.validation.GroupSequence;
import javax.validation.groups.Default;

@GroupSequence({Default.class, UpdateGroup.class})
public interface DUpdateGroup {
}
