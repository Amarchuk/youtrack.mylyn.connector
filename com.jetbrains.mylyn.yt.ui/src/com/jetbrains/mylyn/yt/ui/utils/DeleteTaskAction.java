package com.jetbrains.mylyn.yt.ui.utils;

import java.util.Arrays;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mylyn.commons.ui.CommonImages;
import org.eclipse.mylyn.internal.tasks.ui.actions.DeleteAction;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPage;

import com.jetbrains.mylyn.yt.core.YouTrackRepositoryConnector;

public class DeleteTaskAction extends DeleteAction {

  public static final String ID = "org.eclipse.mylyn.editor.actions.delete"; //$NON-NLS-1$

  private final ITask task;

  private final boolean isNew;

  private final TaskRepository repository;

  private AbstractTaskEditorPage page;

  public DeleteTaskAction(ITask task, boolean isNew, TaskRepository repository,
      AbstractTaskEditorPage page) {
    Assert.isNotNull(task);
    this.task = task;
    this.isNew = isNew;
    this.repository = repository;
    this.page = page;
    if (isNew) {
      setToolTipText("Delete task");
    } else {
      setToolTipText("Delete task from tracker");
    }
    setId(ID);
    setActionDefinitionId(null);
    setImageDescriptor(CommonImages.REMOVE);
  }

  @Override
  public IStructuredSelection getStructuredSelection() {
    if (task != null) {
      return new StructuredSelection(task);
    } else {
      return super.getStructuredSelection();
    }
  }

  @Override
  public void run() {
    if (isNew) {
      super.doDelete(getStructuredSelection().toList());
    } else {
      boolean confirm =
          MessageDialog.openConfirm(null, "Confirm delete", "Delete task from tracker?" + "\n\n" //$NON-NLS-1$
              + task.getSummary());
      if (confirm) {
        YouTrackRepositoryConnector.getClient(repository).deleteIssue(
            YouTrackRepositoryConnector.getYoutrackIssueId(task.getTaskId()));
        super.performDeletion(Arrays.asList(task));
        page.close();
      }
    }
  }
}
