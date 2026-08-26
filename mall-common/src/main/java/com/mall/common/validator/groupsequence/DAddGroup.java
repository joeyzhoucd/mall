package com.mall.common.validator.groupsequence;

import com.mall.common.validator.group.AddGroup;

import jakarta.validation.GroupSequence;
import jakarta.validation.groups.Default;

@GroupSequence({Default.class, AddGroup.class})
public interface DAddGroup {
}
