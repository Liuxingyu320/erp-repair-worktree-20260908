package com.erp.inventory.mapper;

import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.inventory.domain.vo.InvTodoCountRow;

@Mapper
public interface InvTodoMapper
{
    List<TodoItem> selectInventoryTodoList(
            @Param("query") TodoQuery query,
            @Param("enabledTypes") Set<String> enabledTypes,
            @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
            @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds,
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("approvalUrgentHours") int approvalUrgentHours,
            @Param("stockCheckDueSoonHours") int stockCheckDueSoonHours);

    List<TodoItem> selectRecentInventoryTodos(
            @Param("query") TodoQuery query,
            @Param("enabledTypes") Set<String> enabledTypes,
            @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
            @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds,
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("approvalUrgentHours") int approvalUrgentHours,
            @Param("stockCheckDueSoonHours") int stockCheckDueSoonHours,
            @Param("limit") int limit);

    List<InvTodoCountRow> selectInventoryTodoCounts(
            @Param("query") TodoQuery query,
            @Param("enabledTypes") Set<String> enabledTypes,
            @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
            @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds,
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("approvalUrgentHours") int approvalUrgentHours,
            @Param("stockCheckDueSoonHours") int stockCheckDueSoonHours);
}
