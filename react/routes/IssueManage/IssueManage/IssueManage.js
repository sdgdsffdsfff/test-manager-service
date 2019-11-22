import React, { Component } from 'react';
import { observer } from 'mobx-react';
import _ from 'lodash';
import {
  Page, Header, Content, Breadcrumb,
} from '@choerodon/boot';
import { Button, Icon } from 'choerodon-ui';
import { Modal } from 'choerodon-ui/pro/lib';
import { FormattedMessage, injectIntl } from 'react-intl';
import IssueStore from '../stores/IssueStore';
import { getParams } from '../../../common/utils';
import RunWhenProjectChange from '../../../common/RunWhenProjectChange';
import CreateIssue from '../components/CreateIssue';
import IssueTree from '../components/IssueTree';
import IssueTable from '../components/IssueTable';
import ExportSide from '../components/ExportSide';
import ImportSide from '../ImportIssue';
import TestCaseDetail from '../TestCaseDetail';
import './IssueManage.less';
import IssueTreeStore from '../stores/IssueTreeStore';

@injectIntl
@observer
export default class IssueManage extends Component {
  componentDidMount() {
    RunWhenProjectChange(IssueStore.clearStore);    
    this.getInit();
  }

  getInit = () => {
    const Request = getParams(this.props.location.search);
    const { paramName, paramIssueId } = Request;
    IssueStore.setParamName(paramName);
    IssueStore.setParamIssueId(paramIssueId);
    if (paramName && paramIssueId) {
      IssueStore.setClickIssue({
        issueId: paramIssueId,
      });
    }
    // 当参数中有用例名时，在table的筛选框中加入
    const barFilters = paramName ? [paramName] : [];
    IssueStore.setBarFilters(barFilters);    
    this.getTestCase();
  }

  getTestCase = async () => {
    await IssueTreeStore.loadIssueTree();   
    const { currentCycle } = IssueTreeStore;
    const { id } = currentCycle;
    if (id) {
      IssueStore.loadIssues();
    }
  }

  /**
   *
   * 用例创建后，默认选到目标文件夹
   * @param {*} issue
   * @param {*} folderId
   * @memberof IssueManage
   */
  handleCreateIssue(issue, folderId) {
    if (folderId) {
      IssueTreeStore.setCurrentCycleById(folderId);
    }   
    IssueStore.loadIssues();
  }


  handleTableRowClick = (record) => {
    IssueStore.setClickIssue(record);
  }


  saveRef = name => (ref) => {
    this[name] = ref;
  }

  handleClose = () => {
    IssueStore.setClickIssue({});
  }

  handleOpenCreateIssue = () => {
    const { intl } = this.props;
    Modal.open({
      key: 'createIssue',
      // title:<FormattedMessage id='issue_create_name'  />,
      title: intl.formatMessage({ id: 'issue_create_name' }),
      drawer: true,
      style: {
        width: 740,
      },
      children: (
        <CreateIssue
          onOk={this.handleCreateIssue.bind(this)}
          intl={intl}
        />
      ),
      okText: '创建',
    });
  }

  handleOpenImportIssue = () => {
    Modal.open({
      key: 'importIssue',
      // title:<FormattedMessage id='issue_create_name'  />,
      title: '导入用例',
      drawer: true,
      style: {
        width: 380,
      },
      children: (
        <ImportSide />
      ),
      // onOk: this.handleCreateIssue.bind(this),
    });
  }

  handleOpenExportIssue = () => {
    Modal.open({
      key: 'exportIssue',
      title: '导出用例',
      drawer: true,
      style: {
        width: 1090,
      },
      children: (
        <ExportSide />
      ),
    });
  }

  handleAddFolderClick = () => {   
    IssueTreeStore.treeRef.current.addFirstLevelItem();
  }

  render() {
    const { clickIssue } = IssueStore;
    const currentCycle = IssueTreeStore.getCurrentCycle;
    return (
      <Page className="c7ntest-Issue c7ntest-region">
        <Header
          title={<FormattedMessage id="issue_name" />}
        >
          <Button className="leftBtn" onClick={() => this.handleOpenCreateIssue()}>
            <Icon type="playlist_add icon" />
            <FormattedMessage id="issue_createTestIssue" />
          </Button>
          <Button icon="playlist_add" onClick={this.handleAddFolderClick}>
            创建一级目录
          </Button>
          <Button icon="unarchive" onClick={this.handleOpenExportIssue}>
            <FormattedMessage id="issue_export" />
          </Button>
          <Button className="leftBtn" onClick={this.handleOpenImportIssue}>
            {/* <Icon type="file_upload icon" /> */}
            <Icon type="archive" />
            <FormattedMessage id="issue_import" />
          </Button>
        </Header>
        <Breadcrumb />
        <Content style={{ display: 'flex', padding: '0', borderTop: '0.01rem solid rgba(0,0,0,0.12)' }}>          
          <IssueTree />  
          <div
            className="c7ntest-content-issue"
            style={{
              flex: 1,
              display: 'block',
              overflowY: 'auto',
              overflowX: 'hidden',
              padding: '0 20px',
            }}
          >
            <div className="c7ntest-content-issueFolderName">
              {
                currentCycle.id ? currentCycle.data.name : ''
              }
            </div>
            <IssueTable
              onClick={this.handleTableRowClick}
            />
          </div>
          <TestCaseDetail visible={clickIssue.caseId} onClose={this.handleClose} />
        </Content>
      </Page>
    );
  }
}
