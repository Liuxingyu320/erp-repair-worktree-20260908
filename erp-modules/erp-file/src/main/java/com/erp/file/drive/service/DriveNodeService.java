package com.erp.file.drive.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.dto.DriveFolderCreateRequest;
import com.erp.file.drive.domain.dto.DriveMoveRequest;
import com.erp.file.drive.domain.dto.DriveRenameRequest;
import com.erp.file.drive.domain.vo.DriveBreadcrumbVo;
import com.erp.file.drive.domain.vo.DriveNodeVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveNodeMapper;
import com.erp.file.drive.mapper.DriveOperationLogMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriveNodeService
{
    private final DriveNodeMapper nodeMapper;
    private final DriveSpaceMapper spaceMapper;
    private final DriveSpaceService spaceService;
    private final DriveAuthorizationService authorization;
    private final DriveNamePolicy namePolicy;
    private final DriveFilePolicy filePolicy;
    private final DriveOperationLogService logService;
    private final DriveOperationLogMapper operationLogMapper;

    public DriveNodeService(DriveNodeMapper nodeMapper, DriveSpaceMapper spaceMapper,
            DriveSpaceService spaceService, DriveAuthorizationService authorization,
            DriveNamePolicy namePolicy, DriveFilePolicy filePolicy,
            DriveOperationLogService logService, DriveOperationLogMapper operationLogMapper)
    {
        this.nodeMapper = nodeMapper;
        this.spaceMapper = spaceMapper;
        this.spaceService = spaceService;
        this.authorization = authorization;
        this.namePolicy = namePolicy;
        this.filePolicy = filePolicy;
        this.logService = logService;
        this.operationLogMapper = operationLogMapper;
    }

    public List<DriveNodeVo> list(Long spaceId, Long parentId, String keyword,
            String sortField, String sortDirection, DriveActor actor)
    {
        DriveSpace space = spaceService.requireReadableSpace(spaceId, actor);
        Long effectiveParent = parentId == null ? DriveConstants.ROOT_PARENT_ID : parentId;
        String normalizedSort = normalizeSortField(sortField);
        String normalizedDirection = normalizeSortDirection(sortDirection);
        String keywordPattern = namePolicy.searchPattern(keyword);
        if (keywordPattern.isBlank())
        {
            requireParent(spaceId, effectiveParent);
        }
        return queryNodes(space, effectiveParent, keywordPattern,
                normalizedSort, normalizedDirection, actor);
    }

    public List<DriveNodeVo> listPaged(Long spaceId, Long parentId, String keyword,
            String sortField, String sortDirection, DriveActor actor,
            int pageNum, int pageSize)
    {
        DriveSpace space = spaceService.requireReadableSpace(spaceId, actor);
        Long effectiveParent = parentId == null ? DriveConstants.ROOT_PARENT_ID : parentId;
        String normalizedSort = normalizeSortField(sortField);
        String normalizedDirection = normalizeSortDirection(sortDirection);
        String keywordPattern = namePolicy.searchPattern(keyword);
        if (keywordPattern.isBlank())
        {
            requireParent(spaceId, effectiveParent);
        }

        PageHelper.startPage(pageNum, pageSize);
        try
        {
            return queryNodes(space, effectiveParent, keywordPattern,
                    normalizedSort, normalizedDirection, actor);
        }
        finally
        {
            PageHelper.clearPage();
        }
    }

    public DriveNodeVo detail(Long nodeId, DriveActor actor)
    {
        DriveNode node = null;
        try
        {
            node = nodeMapper.selectActiveById(nodeId);
            if (node == null)
            {
                throw nodeNotFound();
            }
            DriveSpace space = spaceService.requireReadableSpace(node.getSpaceId(), actor);
            DriveNodeVo result = mapNodes(List.of(node), space, actor).get(0);
            logService.success(DriveConstants.ACTION_OPEN, actor, node,
                    null, node.getNodeName());
            return result;
        }
        catch (DriveException ex)
        {
            recordFailure(DriveConstants.ACTION_OPEN, actor, node, ex.getBusinessCode());
            throw ex;
        }
        catch (RuntimeException ex)
        {
            DriveException translated = storageUnavailable();
            recordFailure(DriveConstants.ACTION_OPEN, actor, node,
                    translated.getBusinessCode());
            throw translated;
        }
    }

    @Transactional
    public DriveNodeVo createFolder(DriveFolderCreateRequest request, DriveActor actor)
    {
        try
        {
            DriveSpace space = spaceService.requireWritableSpace(request.getSpaceId(), actor);
            Long parentId = request.getParentId() == null
                    ? DriveConstants.ROOT_PARENT_ID : request.getParentId();
            String ancestors;
            if (parentId == DriveConstants.ROOT_PARENT_ID)
            {
                DriveSpace lockedSpace = spaceMapper.selectByIdForUpdate(space.getSpaceId());
                requireSameActiveSpace(space, lockedSpace);
                ancestors = "0";
            }
            else
            {
                DriveNode parent = nodeMapper.selectByIdForUpdate(parentId);
                requireActiveFolderInSpace(parent, space.getSpaceId());
                ancestors = childAncestors(parent);
            }

            String displayName = namePolicy.normalize(request.getName());
            String normalizedName = namePolicy.normalizedKey(displayName);
            if (nodeMapper.existsActiveName(space.getSpaceId(), parentId, normalizedName, null) > 0)
            {
                throw nameConflict();
            }

            DriveNode folder = new DriveNode();
            folder.setSpaceId(space.getSpaceId());
            folder.setParentId(parentId);
            folder.setAncestors(ancestors);
            folder.setNodeType(DriveConstants.NODE_FOLDER);
            folder.setNodeName(displayName);
            folder.setNormalizedName(normalizedName);
            folder.setSizeBytes(0L);
            folder.setStatus(DriveConstants.STATUS_ACTIVE);
            folder.setActiveFlag(1);
            folder.setVersion(0);
            folder.setCreateBy(safeUsername(actor));
            folder.setCreateTime(new Date());
            folder.setUpdateBy(safeUsername(actor));
            folder.setUpdateTime(new Date());
            try
            {
                if (nodeMapper.insertNode(folder) != 1)
                {
                    throw concurrentModification();
                }
            }
            catch (DuplicateKeyException ex)
            {
                throw nameConflict();
            }
            logService.success(DriveConstants.ACTION_CREATE_FOLDER, actor,
                    folder, null, displayName);
            return mapNodes(List.of(folder), space, actor).get(0);
        }
        catch (DriveException ex)
        {
            logService.failure(DriveConstants.ACTION_CREATE_FOLDER, actor, ex.getBusinessCode());
            throw ex;
        }
        catch (RuntimeException ex)
        {
            logService.failure(DriveConstants.ACTION_CREATE_FOLDER, actor,
                    DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
            throw new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                    "文件夹创建失败，请稍后重试");
        }
    }

    public DriveNode requireParent(Long spaceId, Long parentId)
    {
        Long effectiveParent = parentId == null ? DriveConstants.ROOT_PARENT_ID : parentId;
        if (effectiveParent == DriveConstants.ROOT_PARENT_ID)
        {
            return null;
        }
        DriveNode parent = nodeMapper.selectActiveById(effectiveParent);
        requireActiveFolderInSpace(parent, spaceId);
        return parent;
    }

    public String availableFileName(Long spaceId, Long parentId, String originalName)
    {
        String normalized = namePolicy.normalize(originalName);
        if (nodeMapper.existsActiveName(spaceId, parentId,
                namePolicy.normalizedKey(normalized), null) == 0)
        {
            return normalized;
        }

        int dot = normalized.lastIndexOf('.');
        String base = dot > 0 ? normalized.substring(0, dot) : normalized;
        String suffix = dot > 0 ? normalized.substring(dot) : "";
        for (int index = 1; index <= 9999; index++)
        {
            String candidate = namePolicy.normalize(base + " (" + index + ")" + suffix);
            if (nodeMapper.existsActiveName(spaceId, parentId,
                    namePolicy.normalizedKey(candidate), null) == 0)
            {
                return candidate;
            }
        }
        throw nameConflict();
    }

    public DriveNodeVo toVo(DriveNode node, DriveActor actor)
    {
        if (node == null)
        {
            throw nodeNotFound();
        }
        DriveSpace space = spaceService.requireSpace(node.getSpaceId());
        return mapNodes(List.of(node), space, actor).get(0);
    }

    @Transactional
    public DriveNodeVo rename(Long nodeId, DriveRenameRequest request, DriveActor actor)
    {
        DriveNode node = null;
        try
        {
            node = nodeId == null ? null : nodeMapper.selectByIdForUpdate(nodeId);
            requireActiveNode(node);
            DriveSpace space = spaceService.requireWritableSpace(node.getSpaceId(), actor);
            requireVersion(node, request == null ? null : request.getVersion());

            String displayName = namePolicy.normalize(request.getName());
            String extension = renameExtension(node, displayName);
            String normalizedName = namePolicy.normalizedKey(displayName);
            if (nodeMapper.existsActiveName(node.getSpaceId(), node.getParentId(),
                    normalizedName, node.getNodeId()) > 0)
            {
                throw nameConflict();
            }

            String oldName = node.getNodeName();
            try
            {
                int updated = nodeMapper.updateName(node.getNodeId(), displayName,
                        normalizedName, extension, request.getVersion(), safeUsername(actor));
                if (updated != 1)
                {
                    throw concurrentModification();
                }
            }
            catch (DuplicateKeyException ex)
            {
                throw nameConflict();
            }

            node.setNodeName(displayName);
            node.setNormalizedName(normalizedName);
            node.setExtension(extension);
            markUpdated(node, actor);
            logService.success(DriveConstants.ACTION_RENAME, actor, node,
                    oldName, displayName);
            return mapNodes(List.of(node), space, actor).get(0);
        }
        catch (DriveException ex)
        {
            recordFailure(DriveConstants.ACTION_RENAME, actor, node, ex.getBusinessCode());
            throw ex;
        }
        catch (RuntimeException ex)
        {
            DriveException translated = storageUnavailable();
            recordFailure(DriveConstants.ACTION_RENAME, actor, node,
                    translated.getBusinessCode());
            throw translated;
        }
    }

    @Transactional
    public DriveNodeVo move(Long nodeId, DriveMoveRequest request, DriveActor actor)
    {
        DriveNode source = null;
        try
        {
            if (nodeId == null || request == null || request.getTargetParentId() == null
                    || request.getTargetParentId() < DriveConstants.ROOT_PARENT_ID)
            {
                throw invalidMove();
            }
            Long targetParentId = request.getTargetParentId();
            DriveNode target = null;
            if (targetParentId == DriveConstants.ROOT_PARENT_ID)
            {
                source = nodeMapper.selectByIdForUpdate(nodeId);
            }
            else
            {
                long firstId = Math.min(nodeId, targetParentId);
                long secondId = Math.max(nodeId, targetParentId);
                DriveNode first = nodeMapper.selectByIdForUpdate(firstId);
                DriveNode second = firstId == secondId
                        ? first : nodeMapper.selectByIdForUpdate(secondId);
                source = Objects.equals(nodeId, firstId) ? first : second;
                target = Objects.equals(targetParentId, firstId) ? first : second;
            }

            requireActiveNode(source);
            DriveSpace space = spaceService.requireWritableSpace(source.getSpaceId(), actor);
            requireVersion(source, request.getVersion());
            String targetAncestors;
            if (targetParentId == DriveConstants.ROOT_PARENT_ID)
            {
                DriveSpace lockedSpace = spaceMapper.selectByIdForUpdate(source.getSpaceId());
                requireSameActiveSpace(space, lockedSpace);
                targetAncestors = "0";
            }
            else
            {
                requireMoveTarget(source, target);
                targetAncestors = childAncestors(target);
            }

            if (nodeMapper.existsActiveName(source.getSpaceId(), targetParentId,
                    source.getNormalizedName(), source.getNodeId()) > 0)
            {
                throw nameConflict();
            }

            Long oldParentId = source.getParentId();
            if (DriveConstants.NODE_FOLDER.equals(source.getNodeType()))
            {
                String oldPrefix = childAncestors(source);
                String newPrefix = targetAncestors + "," + source.getNodeId();
                if (!oldPrefix.equals(newPrefix))
                {
                    nodeMapper.updateDescendantAncestors(source.getSpaceId(),
                            oldPrefix, newPrefix, safeUsername(actor));
                }
            }
            try
            {
                int updated = nodeMapper.updateParent(source.getNodeId(), targetParentId,
                        targetAncestors, request.getVersion(), safeUsername(actor));
                if (updated != 1)
                {
                    throw concurrentModification();
                }
            }
            catch (DuplicateKeyException ex)
            {
                throw nameConflict();
            }

            source.setParentId(targetParentId);
            source.setAncestors(targetAncestors);
            markUpdated(source, actor);
            logService.success(DriveConstants.ACTION_MOVE, actor, source,
                    "parentId=" + oldParentId, "parentId=" + targetParentId);
            return mapNodes(List.of(source), space, actor).get(0);
        }
        catch (DriveException ex)
        {
            recordFailure(DriveConstants.ACTION_MOVE, actor, source, ex.getBusinessCode());
            throw ex;
        }
        catch (RuntimeException ex)
        {
            DriveException translated = storageUnavailable();
            recordFailure(DriveConstants.ACTION_MOVE, actor, source,
                    translated.getBusinessCode());
            throw translated;
        }
    }

    public List<DriveNodeVo> recent(DriveActor actor, int limit)
    {
        try
        {
            return resolveRecent(actor, limit);
        }
        catch (DriveException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw storageUnavailable();
        }
    }

    private List<DriveNodeVo> resolveRecent(DriveActor actor, int limit)
    {
        if (actor == null || actor.userId() == null)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED,
                    "无权访问最近使用记录");
        }
        int boundedLimit = Math.max(1, Math.min(50, limit));
        List<DriveNode> rows = operationLogMapper.selectRecentNodes(actor.userId(), boundedLimit);
        if (rows == null || rows.isEmpty())
        {
            return List.of();
        }

        Map<Long, DriveSpace> spaces = new LinkedHashMap<>();
        Set<Long> deniedSpaces = new LinkedHashSet<>();
        List<DriveNode> accepted = new ArrayList<>();
        for (DriveNode node : rows)
        {
            if (!isActiveNode(node) || node.getSpaceId() == null)
            {
                continue;
            }
            Long spaceId = node.getSpaceId();
            if (!spaces.containsKey(spaceId) && !deniedSpaces.contains(spaceId))
            {
                try
                {
                    DriveSpace readable = spaceService.requireReadableSpace(spaceId, actor);
                    if (readable == null)
                    {
                        deniedSpaces.add(spaceId);
                    }
                    else
                    {
                        spaces.put(spaceId, readable);
                    }
                }
                catch (DriveException ex)
                {
                    deniedSpaces.add(spaceId);
                }
            }
            if (spaces.containsKey(spaceId))
            {
                accepted.add(node);
            }
        }

        Map<Long, List<DriveNode>> nodesBySpace = accepted.stream()
                .collect(Collectors.groupingBy(DriveNode::getSpaceId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, DriveNodeVo> mappedById = new LinkedHashMap<>();
        nodesBySpace.forEach((spaceId, nodes) -> mapNodes(
                nodes, spaces.get(spaceId), actor).forEach(
                        node -> mappedById.put(node.nodeId(), node)));
        return accepted.stream().map(DriveNode::getNodeId)
                .map(mappedById::get).filter(Objects::nonNull).toList();
    }

    static void requireActiveFolderInSpace(DriveNode parent, Long spaceId)
    {
        if (parent == null || !Objects.equals(spaceId, parent.getSpaceId())
                || !DriveConstants.STATUS_ACTIVE.equals(parent.getStatus())
                || !DriveConstants.NODE_FOLDER.equals(parent.getNodeType()))
        {
            throw nodeNotFound();
        }
    }

    static String childAncestors(DriveNode parent)
    {
        String ancestors = parent.getAncestors();
        if (ancestors == null || ancestors.isBlank())
        {
            ancestors = "0";
        }
        return ancestors + "," + parent.getNodeId();
    }

    private static void requireActiveNode(DriveNode node)
    {
        if (!isActiveNode(node))
        {
            throw nodeNotFound();
        }
    }

    private static boolean isActiveNode(DriveNode node)
    {
        return node != null && DriveConstants.STATUS_ACTIVE.equals(node.getStatus())
                && Objects.equals(1, node.getActiveFlag())
                && (DriveConstants.NODE_FILE.equals(node.getNodeType())
                || DriveConstants.NODE_FOLDER.equals(node.getNodeType()));
    }

    private static void requireVersion(DriveNode node, Integer requestedVersion)
    {
        if (requestedVersion == null || node.getVersion() == null
                || !Objects.equals(node.getVersion(), requestedVersion))
        {
            throw concurrentModification();
        }
    }

    private void requireMoveTarget(DriveNode source, DriveNode target)
    {
        if (target == null || !DriveConstants.STATUS_ACTIVE.equals(target.getStatus())
                || !Objects.equals(1, target.getActiveFlag())
                || !DriveConstants.NODE_FOLDER.equals(target.getNodeType()))
        {
            throw nodeNotFound();
        }
        if (!Objects.equals(source.getSpaceId(), target.getSpaceId()))
        {
            throw invalidMove();
        }
        if (DriveConstants.NODE_FOLDER.equals(source.getNodeType())
                && (Objects.equals(source.getNodeId(), target.getNodeId())
                || containsAncestorToken(target.getAncestors(), source.getNodeId())))
        {
            throw invalidMove();
        }
    }

    private String renameExtension(DriveNode node, String displayName)
    {
        if (DriveConstants.NODE_FOLDER.equals(node.getNodeType()))
        {
            return null;
        }
        String requested = filePolicy.extension(displayName);
        String stored = normalizedExtension(node.getExtension());
        if (stored.isBlank() || !stored.equals(requested))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED,
                    "文件扩展名不能修改");
        }
        return stored;
    }

    private static String normalizedExtension(String extension)
    {
        if (extension == null)
        {
            return "";
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    private static boolean containsAncestorToken(String ancestors, Long nodeId)
    {
        if (ancestors == null || nodeId == null)
        {
            return false;
        }
        String expected = nodeId.toString();
        for (String token : ancestors.split(","))
        {
            if (expected.equals(token.trim()))
            {
                return true;
            }
        }
        return false;
    }

    private static void markUpdated(DriveNode node, DriveActor actor)
    {
        node.setVersion(node.getVersion() == null ? 1 : node.getVersion() + 1);
        node.setUpdateBy(safeUsername(actor));
        node.setUpdateTime(new Date());
    }

    private void recordFailure(String action, DriveActor actor, DriveNode node, String code)
    {
        if (node == null)
        {
            logService.failure(action, actor, code);
        }
        else
        {
            logService.failure(action, actor, node, code, null, null);
        }
    }

    private List<DriveNodeVo> mapNodes(List<DriveNode> nodes, DriveSpace space, DriveActor actor)
    {
        if (nodes == null || nodes.isEmpty())
        {
            return nodes instanceof Page<?> page ? emptyMappedPage(page) : List.of();
        }

        LinkedHashSet<Long> pathIds = new LinkedHashSet<>();
        for (DriveNode node : nodes)
        {
            pathIds.addAll(ancestorIds(node.getAncestors()));
        }
        Map<Long, DriveNode> pathNodes = pathIds.isEmpty() ? Map.of()
                : nodeMapper.selectPathNodes(space.getSpaceId(), List.copyOf(pathIds)).stream()
                .collect(Collectors.toMap(DriveNode::getNodeId, Function.identity(), (left, right) -> left));

        List<DriveNodeVo> mapped = nodes instanceof Page<?> source
                ? mappedPage(source) : new ArrayList<>(nodes.size());
        boolean canWriteSpace = spaceService.canWriteSpace(space, actor);
        for (DriveNode node : nodes)
        {
            mapped.add(toVo(node, space, actor, pathNodes, canWriteSpace));
        }
        return mapped;
    }

    private List<DriveNodeVo> queryNodes(DriveSpace space, Long parentId,
            String keywordPattern, String sortField, String sortDirection, DriveActor actor)
    {
        List<DriveNode> nodes = keywordPattern.isBlank()
                ? nodeMapper.selectActiveChildren(
                        space.getSpaceId(), parentId, sortField, sortDirection)
                : nodeMapper.selectActiveSearch(
                        space.getSpaceId(), keywordPattern, sortField, sortDirection);
        return mapNodes(nodes, space, actor);
    }

    private DriveNodeVo toVo(DriveNode node, DriveSpace space,
            DriveActor actor, Map<Long, DriveNode> pathNodes, boolean canWriteSpace)
    {
        List<Long> ancestorIds = ancestorIds(node.getAncestors());
        List<DriveBreadcrumbVo> breadcrumbs = new ArrayList<>();
        List<String> logicalNames = new ArrayList<>();
        for (Long ancestorId : ancestorIds)
        {
            DriveNode ancestor = pathNodes.get(ancestorId);
            if (ancestor != null)
            {
                breadcrumbs.add(new DriveBreadcrumbVo(ancestor.getNodeId(), ancestor.getNodeName()));
                logicalNames.add(ancestor.getNodeName());
            }
        }
        logicalNames.add(node.getNodeName());
        if (DriveConstants.NODE_FOLDER.equals(node.getNodeType()))
        {
            breadcrumbs.add(new DriveBreadcrumbVo(node.getNodeId(), node.getNodeName()));
        }
        String logicalPath = "/" + String.join("/", logicalNames);
        boolean active = DriveConstants.STATUS_ACTIVE.equals(node.getStatus())
                && Objects.equals(1, node.getActiveFlag());
        boolean canWrite = active && canWriteSpace;
        boolean canDelete = active && authorization.canCleanup(actor, space);
        boolean canPreview = active && DriveConstants.NODE_FILE.equals(node.getNodeType())
                && filePolicy.isPreviewable(node.getExtension(), node.getContentType());
        String spaceName = node.getSpaceName() == null || node.getSpaceName().isBlank()
                ? space.getSpaceName() : node.getSpaceName();
        return new DriveNodeVo(node.getNodeId(), node.getSpaceId(), spaceName,
                node.getParentId(), node.getNodeType(), node.getNodeName(), node.getExtension(),
                node.getContentType(), node.getSizeBytes(), node.getStatus(), node.getVersion(),
                node.getCreateBy(), node.getCreateTime(), node.getUpdateBy(), node.getUpdateTime(),
                logicalPath, ancestorIds, breadcrumbs, canWrite, canDelete, canPreview);
    }

    private static List<Long> ancestorIds(String ancestors)
    {
        if (ancestors == null || ancestors.isBlank() || "0".equals(ancestors))
        {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String token : ancestors.split(","))
        {
            if (!token.isBlank() && !"0".equals(token))
            {
                try
                {
                    result.add(Long.valueOf(token));
                }
                catch (NumberFormatException ex)
                {
                    throw new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                            "云盘目录数据异常");
                }
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeSortField(String value)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Set.of("name", "size", "updated").contains(normalized) ? normalized : "updated";
    }

    private static String normalizeSortDirection(String value)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Set.of("asc", "desc").contains(normalized) ? normalized : "desc";
    }

    private static void requireSameActiveSpace(DriveSpace expected, DriveSpace locked)
    {
        if (locked == null || !Objects.equals(expected.getSpaceId(), locked.getSpaceId())
                || !DriveConstants.STATUS_ACTIVE.equals(locked.getStatus()))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_SPACE_NOT_FOUND, "云盘空间不存在");
        }
    }

    private static String safeUsername(DriveActor actor)
    {
        return actor == null || actor.username() == null ? "" : actor.username();
    }

    @SuppressWarnings("unchecked")
    private static List<DriveNodeVo> mappedPage(Page<?> source)
    {
        Page<DriveNodeVo> target = new Page<>(source.getPageNum(), source.getPageSize());
        target.setTotal(source.getTotal());
        target.setPages(source.getPages());
        return target;
    }

    private static List<DriveNodeVo> emptyMappedPage(Page<?> source)
    {
        return mappedPage(source);
    }

    private static DriveException nodeNotFound()
    {
        return new DriveException(DriveErrorCodes.DRIVE_NODE_NOT_FOUND, "云盘节点不存在");
    }

    private static DriveException nameConflict()
    {
        return new DriveException(DriveErrorCodes.DRIVE_NAME_CONFLICT, "同名文件或文件夹已存在");
    }

    private static DriveException concurrentModification()
    {
        return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "节点已被其他操作更新，请刷新后重试");
    }

    private static DriveException invalidMove()
    {
        return new DriveException(DriveErrorCodes.DRIVE_INVALID_MOVE, "不能移动到该目标目录");
    }

    private static DriveException storageUnavailable()
    {
        return new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                "云盘节点操作失败，请稍后重试");
    }
}
