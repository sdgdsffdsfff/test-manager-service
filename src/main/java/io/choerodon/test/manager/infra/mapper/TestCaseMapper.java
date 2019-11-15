package io.choerodon.test.manager.infra.mapper;

import java.util.List;
import java.util.Set;
import io.choerodon.mybatis.common.Mapper;
import io.choerodon.test.manager.api.vo.TestCaseRepVO;
import io.choerodon.test.manager.infra.dto.TestCaseDTO;
import io.choerodon.test.manager.infra.dto.TestCaseStepDTO;
import org.apache.ibatis.annotations.Param;

/**
 * @author zhaotianxin
 * @since 2019/11/14
 */
public interface TestCaseMapper extends Mapper<TestCaseDTO> {
    List<TestCaseDTO> listCaseByFolderIds(@Param("projectId") Long projectId,@Param("folderIds") Set<Long> folderIds);
}
