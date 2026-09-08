package com.erp.oa.mapper;

import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.oa.domain.vo.OaTodoCountRow;

@Mapper
public interface OaTodoMapper
{
    Set<String> selectExistingTodoTables(
            @Param("tableNames") Set<String> tableNames);

    List<TodoItem> selectOaTodoList(
            @Param("query") TodoQuery query,
            @Param("enabledTypes") Set<String> enabledTypes,
            @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
            @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds,
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("approvalUrgentHours") int approvalUrgentHours);

    List<TodoItem> selectRecentOaTodos(
            @Param("query") TodoQuery query,
            @Param("enabledTypes") Set<String> enabledTypes,
            @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
            @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds,
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("approvalUrgentHours") int approvalUrgentHours,
            @Param("limit") int limit);

    List<OaTodoCountRow> selectOaTodoCounts(
            @Param("query") TodoQuery query,
            @Param("enabledTypes") Set<String> enabledTypes,
            @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
            @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds,
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("approvalUrgentHours") int approvalUrgentHours);
}
