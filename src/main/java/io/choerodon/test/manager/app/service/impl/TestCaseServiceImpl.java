package io.choerodon.test.manager.app.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageInfo;
import io.choerodon.agile.api.vo.*;
import io.choerodon.agile.infra.common.enums.IssueTypeCode;
import io.choerodon.base.domain.PageRequest;
import io.choerodon.base.domain.Sort;
import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.vo.*;
import io.choerodon.test.manager.api.vo.*;
import io.choerodon.test.manager.app.service.TestCaseService;
import io.choerodon.test.manager.app.service.TestCaseStepService;
import io.choerodon.test.manager.app.service.UserService;
import io.choerodon.test.manager.infra.dto.*;
import io.choerodon.test.manager.infra.feign.ApplicationFeignClient;
import io.choerodon.test.manager.infra.feign.BaseFeignClient;
import io.choerodon.test.manager.infra.feign.ProductionVersionClient;
import io.choerodon.test.manager.infra.feign.TestCaseFeignClient;
import io.choerodon.test.manager.infra.mapper.TestCaseMapper;
import io.choerodon.test.manager.infra.mapper.TestIssueFolderMapper;
import io.choerodon.test.manager.infra.mapper.TestProjectInfoMapper;
import io.choerodon.test.manager.infra.util.DBValidateUtil;
import io.choerodon.test.manager.infra.util.PageUtil;
import io.choerodon.test.manager.infra.util.TypeUtil;
import org.checkerframework.checker.units.qual.A;
import org.modelmapper.ModelMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.GitInfoContributor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by 842767365@qq.com on 6/11/18.
 */

@Component
public class TestCaseServiceImpl implements TestCaseService {

    @Autowired
    private TestCaseFeignClient testCaseFeignClient;

    @Autowired
    private ProductionVersionClient productionVersionClient;

    @Autowired
    private BaseFeignClient baseFeignClient;

    @Autowired
    private ApplicationFeignClient applicationFeignClient;

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private TestCaseStepService testCaseStepService;

    @Autowired
    private TestProjectInfoMapper testProjectInfoMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private TestIssueFolderMapper testIssueFolderMapper;
    @Override
    public ResponseEntity<PageInfo<IssueListTestVO>> listIssueWithoutSub(Long projectId, SearchDTO searchDTO, PageRequest pageRequest, Long organizationId) {
        Assert.notNull(projectId, "error.TestCaseService.listIssueWithoutSub.param.projectId.not.null");
        Assert.notNull(pageRequest, "error.TestCaseService.listIssueWithoutSub.param.pageRequest.not.null");
        return testCaseFeignClient.listIssueWithoutSubToTestComponent(projectId, searchDTO, organizationId, pageRequest.getPage(), pageRequest.getSize(), PageUtil.sortToSql(pageRequest.getSort()));
    }

    @Override
    public ResponseEntity<PageInfo<IssueComponentDetailVO>> listIssueWithoutSubDetail(Long projectId, SearchDTO searchDTO, PageRequest pageRequest, Long organizationId) {
        Assert.notNull(projectId, "error.TestCaseService.listIssueWithoutSubDetail.param.projectId.not.null");
        Assert.notNull(pageRequest, "error.TestCaseService.listIssueWithoutSubDetail.param.pageRequest.not.null");
        return testCaseFeignClient.listIssueWithoutSubDetail(pageRequest.getPage(), pageRequest.getSize(), PageUtil.sortToSql(pageRequest.getSort()), projectId, searchDTO, organizationId);
    }

    @Override
    public ResponseEntity<IssueDTO> queryIssue(Long projectId, Long issueId, Long organizationId) {
        Assert.notNull(projectId, "error.TestCaseService.queryIssue.param.projectId.not.null");
        Assert.notNull(issueId, "error.TestCaseService.queryIssue.param.issueId.not.null");
        return testCaseFeignClient.queryIssue(projectId, issueId, organizationId);
    }

