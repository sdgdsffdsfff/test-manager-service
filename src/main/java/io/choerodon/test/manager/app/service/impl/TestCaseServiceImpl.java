package io.choerodon.test.manager.app.service.impl;

import static io.choerodon.test.manager.infra.constant.DataLogConstants.BATCH_UPDATE_CASE_PRIORITY;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.choerodon.core.utils.PageableHelper;
import io.choerodon.mybatis.domain.AuditDomain;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.core.domain.Page;
import io.choerodon.test.manager.api.vo.agile.*;
import io.choerodon.test.manager.infra.feign.IssueFeignClient;

import org.apache.commons.lang3.StringUtils;
import org.hzero.core.base.BaseConstants;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import io.choerodon.test.manager.infra.enums.IssueTypeCode;
import io.choerodon.core.exception.CommonException;
import io.choerodon.test.manager.api.vo.devops.AppServiceDeployVO;
import io.choerodon.test.manager.api.vo.devops.AppServiceVersionRespVO;
import io.choerodon.test.manager.api.vo.devops.ApplicationRepDTO;
import io.choerodon.test.manager.api.vo.devops.InstanceValueVO;
import io.choerodon.test.manager.api.vo.*;
import io.choerodon.test.manager.app.assembler.TestCaseAssembler;
import io.choerodon.test.manager.app.service.*;
import io.choerodon.test.manager.infra.annotation.DataLog;
import io.choerodon.test.manager.infra.constant.DataLogConstants;
import io.choerodon.test.manager.infra.dto.*;
import io.choerodon.test.manager.infra.feign.ApplicationFeignClient;
import io.choerodon.test.manager.infra.feign.BaseFeignClient;
import io.choerodon.test.manager.infra.feign.TestCaseFeignClient;
import io.choerodon.test.manager.infra.mapper.*;
import io.choerodon.test.manager.infra.util.ConvertUtils;
import io.choerodon.test.manager.infra.util.DBValidateUtil;
import io.choerodon.test.manager.infra.util.PageUtil;
import io.choerodon.test.manager.infra.util.TypeUtil;

/**
 * Created by 842767365@qq.com on 6/11/18.
 */

@Service
@Transactional(rollbackFor = Exception.class)
public class TestCaseServiceImpl implements TestCaseService {

    @Autowired
    private TestCaseFeignClient testCaseFeignClient;

    @Autowired
    private BaseFeignClient baseFeignClient;

    @Autowired
    private ApplicationFeignClient applicationFeignClient;

    @Autowired
    private IssueFeignClient issueFeignClient;

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Autowired
    private TestCaseStepMapper testCaseStepMapper;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private TestCaseStepService testCaseStepService;

    @Autowired
    private TestProjectInfoMapper testProjectInfoMapper;

    @Autowired
    private TestIssueFolderMapper testIssueFolderMapper;

    @Autowired
    private TestCaseLinkMapper testCaseLinkMapper;

    @Autowired
    private TestDataLogMapper testDataLogMapper;

    @Autowired
    private TestCaseLinkService testCaseLinkService;

    @Autowired
    private TestAttachmentMapper testAttachmentMapper;

    @Autowired
    private TestCaseAttachmentService testCaseAttachmentService;

    @Autowired
    private TestCaseAssembler testCaseAssembler;

    @Autowired
    private TestCycleCaseMapper testCycleCaseMapper;

    @Autowired
    private TestCycleCaseService testCycleCaseService;

    @Autowired
    private TestPriorityMapper testPriorityMapper;

    @Value("${services.attachment.url}")
    private String attachmentUrl;

    @Override
    public ResponseEntity<Page<IssueListTestVO>> listIssueWithoutSub(Long projectId, SearchDTO searchDTO, PageRequest pageRequest, Long organizationId) {
        Assert.notNull(projectId, "error.TestCaseService.listIssueWithoutSub.param.projectId.not.null");
        Assert.notNull(pageRequest, "error.TestCaseService.listIssueWithoutSub.param.pageRequest.not.null");
        Sort sort = pageRequest.getSort();
        String sortSql = null;
        if (!ObjectUtils.isEmpty(sort)) {
            sortSql = PageableHelper.getSortSql(sort);
            sortSql = sortSql.replace(" ", ",");
        }
        return testCaseFeignClient.listIssueWithoutSubToTestComponent(projectId, searchDTO, organizationId, pageRequest.getPage(), pageRequest.getSize(), sortSql);
    }

