package com.mall.common.validator.groupsequence;

import com.mall.common.validator.group.AddGroup;

import javax.validation.GroupSequence;
import javax.validation.groups.Default;

@GroupSequence({Default.class, AddGroup.class})
public interface DAddGroup {
}