    @Override
    public Map<Long, IssueInfosVO> getIssueInfoMap(Long projectId, SearchDTO searchDTO, PageRequest pageRequest, Long organizationId) {
        return listIssueWithoutSub(projectId, searchDTO, pageRequest, organizationId).getBody().getList().stream().collect(Collectors.toMap(IssueListTestVO::getIssueId, IssueInfosVO::new));
    }

    /**
     * 获取issue信息并且更新分页信息
     *
     * @param projectId
     * @param searchDTO
     * @param pageRequest
     * @return
     */
    public <T> Map<Long, IssueInfosVO> getIssueInfoMapAndPopulatePageInfo(Long projectId, SearchDTO searchDTO, PageRequest pageRequest, Page page, Long organizationId) {
        PageInfo<IssueListTestWithSprintVersionDTO> returnDto = listIssueWithLinkedIssues(projectId, searchDTO, pageRequest, organizationId).getBody();
        Assert.notNull(returnDto, "error.TestCaseService.getIssueInfoMapAndPopulatePageInfo.param.page.not.be.null");
        page.setPageNum(returnDto.getPageNum());
        page.setPageSize(returnDto.getPageSize());
        page.setTotal(returnDto.getTotal());
        return returnDto.getList().stream().collect(Collectors.toMap(IssueListTestWithSprintVersionDTO::getIssueId, IssueInfosVO::new));

    }

    @Override
    public Map<Long, IssueInfosVO> getIssueInfoMap(Long projectId, SearchDTO searchDTO, boolean needDetail, Long organizationId) {
        PageRequest pageRequest = new PageRequest(1, 999999999, Sort.Direction.DESC, "issueId");
        if (needDetail) {
            return listIssueWithoutSubDetail(projectId, searchDTO, pageRequest, organizationId).getBody().getList().stream().collect(Collectors.toMap(IssueComponentDetailVO::getIssueId, IssueInfosVO::new));
        } else {
            return listIssueWithoutSub(projectId, searchDTO, pageRequest, organizationId).getBody().getList().stream().collect(Collectors.toMap(IssueListTestVO::getIssueId, IssueInfosVO::new));
        }
    }

    @Override
    public Map<Long, IssueInfosVO> getIssueInfoMap(Long projectId, Long[] issueIds, boolean needDetail, Long organizationId) {
        if (ObjectUtils.isEmpty(issueIds)) {
            return new HashMap<>();
        }
        return getIssueInfoMap(projectId, buildIdsSearchDTO(issueIds), needDetail, organizationId);
    }

    @Override
    public Map<Long, IssueInfosVO> getIssueInfoMap(Long projectId, Long[] issueIds, PageRequest pageRequest, Long organizationId) {
        if (ObjectUtils.isEmpty(issueIds)) {
            return new HashMap<>();
        }
        return getIssueInfoMap(projectId, buildIdsSearchDTO(issueIds), pageRequest, organizationId);
    }

    private SearchDTO buildIdsSearchDTO(Long[] issueIds) {
        SearchDTO searchDTO = new SearchDTO();
        Map map = new HashMap();
        map.put("issueIds", issueIds);
        searchDTO.setOtherArgs(map);
        return searchDTO;
    }


    @Override
    public List<IssueLinkDTO> listIssueLinkByIssueId(Long projectId, List<Long> issueId) {
        Assert.notNull(projectId, "error.TestCaseService.listIssueLinkByIssueId.param.projectId.not.null");
        if (ObjectUtils.isEmpty(issueId)) {
            return new ArrayList<>();
        }
        return testCaseFeignClient.listIssueLinkByBatch(projectId, issueId).getBody();
    }

    public List<IssueInfoDTO> listByIssueIds(Long projectId, List<Long> issueIds) {
        return testCaseFeignClient.listByIssueIds(projectId, issueIds).getBody();
    }

    @Override
    public PageInfo<ComponentForListDTO> listByProjectId(Long projectId) {
        return testCaseFeignClient.listByProjectId(projectId, new SearchDTO()).getBody();
    }

