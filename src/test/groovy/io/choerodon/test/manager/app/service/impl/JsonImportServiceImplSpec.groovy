//package io.choerodon.test.manager.app.service.impl
//
//import io.choerodon.agile.api.vo.*
//import io.choerodon.core.exception.CommonException
//import io.choerodon.devops.api.vo.ApplicationRepDTO
//import io.choerodon.devops.api.vo.ApplicationVersionRepDTO
//import io.choerodon.test.manager.IntegrationTestConfiguration
//import io.choerodon.test.manager.app.service.TestCaseService
//import io.choerodon.test.manager.infra.dto.TestAppInstanceDTO
//import io.choerodon.test.manager.infra.dto.TestAutomationHistoryDTO
//import io.choerodon.test.manager.infra.dto.TestCycleCaseDTO
//import io.choerodon.test.manager.infra.dto.TestIssueFolderRelDTO
//import io.choerodon.test.manager.infra.util.FileUtil
//import io.choerodon.test.manager.infra.util.RedisTemplateUtil
//import io.choerodon.test.manager.infra.feign.ApplicationFeignClient
//import io.choerodon.test.manager.infra.feign.IssueFeignClient
//import io.choerodon.test.manager.infra.mapper.*
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.boot.test.context.SpringBootTest
//import org.springframework.context.annotation.Import
//import org.springframework.core.io.ClassPathResource
//import org.springframework.data.redis.support.atomic.RedisAtomicLong
//import org.springframework.http.HttpStatus
//import org.springframework.http.ResponseEntity
//import spock.lang.Shared
//import spock.lang.Specification
//import spock.lang.Stepwise
//
//import java.nio.charset.StandardCharsets
//
//import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
//
//@SpringBootTest(webEnvironment = RANDOM_PORT)
//@Import(IntegrationTestConfiguration)
//@Stepwise
//class JsonImportServiceImplSpec extends Specification {
//
//    @Autowired
//    private JsonImportServiceImpl jsonImportService
//
//    @Autowired
//    private JsonImportServiceImpl JsonImportService
//
//    @Autowired
//    private TestAppInstanceMapper instanceMapper
//
//    @Autowired
//    private ExcelImportServiceImpl ExcelImportService
//
//    @Autowired
//    private TestIssueFolderRelMapper issueFolderRelMapper
//
//    @Autowired
//    private TestAutomationResultMapper automationResultMapper
//
//    @Autowired
//    private TestAutomationHistoryMapper automationHistoryMapper
//
//    @Autowired
//    private TestCycleCaseMapper cycleCaseMapper
//
//
//    @Autowired
//    RedisTemplateUtil redisTemplateUtil
//
//    @Shared
//    private String report = new String(FileUtil.unTarGzToMemory(new ClassPathResource("mochawesome.json.tar.gz")
//            .file.newInputStream()).get(0), StandardCharsets.UTF_8)
//
//    @Shared
//    private String emptyReport = new String(FileUtil.unTarGzToMemory(new ClassPathResource("empty_mocha_report.tar.gz")
//            .file.newInputStream()).get(0), StandardCharsets.UTF_8)
//
//    @Shared
//    private List<TestAppInstanceDTO> instances = []
//
//    private List<TestAutomationHistoryDTO> automationHistories = []
//
//    def "importMochaReport"() {
//        given:
//        TestAppInstanceDTO testAppInstanceDTO = new TestAppInstanceDTO()
//        testAppInstanceDTO.setProjectId(1L)
//        testAppInstanceDTO.setAppId(1L)
//        testAppInstanceDTO.setProjectVersionId(1L)
//        testAppInstanceDTO.setCreatedBy(1L)
//        testAppInstanceDTO.setLastUpdatedBy(1L)
//        testAppInstanceDTO.setId(100000L)
//        instanceMapper.insert(testAppInstanceDTO)
//
//        RedisAtomicLong RAL = Mock(RedisAtomicLong)
//        redisTemplateUtil.getRedisAtomicLong(_, _) >> RAL
//        TestAppInstanceDTO instanceE = new TestAppInstanceDTO(
//                code: "mocha-test",
//                projectVersionId: 233L,
//                projectId: 144L,
//                appId: 662,
//                appVersionId: 582L
//        )
//        instances << instanceE
//        instanceMapper.insert(instanceE)
//        Long instanceId = instanceMapper.selectAll().get(0).id
//
//        TestAutomationHistoryDTO automationHistory = new TestAutomationHistoryDTO(instanceId: instanceId)
//        automationHistories << automationHistory
//        automationHistoryMapper.insert(automationHistory)
//
////        ProjectFeignClient projectFeignClient = Mock() {
////            _ * query(instanceE.projectId) >>
////            new ResponseEntity<>(new ProjectDTO(organizationId: 1L), HttpStatus.OK)
////        }
//        ApplicationFeignClient applicationFeignClient = Mock()
//
////        JsonImportService.setProjectFeignClient(projectFeignClient)
////        JsonImportService.setApplicationFeignClient(applicationFeignClient)
//
//        IssueFeignClient issueFeignClient = Mock() {
//            _ * queryDefaultPriority(instanceE.projectId, _ as Long) >>
//                    new ResponseEntity<>(new PriorityVO(id: 8L, isDefault: true), HttpStatus.OK)
//            _ * queryIssueType(instanceE.projectId, "test", _ as Long) >>
//                    new ResponseEntity<>([new IssueTypeVO(typeCode: "issue_test", id: 18L), new IssueTypeVO(typeCode: "issue_auto_test", id: 19L)], HttpStatus.OK)
//        }
//
////        ExcelImportService.setIssueFeignClient(issueFeignClient)
////        JsonImportService.setIssueFeignClient(issueFeignClient)
//        def issueDTOs = []
//        for (long i in 1000001L..1000009L) {
//            issueDTOs << new IssueDTO(issueId: i)
//        }
//        TestCaseService testCaseService = Mock() {
//            _ * createTest(_ as IssueCreateDTO, instanceE.projectId, "test") >>> issueDTOs
//        }
//
////        ExcelImportService.setTestCaseService(testCaseService)
//
//        when: "app instance 不存在"
//        jsonImportService.importMochaReport("att-662-582-${instanceId * 100000}", report)
//        then:
//        CommonException commonException = thrown()
//        commonException.message == "app instance 不存在"
//
//        when: "app version id 不存在"
//        jsonImportService.importMochaReport("att-662-582000000-$instanceId", report)
//        then:
//        1 * applicationFeignClient.getAppversion(instanceE.projectId, _ as List<Long>) >>
//                new ResponseEntity<>([new ApplicationVersionRepDTO(version: "测试版本号")], HttpStatus.INTERNAL_SERVER_ERROR)
//        (0..1) * applicationFeignClient.queryByAppId(instanceE.projectId, _ as Long) >>
//                new ResponseEntity<>(new ApplicationRepDTO(name: "应用名称"), HttpStatus.OK)
//        CommonException completionException = thrown()
//        completionException.message == "error.get.app.version.name"
//
//        when: "app id 不存在"
//        jsonImportService.importMochaReport("att-662000000-582-$instanceId", report)
//        then:
//        (0..1) * applicationFeignClient.getAppversion(instanceE.projectId, _ as List<Long>) >>
//                new ResponseEntity<>([new ApplicationVersionRepDTO(version: "测试版本号")], HttpStatus.OK)
//        1 * applicationFeignClient.queryByAppId(instanceE.projectId, _ as Long) >>
//                new ResponseEntity<>(new ApplicationRepDTO(name: "应用名称"), HttpStatus.INTERNAL_SERVER_ERROR)
//        completionException = thrown()
//        completionException.message == "error.get.app.name"
//
//        when: "第一次导入"
//        Long reportId = jsonImportService.importMochaReport("att-662-582-$instanceId", report)
//        automationResultMapper.deleteByPrimaryKey(reportId)
//        then:
//        1 * applicationFeignClient.getAppversion(instanceE.projectId, _ as List<Long>) >>
//                new ResponseEntity<>([new ApplicationVersionRepDTO(version: "2018.12.10-111111-master")], HttpStatus.OK)
//        1 * applicationFeignClient.queryByAppId(instanceE.projectId, _ as Long) >>
//                new ResponseEntity<>(new ApplicationRepDTO(name: "自动化测试mocha"), HttpStatus.OK)
//
//        when: "重试"
//        reportId = jsonImportService.importMochaReport("att-662-582-$instanceId", report)
//        for (long i in 1000001L..1000009L) {
//            issueFolderRelMapper.delete(new TestIssueFolderRelDTO(issueId: i))
//            cycleCaseMapper.delete(new TestCycleCaseDTO(issueId: i))
//        }
//        automationResultMapper.deleteByPrimaryKey(reportId)
//        then:
//        1 * applicationFeignClient.getAppversion(instanceE.projectId, _ as List<Long>) >>
//                new ResponseEntity<>([new ApplicationVersionRepDTO(version: "2018.12.10-111111-master")], HttpStatus.OK)
//        1 * applicationFeignClient.queryByAppId(instanceE.projectId, _ as Long) >>
//                new ResponseEntity<>(new ApplicationRepDTO(name: "自动化测试mocha"), HttpStatus.OK)
//
//        when: "测试用例数量为 0"
//        reportId = jsonImportService.importMochaReport("att-663-583-$instanceId", emptyReport)
//        automationResultMapper.deleteByPrimaryKey(reportId)
//        then:
//        1 * applicationFeignClient.getAppversion(instanceE.projectId, _ as List<Long>) >>
//                new ResponseEntity<>([new ApplicationVersionRepDTO(version: "2018.12.11-111111-master")], HttpStatus.OK)
//        1 * applicationFeignClient.queryByAppId(instanceE.projectId, _ as Long) >>
//                new ResponseEntity<>(new ApplicationRepDTO(name: "自动化测试mocha"), HttpStatus.OK)
//    }
//}
