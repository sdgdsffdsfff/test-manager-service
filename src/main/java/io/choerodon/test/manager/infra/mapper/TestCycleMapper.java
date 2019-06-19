package io.choerodon.test.manager.infra.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import io.choerodon.mybatis.common.Mapper;
import io.choerodon.test.manager.infra.dataobject.TestCycleDO;

/**
 * Created by 842767365@qq.com on 6/11/18.
 */
public interface TestCycleMapper extends Mapper<TestCycleDO> {

    List<TestCycleDO> query(@Param("projectId") Long projectId, @Param("versionIds") Long[] versionId, @Param("assignedTo") Long assignedTo);

    List<TestCycleDO> queryOneCycleBar(@Param("cycleId") Long cycleId);

    /**
     * 获取version下的所有循环Id
     *
     * @param versionIds
     * @return
     */
    List<Long> selectCyclesInVersions(@Param("versionIds") Long[] versionIds);

    /**
     * 验证version下是否有重名cycle
     *
     * @param testCycleDO
     * @return
     */
    Long validateCycle(TestCycleDO testCycleDO);

    List<TestCycleDO> queryChildCycle(@Param("dto") TestCycleDO testCycleDO);

    List<TestCycleDO> queryCycleInVersion(@Param("dto") TestCycleDO testCycleDO);

    List<TestCycleDO> queryByIds(@Param("cycleIds") List<Long> cycleIds);

    void updateAuditFields(@Param("cycleIds") Long[] cycleId, @Param("userId") Long userId, @Param("date") Date date);

    String getCycleLastedRank(@Param("versionId") Long versionId);

    String getFolderLastedRank(@Param("cycleId") Long cycleId);

    Long getCycleCountInVersion(@Param("versionId") Long versionId);

    Long getFolderCountInCycle(@Param("cycleId") Long cycleId);

    List<TestCycleDO> queryChildFolderByRank(@Param("cycleId") Long cycleId);
}
