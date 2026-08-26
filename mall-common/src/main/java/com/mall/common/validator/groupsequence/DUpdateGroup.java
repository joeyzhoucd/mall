package com.mall.common.validator.groupsequence;

import com.mall.common.validator.group.UpdateGroup;

import jakarta.validation.GroupSequence;
import jakarta.validation.groups.Default;

@GroupSequence({Default.class, UpdateGroup.class})
public interface DUpdateGroup {
}
