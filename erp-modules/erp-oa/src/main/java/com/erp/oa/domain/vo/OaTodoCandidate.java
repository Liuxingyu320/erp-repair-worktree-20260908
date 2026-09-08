package com.erp.oa.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.erp.common.core.domain.todo.TodoItem;

/**
 * Internal OA todo projection. Route references are copied into the validated
 * routeParams map by the service and are never exposed as a second API field.
 */
public class OaTodoCandidate extends TodoItem
{
    private static final long serialVersionUID = 1L;

    private String routeReference;

    @JsonIgnore
    public String getRouteReference()
    {
        return routeReference;
    }

    public void setRouteReference(String routeReference)
    {
        this.routeReference = routeReference;
    }
}
