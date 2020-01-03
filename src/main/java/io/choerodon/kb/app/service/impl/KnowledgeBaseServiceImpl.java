package io.choerodon.kb.app.service.impl;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import io.choerodon.core.exception.CommonException;
import io.choerodon.kb.api.vo.KnowledgeBaseInfoVO;
import io.choerodon.kb.api.vo.KnowledgeBaseListVO;
import io.choerodon.kb.app.service.KnowledgeBaseService;
import io.choerodon.kb.app.service.PageService;
import io.choerodon.kb.app.service.WorkSpaceService;
import io.choerodon.kb.app.service.assembler.KnowledgeBaseAssembler;
import io.choerodon.kb.infra.dto.KnowledgeBaseDTO;
import io.choerodon.kb.infra.dto.WorkSpaceDTO;
import io.choerodon.kb.infra.feign.BaseFeignClient;
import io.choerodon.kb.infra.mapper.KnowledgeBaseMapper;

/**
 * @author zhaotianxin
 * @since 2019/12/30
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private static final String RANGE_PRIVATE = "range_private";
    private static final String RANGE_PUBLIC = "range_public";
    private static final String RANGE_PROJECT= "range_project";
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private WorkSpaceService workSpaceService;

    @Autowired
    private PageService pageService;

    @Autowired
    private KnowledgeBaseAssembler knowledgeBaseAssembler;

    @Override
    public KnowledgeBaseDTO baseInsert(KnowledgeBaseDTO knowledgeBaseDTO) {
        if(ObjectUtils.isEmpty(knowledgeBaseDTO)){
           throw new CommonException("error.insert.knowledge.base.is.null");
        }
        if( knowledgeBaseMapper.insertSelective(knowledgeBaseDTO) != 1){
            throw new CommonException("error.insert.knowledge.base");
        };
        return knowledgeBaseMapper.selectByPrimaryKey(knowledgeBaseDTO.getId());
    }

    @Override
    public KnowledgeBaseDTO baseUpdate(KnowledgeBaseDTO knowledgeBaseDTO) {
        if(ObjectUtils.isEmpty(knowledgeBaseDTO)){
            throw new CommonException("error.update.knowledge.base.is.null");
        }
        if(knowledgeBaseMapper.updateByPrimaryKeySelective(knowledgeBaseDTO) != 1){
            throw new CommonException("error.update.knowledge.base");
        };
        return knowledgeBaseMapper.selectByPrimaryKey(knowledgeBaseDTO.getId());
    }

    @Override
    public KnowledgeBaseInfoVO create(Long organizationId,Long projectId,KnowledgeBaseInfoVO knowledgeBaseInfoVO) {
        KnowledgeBaseDTO knowledgeBaseDTO = modelMapper.map(knowledgeBaseInfoVO, KnowledgeBaseDTO.class);
        knowledgeBaseDTO.setProjectId(projectId);
        knowledgeBaseDTO.setOrganizationId(organizationId);
        // 公开范围
        if(RANGE_PROJECT.equals(knowledgeBaseInfoVO.getOpenRange())){
            List<Long> rangeProjectIds = knowledgeBaseInfoVO.getRangeProjectIds();
            if(CollectionUtils.isEmpty(rangeProjectIds)){
               throw new CommonException("error.range.project.of.at.least.one.project");
            }
            knowledgeBaseDTO.setRangeProject(StringUtils.join(rangeProjectIds,","));
        }
        // 插入数据库
        KnowledgeBaseDTO knowledgeBaseDTO1 = baseInsert(knowledgeBaseDTO);
        // 是否按模板创建知识库
        if(knowledgeBaseInfoVO.getTemplateBaseId() != null){
            pageService.createByTemplate(organizationId,projectId,knowledgeBaseDTO1.getId(),knowledgeBaseInfoVO.getTemplateBaseId());
         }
        //返回给前端
        return knowledgeBaseAssembler.dtoToInfoVO(knowledgeBaseDTO1);
    }

    @Override
    public KnowledgeBaseInfoVO update(Long organizationId, Long projectId, KnowledgeBaseInfoVO knowledgeBaseInfoVO) {
        knowledgeBaseInfoVO.setProjectId(projectId);
        knowledgeBaseInfoVO.setOrganizationId(organizationId);
        KnowledgeBaseDTO knowledgeBaseDTO = modelMapper.map(knowledgeBaseInfoVO, KnowledgeBaseDTO.class);
        if(RANGE_PROJECT.equals(knowledgeBaseInfoVO.getOpenRange())){
            List<Long> rangeProjectIds = knowledgeBaseInfoVO.getRangeProjectIds();
            if(CollectionUtils.isEmpty(rangeProjectIds)){
                throw new CommonException("error.range.project.of.at.least.one.project");
            }
            knowledgeBaseDTO.setRangeProject(StringUtils.join(rangeProjectIds,","));
        }
        return knowledgeBaseAssembler.dtoToInfoVO(baseUpdate(knowledgeBaseDTO));
    }

    @Override
    public void removeKnowledgeBase(Long organizationId, Long projectId, Long baseId) {
        KnowledgeBaseDTO knowledgeBaseDTO = knowledgeBaseMapper.selectByPrimaryKey(baseId);
        knowledgeBaseDTO.setDelete(true);
        baseUpdate(knowledgeBaseDTO);
        //将知识库下面所有的文件 设置为is_delete:true
        workSpaceService.removeWorkSpaceByBaseId(organizationId,projectId,baseId);
    }

    @Override
    public void deleteKnowledgeBase(Long organizationId, Long projectId, Long baseId) {
        // 彻底删除知识库下面所有的文件
        workSpaceService.deleteWorkSpaceByBaseId(organizationId,projectId,baseId);
        // 删除知识库
        knowledgeBaseMapper.deleteByPrimaryKey(baseId);

    }

    @Override
    public List<KnowledgeBaseListVO> queryKnowledgeBaseWithRecent(Long organizationId, Long projectId) {
        WorkSpaceDTO workSpaceDTO = new WorkSpaceDTO();
        workSpaceDTO.setProjectId(projectId);
        List<KnowledgeBaseListVO> knowledgeBaseListVOS = knowledgeBaseMapper.queryKnowledgeBaseWithRecentUpate(projectId,null,"range_private");
//        knowledgeBaseListVOS.stream().forEach(e -> knowledgeBaseAssembler.docheage(e.getWorkSpaceRecents(), projectId));
        return knowledgeBaseListVOS;
    }

    @Override
    public void restoreKnowledgeBase(Long organizationId, Long projectId, Long baseId) {
        KnowledgeBaseDTO knowledgeBaseDTO = knowledgeBaseMapper.selectByPrimaryKey(baseId);
        knowledgeBaseDTO.setDelete(false);
        baseUpdate(knowledgeBaseDTO);
        // 恢复目标知识库下面的所有文档
        workSpaceService.restoreWorkSpaceByBaseId(organizationId,projectId,baseId);
    }

}
