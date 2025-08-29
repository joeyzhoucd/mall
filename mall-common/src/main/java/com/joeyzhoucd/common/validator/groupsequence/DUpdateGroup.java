package com.joeyzhoucd.common.validator.groupsequence;

import com.joeyzhoucd.common.validator.group.UpdateGroup;

import javax.validation.GroupSequence;
import javax.validation.groups.Default;

@GroupSequence({Default.class, UpdateGroup.class})
public interface DUpdateGroup {
}
