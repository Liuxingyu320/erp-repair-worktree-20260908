package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.system.domain.SysPost;
import com.erp.system.mapper.SysPostMapper;

class SysPostServiceImplTest
{
    @Test
    void disablingPostRunsResourceGateBeforeDatabaseWrite()
    {
        SysPostMapper postMapper=mock(SysPostMapper.class);
        HrMasterDataGateService gate=mock(HrMasterDataGateService.class);
        SysPost stored=post(9L,"0");
        SysPost change=post(9L,"1");
        when(postMapper.selectPostById(9L)).thenReturn(stored);
        when(postMapper.updatePost(change)).thenReturn(1);
        SysPostServiceImpl service=new SysPostServiceImpl();
        ReflectionTestUtils.setField(service,"postMapper",postMapper);
        ReflectionTestUtils.setField(service,"masterDataGate",gate);

        assertThat(service.updatePost(change)).isEqualTo(1);

        InOrder order=inOrder(postMapper,gate);
        order.verify(postMapper).selectPostById(9L);
        order.verify(gate).validatePostDisableOrDelete(9L);
        order.verify(postMapper).updatePost(change);
    }

    private static SysPost post(Long id,String status)
    {
        SysPost post=new SysPost();post.setPostId(id);post.setPostName("岗位"+id);post.setStatus(status);return post;
    }
}
