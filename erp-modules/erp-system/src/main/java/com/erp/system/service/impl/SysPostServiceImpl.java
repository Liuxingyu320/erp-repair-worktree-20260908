package com.erp.system.service.impl;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.system.domain.SysPost;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.ISysPostService;

/**
 * 岗位信息 服务层处理
 * 
 * @author erp
 */
@Service
public class SysPostServiceImpl implements ISysPostService
{
    private static final Pattern POSITION_POST_CODE = Pattern.compile("[A-Za-z0-9]{2,16}");

    @Autowired
    private SysPostMapper postMapper;

    @Autowired
    private SysUserPostMapper userPostMapper;

    @Autowired
    private HrMasterDataGateService masterDataGate;

    /**
     * 查询岗位信息集合
     * 
     * @param post 岗位信息
     * @return 岗位信息集合
     */
    @Override
    public List<SysPost> selectPostList(SysPost post)
    {
        return postMapper.selectPostList(post);
    }

    /**
     * 查询所有岗位
     * 
     * @return 岗位列表
     */
    @Override
    public List<SysPost> selectPostAll()
    {
        return postMapper.selectPostAll();
    }

    /**
     * 通过岗位ID查询岗位信息
     * 
     * @param postId 岗位ID
     * @return 角色对象信息
     */
    @Override
    public SysPost selectPostById(Long postId)
    {
        return postMapper.selectPostById(postId);
    }

    /**
     * 根据用户ID获取岗位选择框列表
     * 
     * @param userId 用户ID
     * @return 选中岗位ID列表
     */
    @Override
    public List<Long> selectPostListByUserId(Long userId)
    {
        return postMapper.selectPostListByUserId(userId);
    }

    /**
     * 校验岗位名称是否唯一
     * 
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public boolean checkPostNameUnique(SysPost post)
    {
        Long postId = StringUtils.isNull(post.getPostId()) ? -1L : post.getPostId();
        SysPost info = postMapper.checkPostNameUnique(post.getPostName());
        if (StringUtils.isNotNull(info) && info.getPostId().longValue() != postId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验岗位编码是否唯一
     * 
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public boolean checkPostCodeUnique(SysPost post)
    {
        Long postId = StringUtils.isNull(post.getPostId()) ? -1L : post.getPostId();
        SysPost info = postMapper.checkPostCodeUnique(post.getPostCode());
        if (StringUtils.isNotNull(info) && info.getPostId().longValue() != postId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 通过岗位ID查询岗位使用数量
     * 
     * @param postId 岗位ID
     * @return 结果
     */
    @Override
    public int countUserPostById(Long postId)
    {
        return userPostMapper.countUserPostById(postId);
    }

    /**
     * 删除岗位信息
     * 
     * @param postId 岗位ID
     * @return 结果
     */
    @Override
    public int deletePostById(Long postId)
    {
        if (masterDataGate != null) masterDataGate.validatePostDisableOrDelete(postId);
        return postMapper.deletePostById(postId);
    }

    /**
     * 批量删除岗位信息
     * 
     * @param postIds 需要删除的岗位ID
     * @return 结果
     */
    @Override
    public int deletePostByIds(Long[] postIds)
    {
        for (Long postId : postIds)
        {
            SysPost post = selectPostById(postId);
            if (masterDataGate != null) masterDataGate.validatePostDisableOrDelete(postId);
            if (countUserPostById(postId) > 0)
            {
                throw new ServiceException(String.format("%1$s已分配,不能删除", post.getPostName()));
            }
        }
        return postMapper.deletePostByIds(postIds);
    }

    /**
     * 新增保存岗位信息
     * 
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public int insertPost(SysPost post)
    {
        validatePositionPostCode(post == null ? null : post.getPostCode());
        return postMapper.insertPost(post);
    }

    /**
     * 修改保存岗位信息
     * 
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public int updatePost(SysPost post)
    {
        String requestedCode = post == null ? null : StringUtils.trim(post.getPostCode());
        if (StringUtils.isNotEmpty(requestedCode))
        {
            validatePositionPostCode(requestedCode);
        }
        SysPost stored = post == null || post.getPostId() == null
                ? null : postMapper.selectPostById(post.getPostId());
        if (stored != null && StringUtils.isNotEmpty(requestedCode)
                && !StringUtils.equals(stored.getPostCode(), requestedCode)
                && countUserPostById(post.getPostId()) > 0)
        {
            throw new ServiceException("岗位已分配员工，岗位编码不可修改；如需调整请新建岗位");
        }
        if (post != null && "1".equals(post.getStatus()) && masterDataGate != null)
        {
            if (stored == null || !"1".equals(stored.getStatus()))
                masterDataGate.validatePostDisableOrDelete(post.getPostId());
        }
        return postMapper.updatePost(post);
    }

    private void validatePositionPostCode(String postCode)
    {
        String normalized = StringUtils.trim(postCode);
        if (StringUtils.isEmpty(normalized) || !POSITION_POST_CODE.matcher(normalized).matches())
        {
            throw new ServiceException("岗位编码必须为2至16位字母或数字");
        }
    }
}