    @Override
    public ResponseEntity<Page<IssueComponentDetailVO>> listIssueWithoutSubDetail(Long projectId, SearchDTO searchDTO, PageRequest pageRequest, Long organizationId) {
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
        return listIssueWithoutSub(projectId, searchDTO, pageRequest, organizationId).getBody().getContent().stream().collect(Collectors.toMap(IssueListTestVO::getIssueId, IssueInfosVO::new));
    }

    /**
     * 获取issue信息并且更新分页信息
     *
     * @param projectId
     * @param searchDTO
     * @param pageRequest
     * @return
     */
    @Override
    public <T> Map<Long, IssueInfosVO> getIssueInfoMapAndPopulatePageInfo(Long projectId, SearchDTO searchDTO, PageRequest pageRequest, Page page, Long organizationId) {
        Page<IssueListTestWithSprintVersionDTO> returnDto = listIssueWithLinkedIssues(projectId, searchDTO, pageRequest, organizationId).getBody();
        Assert.notNull(returnDto, "error.TestCaseService.getIssueInfoMapAndPopulatePageInfo.param.page.not.be.null");
        page.setNumber(returnDto.getNumber());
        page.setSize(returnDto.getSize());
        page.setTotalElements(returnDto.getTotalElements());
        return returnDto.getContent().stream().collect(Collectors.toMap(IssueListTestWithSprintVersionDTO::getIssueId, IssueInfosVO::new));

    }

