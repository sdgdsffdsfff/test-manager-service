package script.db.groovy.test_manager_service

/**
 * @author lihao* @since 2020/08/04
 */
databaseChangeLog(logicalFilePath: "script/db/test_api_case.groovy") {
    changeSet(author: 'lihao', id: '2020-08-04-init_table_test_api_case') {
        createTable(tableName: "test_api_case", remarks: "接口测试用例") {
            column(name: 'id', type: 'BIGINT UNSIGNED', autoIncrement: true, remarks: '表ID，主键，供其他表做外键，unsigned bigint、单表时自增、步长为 1') {
                constraints(primaryKey: true)
            }
            column(name: 'name', type: "VARCHAR(32)", remarks: '测试用例名称')
            column(name: 'description', type: "VARCHAR(64)", remarks: '测试用例描述')
            column(name: 'code', type: 'VARCHAR(32)', remarks: '用例编号')
            column(name: 'project_id', type: 'BIGINT UNSIGNED', remarks: '项目id')
            column(name: 'folder_id', type: 'BIGINT UNSIGNED', remarks: '所属文件夹')
            column(name: 'service_name', type: 'VARCHAR(32)', remarks: '所属服务名称')
            column(name: 'method', type: 'VARCHAR(6)', remarks: '请求方法')
            column(name: 'path', type: 'VARCHAR(256)', remarks: 'path')
            column(name: "request_param", type: "text")
            column(name: "request_body", type: "text")
            column(name: "created_by", type: "BIGINT UNSIGNED", defaultValue: "0")
            column(name: "creation_date", type: "DATETIME", defaultValueComputed: "CURRENT_TIMESTAMP")
            column(name: "last_updated_by", type: "BIGINT UNSIGNED", defaultValue: "0")
            column(name: "last_update_date", type: "DATETIME", defaultValueComputed: "CURRENT_TIMESTAMP")
        }
    }

    changeSet(id: '2020-08-04-test-api-case-add-index', author: 'lihao') {
        createIndex(tableName: "test_api_case", indexName: "idx_project_id") {
            column(name: "project_id")
        }
        createIndex(tableName: "test_api_case", indexName: "idx_folder_id") {
            column(name: "folder_id")
        }
    }
}