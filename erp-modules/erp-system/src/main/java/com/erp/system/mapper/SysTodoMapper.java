package com.erp.system.mapper;

import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.system.domain.vo.SysTodoCandidateRow;
import com.erp.system.domain.vo.SysHealthCertificateTodoCandidate;

@Mapper
public interface SysTodoMapper
{
    List<SysTodoCandidateRow> selectScopedTodoCandidates(
            @Param("query") TodoQuery query,
            @Param("enabledTypes") Set<String> enabledTypes,
            @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
            @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds);

    List<SysHealthCertificateTodoCandidate> selectHealthCertificateTodoCandidates(
            @Param("query") TodoQuery query,
            @Param("enabledTypes") Set<String> enabledTypes,
            @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
            @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds,
            @Param("userId") Long userId);

    List<Long> selectAllActiveHrDeptIds();
}
