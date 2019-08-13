import React, { useContext, useState, useRef, createRef } from 'react';
import { observer } from 'mobx-react-lite';
import { withRouter } from 'react-router-dom';
import { injectIntl, FormattedMessage } from 'react-intl';
import { Input, Button } from 'choerodon-ui';
import PageStore from '../../../stores';
import Editor from '../../../../../components/Editor';
import FileUpload from '../file-upload';

function EditMode() {
  const { pageStore, type: levelType } = useContext(PageStore);
  const { getDoc: { pageInfo, userSettingVO, workSpace }, getFileList: fileList } = pageStore;
  const initialEditType = userSettingVO ? userSettingVO.editMode : undefined;
  const [title, setTitle] = useState(pageInfo.title);
  const [loading, setLoading] = useState(false);
  let editorRef = createRef();
  const [removeList, setRemoveList] = useState([]);

  function handleFileListChange(e) {
    const newFileList = e.fileList;
    if (e.file.status === 'removed' && e.file.id) {
      setRemoveList([...removeList, e.file.id]);
    } else if (e.file.status === 'removed') {
      Choerodon.prompt('无法删除正在上传的附件！');
      newFileList.unshift(e.file);
    }
    pageStore.setFileList(newFileList);
  }

  function handleBeforeUpload(file) {
    const config = {
      pageId: pageInfo.id,
      versionId: pageInfo.versionId,
      uid: file.uid,
    };
    const formData = new FormData();
    formData.append('file', file);
    Choerodon.prompt('附件上传中...');
    pageStore.uploadFile(formData, config);
    return false;
  }

  function handleCancelClick() {
    const docData = pageStore.getDoc;
    const { pageInfo: { id }, hasDraft } = docData;
    if (hasDraft) {
      pageStore.deleteDraftDoc(id);
    }
    pageStore.setMode('view');
  }

  function editDoc(doc) {
    pageStore.editDoc(workSpace.id, doc).then(() => {
      // 更新workSpace
      // const spaceCode = levelType === 'project' ? 'pro' : 'org';
      // const workSpaceData = pageStore.getWorkSpace;
      // pageStore.setWorkSpaceByCode(spaceCode, {
      //   ...workSpaceData[spaceCode].data,
      //   items: {
      //     ...workSpaceData[spaceCode].data.items,
      //     [workSpace.id]: {
      //       ...workSpaceData[spaceCode].data.items[workSpace.id],
      //       data: {
      //         title: doc.title,
      //       },
      //     },
      //   },
      // });
      setLoading(false);
      handleCancelClick();
    }).catch(() => {
      setLoading(false);
    });
  }

  function handleSaveClick() {
    setLoading(true);
    const md = editorRef.current && editorRef.current.editorInst && editorRef.current.editorInst.getMarkdown();
    const editMode = editorRef.current && editorRef.current.editorInst && editorRef.current.editorInst.currentMode;
    const doc = {
      title: title && title.trim(),
      content: md || '',
      minorEdit: false,
      objectVersionNumber: pageInfo.objectVersionNumber,
    };
    // 修改默认编辑模式
    if (initialEditType !== editMode) {
      const mode = {
        editMode,
        type: 'edit_mode',
      };
      if (userSettingVO) {
        mode.id = userSettingVO.id;
        mode.objectVersionNumber = userSettingVO.objectVersionNumber;
      }
      pageStore.editDefaultMode(mode);
    }
    if (removeList.length) {
      pageStore.batchDeleteFile(removeList).then(() => {
        editDoc(doc);
      }).catch(() => {
        editDoc(doc);
      });
    } else {
      editDoc(doc);
    }
  }

  function handleAutoSave() {
    const md = editorRef.current && editorRef.current.editorInst && editorRef.current.editorInst.getMarkdown();
    const doc = {
      content: md || '',
    };
    pageStore.autoSaveDoc(pageInfo.id, doc);
  }

  function setEditorRef(e) {
    editorRef = e;
  }

  function handleTitleChange(e) {
    setTitle(e.target.value);
  }

  return (
    <React.Fragment>
      <div className="c7n-doc-editMode-header" style={{ padding: 10, paddingBottom: 0 }}>
        <Input
          size="large"
          showLengthInfo={false}
          maxLength={40}
          style={{ maxWidth: 684, width: 'calc(100% - 150px)' }}
          defaultValue={title}
          onChange={handleTitleChange}
        />
        <Button
          funcType="raised"
          type="primary"
          style={{ marginLeft: 10, verticalAlign: 'middle' }}
          onClick={handleSaveClick}
          loading={loading}
        >
          <FormattedMessage id="save" />
        </Button>
        <Button
          funcType="raised"
          style={{ marginLeft: 10, verticalAlign: 'middle' }}
          onClick={handleCancelClick}
        >
          <FormattedMessage id="cancel" />
        </Button>
        <FileUpload
          fileList={fileList.map(file => (file.id ? ({ ...file, uid: file.id }) : file))}
          beforeUpload={handleBeforeUpload}
          onChange={handleFileListChange}
        />
      </div>
      <Editor
        data={pageInfo.content}
        initialEditType={initialEditType}
        editorRef={setEditorRef}
        onSave={handleAutoSave}
      />
    </React.Fragment>
  );
}

export default injectIntl(observer(EditMode));
