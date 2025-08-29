package com.joeyzhoucd.common.validator.groupsequence;

import com.joeyzhoucd.common.validator.group.AddGroup;

import javax.validation.GroupSequence;
import javax.validation.groups.Default;

@GroupSequence({Default.class, AddGroup.class})
public interface DAddGroup {
}