    @Override
    public Map<Long, IssueInfosVO> getIssueInfoMap(Long projectId, SearchDTO searchDTO, boolean needDetail, Long organizationId) {
        PageRequest pageRequest = new PageRequest(0, 999999999, Sort.Direction.DESC, "issueId");
        if (needDetail) {
            return listIssueWithoutSubDetail(projectId, searchDTO, pageRequest, organizationId).getBody().getContent().stream().collect(Collectors.toMap(IssueComponentDetailVO::getIssueId, IssueInfosVO::new));
        } else {
            return listIssueWithoutSub(projectId, searchDTO, pageRequest, organizationId).getBody().getContent().stream().collect(Collectors.toMap(IssueListTestVO::getIssueId, IssueInfosVO::new));
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
    public TestCaseRepVO createTestCase(Long projectId, TestCaseVO testCaseVO){
        return this.createTestCase(projectId, testCaseVO, null);
    }

    @Override
    public TestCaseRepVO createTestCase(Long projectId, TestCaseVO testCaseVO, AtomicLong outsideCount) {
        Long caseNum;
        if (Objects.isNull(outsideCount)){
            TestProjectInfoDTO testProjectInfoDTO = new TestProjectInfoDTO();
            testProjectInfoDTO.setProjectId(projectId);
            TestProjectInfoDTO testProjectInfo = testProjectInfoMapper.selectOne(testProjectInfoDTO);
            if (ObjectUtils.isEmpty(testProjectInfo)) {
                throw new CommonException("error.query.project.info.null");
            }
            testCaseVO.setProjectId(projectId);
            caseNum = testProjectInfo.getCaseMaxNum() + 1;
            testProjectInfo.setCaseMaxNum(caseNum);
            testProjectInfoMapper.updateByPrimaryKeySelective(testProjectInfo);
        }else {
            caseNum = outsideCount.incrementAndGet();
        }
        testCaseVO.setCaseNum(caseNum.toString());
        TestCaseDTO testCaseDTO = baseInsert(testCaseVO);
        // 创建测试步骤
        List<TestCaseStepVO> caseStepVOS = testCaseVO.getCaseStepVOS();
        if (!CollectionUtils.isEmpty(caseStepVOS)) {
            caseStepVOS.forEach(v -> {
                v.setIssueId(testCaseDTO.getCaseId());
                testCaseStepService.changeStep(v, projectId, false);
            });
        }
        // 返回数据
        List<TestIssueFolderDTO> testIssueFolderDTOS = testIssueFolderMapper.selectListByProjectId(projectId);
        Map<Long, TestIssueFolderDTO> folderMap = testIssueFolderDTOS.stream().collect(Collectors.toMap(TestIssueFolderDTO::getFolderId, Function.identity()));
        TestCaseRepVO testCaseRepVO = testCaseAssembler.dtoToRepVo(testCaseDTO, folderMap);
        return testCaseRepVO;
    }

    @Override
    public TestCaseInfoVO queryCaseInfo(Long projectId, Long caseId) {
        TestCaseDTO testCaseDTO = testCaseMapper.selectByPrimaryKey(caseId);
        if (ObjectUtils.isEmpty(testCaseDTO)) {
            throw new CommonException("error.test.case.is.not.exist");
        }
        TestCaseInfoVO testCaseInfoVO = testCaseAssembler.dtoToInfoVO(testCaseDTO);
        return testCaseInfoVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(Long projectId, Long caseId) {
        // 删除测试用例步骤
        testCaseStepService.removeStepByIssueId(projectId, caseId);
        // 删除问题链接
        TestCaseLinkDTO testCaseLinkDTO = new TestCaseLinkDTO();
        testCaseLinkDTO.setLinkCaseId(caseId);
        testCaseLinkDTO.setProjectId(projectId);
        testCaseLinkMapper.delete(testCaseLinkDTO);
        // 删除测试用例相关的dataLog
        TestDataLogDTO testDataLogDTO = new TestDataLogDTO();
        testDataLogDTO.setProjectId(projectId);
        testDataLogDTO.setCaseId(caseId);
        testDataLogMapper.delete(testDataLogDTO);
        // 删除附件信息
        TestCaseAttachmentDTO testCaseAttachmentDTO = new TestCaseAttachmentDTO();
        testCaseAttachmentDTO.setProjectId(projectId);
        testCaseAttachmentDTO.setCaseId(caseId);
        List<TestCaseAttachmentDTO> attachmentDTOS = testAttachmentMapper.select(testCaseAttachmentDTO);
        attachmentDTOS.forEach(v -> testCaseAttachmentService.delete(projectId, v.getAttachmentId()));
        // 删除测试用例
        testCaseMapper.deleteByPrimaryKey(caseId);

        // 删除测试计划中选择自动同步的计划中的有关联的执行
        List<TestCycleCaseDTO> testCycleCaseDTOS = testCycleCaseMapper.listAsyncCycleCase(projectId, caseId);
        if (CollectionUtils.isEmpty(testCycleCaseDTOS)) {
            List<Long> executeIds = testCycleCaseDTOS.stream().map(TestCycleCaseDTO::getExecuteId).collect(Collectors.toList());
            testCycleCaseService.batchDeleteByExecuteIds(executeIds);
        }
    }

    @Override
    public Page<TestCaseRepVO> listAllCaseByFolderId(Long projectId, Long folderId, PageRequest pageRequest, SearchDTO searchDTO, Long planId) {
        // 查询文件夹下所有的目录
        Set<Long> folderIds = new HashSet<>();
        TestIssueFolderDTO testIssueFolder = new TestIssueFolderDTO();
        testIssueFolder.setProjectId(projectId);
        Map<Long, List<TestIssueFolderDTO>> folderMap = testIssueFolderMapper.select(testIssueFolder).stream().filter(issueFolderDTO -> !"api".equals(issueFolderDTO.getType())).collect(Collectors.groupingBy(TestIssueFolderDTO::getParentId));
        queryAllFolderIds(folderId, folderIds, folderMap);
        // 处理排序
        checkPageRequest(pageRequest);
        // 查询文件夹下的的用例
        Page<TestCaseDTO> longPageInfo = PageHelper.doPageAndSort(pageRequest, () -> testCaseMapper.listCase(projectId, folderIds, searchDTO));
        Page<TestCaseRepVO> pageRepList;
        if (CollectionUtils.isEmpty(longPageInfo.getContent())) {
            pageRepList = PageUtil.buildPageInfoWithPageInfoList(longPageInfo, new ArrayList<>());
            return pageRepList;
        }
        List<TestCaseRepVO> repVOS = testCaseAssembler.listDtoToRepVo(projectId, longPageInfo.getContent(), planId);
        pageRepList = PageUtil.buildPageInfoWithPageInfoList(longPageInfo, repVOS);
        return pageRepList;
    }

    private void checkPageRequest(PageRequest pageRequest) {
        Sort sort = pageRequest.getSort();
        if (Objects.isNull(sort)){
            pageRequest.setSort(new Sort(new Sort.Order(Sort.Direction.DESC, TestCaseDTO.FIELD_CASE_ID)));
            return;
        }
        Iterator<Sort.Order> iterator = sort.iterator();
        Sort.Order t;
        while (iterator.hasNext()){
            t = iterator.next();
            if (!StringUtils.equalsAny(t.getProperty(),
                    TestCaseDTO.FIELD_CASE_ID, TestPriorityDTO.FIELD_SEQUENCE,
                    AuditDomain.FIELD_CREATION_DATE, AuditDomain.FIELD_LAST_UPDATE_DATE)){
                pageRequest.setSort(new Sort(new Sort.Order(t.getDirection(), TestCaseDTO.FIELD_CASE_ID)));
            }
        }
    }

    @Override
    public List<TestCaseDTO> listCaseByFolderId(Long folderId) {
        TestCaseDTO testCaseDTO = new TestCaseDTO();
        testCaseDTO.setFolderId(folderId);
        return testCaseMapper.select(testCaseDTO);
    }

    @Override
    @DataLog(type = DataLogConstants.CASE_UPDATE)
    public TestCaseRepVO updateCase(Long projectId, TestCaseRepVO testCaseRepVO, String[] fieldList) {
        if (ObjectUtils.isEmpty(testCaseRepVO) || ObjectUtils.isEmpty(testCaseRepVO.getCaseId())) {
            throw new CommonException("error.case.is.not.null");
        }
        TestCaseDTO testCaseDTO = baseQuery(testCaseRepVO.getCaseId());
        TestCaseDTO map = modelMapper.map(testCaseRepVO, TestCaseDTO.class);
        map.setVersionNum(testCaseDTO.getVersionNum() + 1);
        baseUpdate(map);

        List<TestCycleCaseDTO> testCycleCaseDTOS = testCycleCaseMapper.listAsyncCycleCase(testCaseDTO.getProjectId(), testCaseDTO.getCaseId());
        if (!CollectionUtils.isEmpty(testCycleCaseDTOS)) {
            List<TestCycleCaseDTO> collect = new ArrayList<>();
            if (!ObjectUtils.isEmpty(testCaseRepVO.getExecuteId())) {
                List<TestCycleCaseDTO> collect1 = testCycleCaseDTOS.stream().filter(v -> !testCaseRepVO.getExecuteId().equals(v.getExecuteId())).collect(Collectors.toList());
                collect.addAll(collect1);
            } else {
                collect.addAll(testCycleCaseDTOS);
            }
            testCaseAssembler.autoAsyncCase(collect, true, false, false);
        }
        TestCaseDTO testCaseDTO1 = testCaseMapper.selectByPrimaryKey(map.getCaseId());
        List<TestIssueFolderDTO> testIssueFolderDTOS = testIssueFolderMapper.selectListByProjectId(projectId);
        Map<Long, TestIssueFolderDTO> folderMap = testIssueFolderDTOS.stream().collect(Collectors.toMap(TestIssueFolderDTO::getFolderId, Function.identity()));
        TestCaseRepVO testCaseRepVO1 = testCaseAssembler.dtoToRepVo(testCaseDTO1, folderMap);
        return testCaseRepVO1;
    }


    @Override
    @DataLog(type = DataLogConstants.BATCH_MOVE, single = false)
    public void batchMove(Long projectId, Long folderId, List<TestCaseRepVO> testCaseRepVOS) {
        if (ObjectUtils.isEmpty(testCaseRepVOS)) {
            return;
        }
        if (ObjectUtils.isEmpty(testIssueFolderMapper.selectByPrimaryKey(folderId))) {
            throw new CommonException("error.query.folder.not.exist");
        }
        for (TestCaseRepVO testCaseRepVO : testCaseRepVOS) {
            // 待定 以后可能增加 rank 排序
//            if (!StringUtils.isEmpty(testCaseRepVO.getLastRank()) || !StringUtils.isEmpty(testCaseRepVO.getNextRank())) {
//                testCaseRepVO.setRank(RankUtil.Operation.UPDATE.getRank(testCaseRepVO.getLastRank(), testCaseRepVO.getNextRank()));
//            }
            TestCaseDTO testCaseDTO = baseQuery(testCaseRepVO.getCaseId());
            TestCaseDTO map = modelMapper.map(testCaseRepVO, TestCaseDTO.class);
            map.setObjectVersionNumber(testCaseDTO.getObjectVersionNumber());
            map.setFolderId(folderId);
            DBValidateUtil.executeAndvalidateUpdateNum(testCaseMapper::updateByPrimaryKeySelective, map, 1, "error.update.case");
        }

    }

    @Override
    public List<TestCaseDTO> batchCopy(Long projectId, Long folderId, List<TestCaseRepVO> testCaseRepVOS) {
        return this.batchCopy(projectId, folderId, testCaseRepVOS, null);
    }

    @Override
    public List<TestCaseDTO> batchCopy(Long projectId, Long folderId, List<TestCaseRepVO> testCaseRepVOS, AtomicLong outsideCount) {
        if (CollectionUtils.isEmpty(testCaseRepVOS)) {
            return Collections.emptyList();
        }
        if (ObjectUtils.isEmpty(testIssueFolderMapper.selectByPrimaryKey(folderId))) {
            throw new CommonException("error.query.folder.not.exist");
        }
        // 复制用例
        List<Long> collect = testCaseRepVOS.stream().map(TestCaseRepVO::getCaseId).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(collect)) {
            return Collections.emptyList();
        }
        List<TestCaseDTO> testCaseDTOS = testCaseMapper.listCopyCase(projectId, collect);
        for (TestCaseDTO testCaseDTO : testCaseDTOS) {
            Long oldCaseId = testCaseDTO.getCaseId();
            testCaseDTO.setCaseId(null);
            testCaseDTO.setVersionNum(1L);
            testCaseDTO.setFolderId(folderId);
            testCaseDTO.setObjectVersionNumber(null);
            TestCaseRepVO testCase = createTestCase(projectId, modelMapper.map(testCaseDTO, TestCaseVO.class), outsideCount);
            // 复制用例步骤
            TestCaseStepVO testCaseStepVO = new TestCaseStepVO();
            testCaseStepVO.setIssueId(oldCaseId);
            testCaseStepService.batchClone(testCaseStepVO, testCase.getCaseId(), projectId);
            // 复制用例链接
            testCaseLinkService.copyByCaseId(projectId, testCase.getCaseId(), oldCaseId);
            // 复制附件
            testCaseAttachmentService.cloneAttachmentByCaseId(projectId, testCase.getCaseId(), oldCaseId);
        }
        return testCaseDTOS;
    }

    @Override
    public void updateVersionNum(Long caseId) {
        TestCaseDTO testCaseDTO = testCaseMapper.selectByPrimaryKey(caseId);
        if (ObjectUtils.isEmpty(testCaseDTO)) {
            throw new CommonException("error.query.case.not.exist");
        }
        testCaseDTO.setVersionNum(testCaseDTO.getVersionNum() + 1);
        DBValidateUtil.executeAndvalidateUpdateNum(testCaseMapper::updateByPrimaryKeySelective, testCaseDTO, 1, "error.update.case");
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

//    @Override
//    public Map<Long, ProductVersionDTO> getVersionInfo(Long projectId) {
//        Assert.notNull(projectId, "error.TestCaseService.getVersionInfo.param.projectId.not.be.null");
//        return productionVersionClient.listByProjectId(projectId).getBody().stream().collect(Collectors.toMap(ProductVersionDTO::getVersionId, Function.identity()));
//    }

//    public Long[] getVersionIds(Long projectId) {
//        Assert.notNull(projectId, "error.TestCaseService.getVersionIds.param.projectId.not.be.null");
//        return productionVersionClient.listByProjectId(projectId).getBody().stream().map(ProductVersionDTO::getVersionId).distinct().toArray(Long[]::new);
//
//    }

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
    public void batchDeleteIssues(Long projectId, List<Long> issueIds) {
        Assert.notNull(projectId, "error.TestCaseService.batchDeleteIssues.param.projectId.not.be.null");
        if (!CollectionUtils.isEmpty(issueIds)) {
            testCaseStepMapper.deleteByCaseIds(projectId, issueIds);
            testCaseMapper.batchDeleteCases(projectId, issueIds);
        }
    }

    private ResponseEntity<Page<IssueListTestWithSprintVersionDTO>> listIssueWithLinkedIssues(Long projectId, SearchDTO searchDTO, PageRequest pageRequest, Long organizationId) {
        Assert.notNull(projectId, "error.TestCaseService.listIssueWithLinkedIssues.param.projectId.not.null");
        Assert.notNull(pageRequest, "error.TestCaseService.listIssueWithLinkedIssues.param.pageRequest.not.null");
        return testCaseFeignClient.listIssueWithLinkedIssues(pageRequest.getPage(), pageRequest.getSize(), PageUtil.sortToSql(pageRequest.getSort()), projectId, searchDTO, organizationId);
    }

    private TestCaseDTO baseInsert(TestCaseVO testCaseVO) {
        if (testCaseVO == null || testCaseVO.getCaseId() != null) {
            throw new CommonException("error.test.case.insert.caseId.should.be.null");
        }
        if (Objects.isNull(testCaseVO.getPriorityId())){
            TestPriorityDTO priorityDTO = new TestPriorityDTO();
            priorityDTO.setOrganizationId(ConvertUtils.getOrganizationId(testCaseVO.getProjectId()));
            priorityDTO.setDefaultFlag(true);
            testCaseVO.setPriorityId(testPriorityMapper.selectOne(priorityDTO).getId());
        }
        TestCaseDTO testCaseDTO = modelMapper.map(testCaseVO, TestCaseDTO.class);
        testCaseDTO.setVersionNum(1L);
        DBValidateUtil.executeAndvalidateUpdateNum(testCaseMapper::insert, testCaseDTO, 1, "error.testcase.insert");
        return testCaseDTO;
    }

    private void queryAllFolderIds(Long folderId, Set<Long> folderIds, Map<Long, List<TestIssueFolderDTO>> folderMap) {
        folderIds.add(folderId);
        List<TestIssueFolderDTO> testIssueFolderDTOS = folderMap.get(folderId);
        if (!CollectionUtils.isEmpty(testIssueFolderDTOS)) {
            testIssueFolderDTOS.forEach(v -> queryAllFolderIds(v.getFolderId(), folderIds, folderMap));
        }
    }

    @Override
    public TestCaseDTO baseUpdate(TestCaseDTO testCaseDTO) {
        if (ObjectUtils.isEmpty(testCaseDTO) || ObjectUtils.isEmpty(testCaseDTO.getCaseId())) {
            throw new CommonException("error.case.is.not.null");
        }
        DBValidateUtil.executeAndvalidateUpdateNum(testCaseMapper::updateByPrimaryKeySelective, testCaseDTO, 1, "error.testcase.update");
        return testCaseDTO;
    }

    @Override
    public Page<IssueNumDTO> queryIssueByOptionForAgile(Long projectId, Long issueId, String issueNum, Boolean self, String content, PageRequest pageRequest) {
        return issueFeignClient
                .queryIssueByOptionForAgile(
                        pageRequest.getPage(),
                        pageRequest.getSize(), projectId, issueId, issueNum, self, content)
                .getBody();
    }

    @Override
    public Set<Long> selectFolderIds(Long projectId, Long folderId) {
        // 查询文件夹下所有的目录
        Set<Long> folderIds = new HashSet<>();
        TestIssueFolderDTO testIssueFolder = new TestIssueFolderDTO();
        testIssueFolder.setProjectId(projectId);
        Map<Long, List<TestIssueFolderDTO>> folderMap = testIssueFolderMapper.select(testIssueFolder).stream().filter(issueFolderDTO -> !"api".equals(issueFolderDTO.getType())).collect(Collectors.groupingBy(TestIssueFolderDTO::getParentId));
        queryAllFolderIds(folderId, folderIds, folderMap);
        return folderIds;
    }

    @Override
    @DataLog(type = BATCH_UPDATE_CASE_PRIORITY, single = false)
    public void batchUpdateCasePriority(Long organizationId, Long priorityId, Long changePriorityId, Long userId,
                                         List<Long> projectIds) {
        testCaseMapper.batchUpdateCasePriority(priorityId, changePriorityId, userId, projectIds);
    }

    private TestCaseDTO baseQuery(Long caseId) {
        TestCaseDTO testCaseDTO = testCaseMapper.selectByPrimaryKey(caseId);
        if (ObjectUtils.isEmpty(testCaseDTO)) {
            throw new CommonException("error.case.is.not.exist");
        }
        return testCaseDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestCaseDTO importTestCase(IssueCreateDTO issueCreateDTO, Long projectId, String applyType) {
        TestProjectInfoDTO testProjectInfoDTO = new TestProjectInfoDTO();
        testProjectInfoDTO.setProjectId(projectId);
        TestProjectInfoDTO testProjectInfo = testProjectInfoMapper.selectOne(testProjectInfoDTO);
        if (ObjectUtils.isEmpty(testProjectInfo)) {
            throw new CommonException("error.query.project.info.null");
        }

        TestCaseDTO testCaseDTO = ConvertUtils.convertObject(issueCreateDTO, TestCaseDTO.class);
        Long caseNum = testProjectInfo.getCaseMaxNum() + 1;
        testCaseDTO.setCaseNum(caseNum.toString());
        testCaseDTO.setVersionNum(1L);
        // 插入测试用例
        testCaseMapper.insert(testCaseDTO);

        //修改projectInfo maxCaseNum
        testProjectInfo.setCaseMaxNum(caseNum);
        testProjectInfoMapper.updateByPrimaryKeySelective(testProjectInfo);
        // 更新记录关联表
        List<TestCaseLinkDTO> testCaseLinkDTOList = issueCreateDTO.getTestCaseLinkDTOList();
        if (testCaseLinkDTOList != null && !testCaseLinkDTOList.isEmpty()) {
            for (TestCaseLinkDTO testCaseLinkDTO : testCaseLinkDTOList) {
                testCaseLinkDTO.setProjectId(projectId);
                testCaseLinkDTO.setLinkCaseId(testCaseDTO.getCaseId());
                testCaseLinkMapper.insert(testCaseLinkDTO);
            }
        }
        return testCaseDTO;
    }

    @Override
    public List<Long> listAllCaseByFolderId(Long projectId, Long folderId) {
        // 查询文件夹下所有的目录
        Set<Long> folderIds = new HashSet<>();
        TestIssueFolderDTO testIssueFolder = new TestIssueFolderDTO();
        testIssueFolder.setProjectId(projectId);
        Map<Long, List<TestIssueFolderDTO>> folderMap = testIssueFolderMapper.select(testIssueFolder).stream().filter(issueFolderDTO -> !"api".equals(issueFolderDTO.getType())).collect(Collectors.groupingBy(TestIssueFolderDTO::getParentId));
        queryAllFolderIds(folderId, folderIds, folderMap);
        // 查询文件夹下的的用例
        List<Long> caseIds = testCaseMapper.listCaseIds(projectId, folderIds, null);
        return caseIds;
    }

    @Override
    public List<TestCaseDTO> listByCaseIds(Long projectId, List<Long> caseIds) {
        if (CollectionUtils.isEmpty(caseIds)) {
            return new ArrayList<>();
        }
        return testCaseMapper.listByCaseIds(projectId, caseIds, true);
    }

    @Override
    public TestCaseInfoVO queryCaseRep(Long caseId) {
        TestCaseDTO testCaseDTO = testCaseMapper.selectByPrimaryKey(caseId);
        if (ObjectUtils.isEmpty(testCaseDTO)) {
            return new TestCaseInfoVO();
        }
        TestCaseInfoVO testCaseInfoVO = testCaseAssembler.dtoToInfoVO(testCaseDTO);
        List<TestCaseStepDTO> testCaseStepDTOS = testCaseStepService.listByCaseIds(Arrays.asList(caseId));
        if (!CollectionUtils.isEmpty(testCaseStepDTOS)) {
            testCaseInfoVO.setTestCaseStepS(testCaseStepDTOS);
        }
        return testCaseInfoVO;
    }
}