    @Override
    public List<IssueLabelDTO> listIssueLabel(Long projectId) {
        return testCaseFeignClient.listIssueLabel(projectId).getBody();
    }

    @Override
    public LookupTypeWithValuesDTO queryLookupValueByCode(String typeCode) {
        return testCaseFeignClient.queryLookupValueByCode(typeCode).getBody();
    }


    @Override
    public List<IssueStatusDTO> listStatusByProjectId(Long projectId) {
        return testCaseFeignClient.listStatusByProjectId(projectId).getBody();
    }

    @Override
    public String getVersionValue(Long projectId, Long appVersionId) {
        return applicationFeignClient.getVersionValue(projectId, appVersionId).getBody();
    }

    @Override
    public ApplicationRepDTO queryByAppId(Long projectId, Long applicationId) {
        return applicationFeignClient.queryByAppId(projectId, applicationId).getBody();
    }

    @Override
    public List<AppServiceVersionRespVO> getAppversion(Long projectId, List<Long> appVersionId) {
        return applicationFeignClient.getAppversion(projectId, TypeUtil.longsToArray(appVersionId)).getBody();
    }

    @Override
    public InstanceValueVO previewValues(Long projectId, InstanceValueVO replaceResult, Long appVersionId) {
        return applicationFeignClient.previewValues(projectId, replaceResult, appVersionId).getBody();
    }

    @Override
    public void deployTestApp(Long projectId, AppServiceDeployVO appServiceDeployVO) {
        applicationFeignClient.deployTestApp(projectId, appServiceDeployVO);
    }

    @Override
    @Transactional
    public TestCaseVO createTestCase(Long projectId, TestCaseVO testCaseVO) {
        TestProjectInfoDTO testProjectInfoDTO = new TestProjectInfoDTO();
        testProjectInfoDTO.setProjectId(projectId);
        TestProjectInfoDTO testProjectInfo = testProjectInfoMapper.selectOne(testProjectInfoDTO);
        if (ObjectUtils.isEmpty(testProjectInfo)) {
            throw new CommonException("error.query.project.info.null");
        }
        testCaseVO.setProjectId(projectId);
        testCaseVO.setCaseNum(testProjectInfo.getCaseMaxNum() + 1);
        TestCaseDTO testCaseDTO = baseInsert(testCaseVO);
        List<TestCaseStepVO> caseStepVOS = testCaseVO.getCaseStepVOS();
        if (!CollectionUtils.isEmpty(caseStepVOS)) {
            caseStepVOS.forEach(v -> {
                v.setIssueId(testCaseDTO.getCaseId());
                testCaseStepService.changeStep(v, projectId);
            });
        }
        testProjectInfo.setCaseMaxNum(testCaseVO.getCaseNum());
        testProjectInfoMapper.updateByPrimaryKeySelective(testProjectInfo);
        return testCaseVO;
    }

    @Override
    public TestCaseInfoVO queryCaseInfo(Long projectId, Long caseId) {
        TestCaseDTO testCaseDTO = testCaseMapper.selectByPrimaryKey(caseId);
        if (ObjectUtils.isEmpty(testCaseDTO)) {
           throw new CommonException("error.test.case.is.not.exist");
        }
        TestCaseInfoVO testCaseInfoVO = modelMapper.map(testCaseDTO,TestCaseInfoVO.class);
        // 获取用户信息
        Set<Long> ids = new HashSet<>();
        ids.add(testCaseDTO.getCreatedBy());
        ids.add(testCaseDTO.getLastUpdatedBy());
        List<Long> collect = ids.stream().collect(Collectors.toList());
        Map<Long, UserMessageDTO> UserMessageDTOMap = userService.queryUsersMap(collect, true);
        if(!ObjectUtils.isEmpty(UserMessageDTOMap.get(testCaseDTO.getCreatedBy()))) {
            testCaseInfoVO.setCreateUser(UserMessageDTOMap.get(testCaseDTO.getCreatedBy()));
        }
        if(!ObjectUtils.isEmpty(UserMessageDTOMap.get(testCaseDTO.getCreatedBy()))) {
            testCaseInfoVO.setLastUpdateUser(UserMessageDTOMap.get(testCaseDTO.getLastUpdatedBy()));
        }

        // 查询测试用例所属的文件夹
        TestIssueFolderDTO testIssueFolderDTO = testIssueFolderMapper.selectByPrimaryKey(testCaseDTO.getFolderId());
        if (!ObjectUtils.isEmpty(testIssueFolderDTO)) {
            testCaseInfoVO.setFolder(testIssueFolderDTO.getName());
        }
        return testCaseInfoVO;
    }

    @Override
    @Transactional
    public void deleteCase(Long projectId, Long caseId) {
        // 删除测试用例步骤
         testCaseStepService.removeStepByIssueId(caseId);
        // 删除问题链接

        // 删除测试循环

        // 删除测试用例相关的dataLog

        // 删除测试用例
    }

    @Override
    public List<TestCaseRepVO> listCaseByFolderId(Long projectId, Long folderId) {
        Set<Long> folderIds = new HashSet<>();
        queryAllFolderIds(folderId,folderIds);
        List<TestCaseDTO> testCaseDTOS = testCaseMapper.listCaseByFolderIds(projectId, folderIds);
        List<Long> userIds = new ArrayList<>();
        testCaseDTOS.forEach(v -> {
            userIds.add(v.getCreatedBy());
            userIds.add(v.getLastUpdatedBy());
        });
        Map<Long, UserMessageDTO> userMessageDTOMap = userService.queryUsersMap(userIds, false);
        if(!CollectionUtils.isEmpty(testCaseDTOS)){
            return  testCaseDTOS.stream().map(v -> dtoToRepVo(v, userMessageDTOMap)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }


    @Override
    public List<IssueLinkDTO> getLinkIssueFromIssueToTest(Long projectId, List<Long> issueId) {
        return listIssueLinkByIssueId(projectId, issueId).stream()
                .filter(u -> u.getTypeCode().matches(IssueTypeCode.ISSUE_TEST + "|" + IssueTypeCode.ISSUE_AUTO_TEST)).collect(Collectors.toList());
    }

    @Override
    public List<IssueLinkDTO> getLinkIssueFromTestToIssue(Long projectId, List<Long> issueId) {
        return listIssueLinkByIssueId(projectId, issueId).stream().collect(Collectors.toList());
    }

    @Override
    public Map<Long, ProductVersionDTO> getVersionInfo(Long projectId) {
        Assert.notNull(projectId, "error.TestCaseService.getVersionInfo.param.projectId.not.be.null");
        return productionVersionClient.listByProjectId(projectId).getBody().stream().collect(Collectors.toMap(ProductVersionDTO::getVersionId, Function.identity()));
    }

    @Override
    public ResponseEntity<PageInfo<ProductVersionPageDTO>> getTestCycleVersionInfo(Long projectId, Map<String, Object> searchParamMap) {
        return productionVersionClient.listByOptions(projectId, searchParamMap);
    }

    public Long[] getVersionIds(Long projectId) {
        Assert.notNull(projectId, "error.TestCaseService.getVersionIds.param.projectId.not.be.null");
        return productionVersionClient.listByProjectId(projectId).getBody().stream().map(ProductVersionDTO::getVersionId).distinct().toArray(Long[]::new);

    }

    @Override
    public ProjectDTO getProjectInfo(Long projectId) {
        Assert.notNull(projectId, "error.TestCaseService.getProjectInfo.param.projectId.not.be.null");
        return baseFeignClient.queryProject(projectId).getBody();
    }

    @Override
    public List<Long> queryIssueIdsByOptions(SearchDTO searchDTO, Long projectId) {
        Assert.notNull(projectId, "error.TestCaseService.queryIssueIdsByOptions.param.projectId.not.be.null");
        return testCaseFeignClient.queryIssueIdsByOptions(projectId, searchDTO).getBody();
    }

    @Override
    public IssueDTO createTest(IssueCreateDTO issueCreateDTO, Long projectId, String applyType) {
        Assert.notNull(projectId, "error.TestCaseService.createTest.param.projectId.not.be.null");
        return testCaseFeignClient.createIssue(projectId, applyType, issueCreateDTO).getBody();
    }

    @Override
    public List<IssueSearchDTO> batchIssueToVersion(Long projectId, Long versionId, List<Long> issueIds) {
        Assert.notNull(projectId, "error.TestCaseService.batchIssueToVersion.param.projectId.not.be.null");
        return testCaseFeignClient.batchIssueToVersion(projectId, versionId, issueIds).getBody();
    }


    @Override
    public List<Long> batchCloneIssue(Long projectId, Long versionId, Long[] issueIds) {
        Assert.notNull(projectId, "error.TestCaseService.batchCloneIssue.param.projectId.not.be.null");
        return testCaseFeignClient.batchCloneIssue(projectId, versionId, issueIds).getBody();
    }

    @Override
    public ResponseEntity batchIssueToVersionTest(Long projectId, Long versionId, List<Long> issueIds) {
        Assert.notNull(projectId, "error.TestCaseService.batchIssueToVersionTest.param.projectId.not.be.null");
        return testCaseFeignClient.batchIssueToVersionTest(projectId, versionId, issueIds);
    }

    @Override
    public ResponseEntity batchDeleteIssues(Long projectId, List<Long> issueIds) {
        Assert.notNull(projectId, "error.TestCaseService.batchDeleteIssues.param.projectId.not.be.null");
        return testCaseFeignClient.batchDeleteIssues(projectId, issueIds);
    }

    private ResponseEntity<PageInfo<IssueListTestWithSprintVersionDTO>> listIssueWithLinkedIssues(Long projectId, SearchDTO searchDTO, PageRequest pageRequest, Long organizationId) {
        Assert.notNull(projectId, "error.TestCaseService.listIssueWithLinkedIssues.param.projectId.not.null");
        Assert.notNull(pageRequest, "error.TestCaseService.listIssueWithLinkedIssues.param.pageRequest.not.null");
        return testCaseFeignClient.listIssueWithLinkedIssues(pageRequest.getPage(), pageRequest.getSize(), PageUtil.sortToSql(pageRequest.getSort()), projectId, searchDTO, organizationId);
    }

    private TestCaseDTO baseInsert(TestCaseVO testCaseVO) {
        if (testCaseVO == null || testCaseVO.getCaseId() != null) {
            throw new CommonException("error.test.case.insert.caseId.should.be.null");
        }
        TestCaseDTO testCaseDTO = modelMapper.map(testCaseVO, TestCaseDTO.class);
        DBValidateUtil.executeAndvalidateUpdateNum(testCaseMapper::insert, testCaseDTO, 1, "error.testcase.insert");
        return testCaseDTO;
    }

    private void queryAllFolderIds(Long folderId,Set<Long> folderIds){
        TestIssueFolderDTO testIssueFolderDTO = testIssueFolderMapper.selectByPrimaryKey(folderId);
        folderIds.add(folderId);
        TestIssueFolderDTO testIssueFolder = new TestIssueFolderDTO();
        testIssueFolder.setParentId(folderId);
        List<TestIssueFolderDTO> folderDTOS = testIssueFolderMapper.select(testIssueFolder);
        if(!CollectionUtils.isEmpty(folderDTOS)) {
          folderDTOS.forEach(v -> queryAllFolderIds(v.getFolderId(),folderIds));
        }
    }

    private TestCaseRepVO dtoToRepVo(TestCaseDTO testCaseDTO,Map<Long,UserMessageDTO> map){
        TestCaseRepVO testCaseRepVO = new TestCaseRepVO();
        modelMapper.map(testCaseDTO,testCaseRepVO);
        testCaseRepVO.setCreateUser(map.get(testCaseDTO.getCreatedBy()));
        testCaseRepVO.setLastUpdateUser(map.get(testCaseDTO.getLastUpdatedBy()));
        return testCaseRepVO;
    }
}
