/**
 * @author: amarch
 */

package com.jetbrains.mylyn.yt.ui;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mylyn.internal.tasks.ui.editors.EditorUtil;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorOutlineNode;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorRichTextPart;
import org.eclipse.mylyn.tasks.core.IRepositoryElement;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPage;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPart;
import org.eclipse.mylyn.tasks.ui.editors.AttributeEditorFactory;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditorPartDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
import org.eclipse.ui.forms.IManagedForm;

import com.jetbrains.mylyn.yt.core.YouTrackCorePlugin;
import com.jetbrains.mylyn.yt.core.YouTrackRepositoryConnector;
import com.jetbrains.mylyn.yt.core.YouTrackTaskDataHandler;
import com.jetbrains.mylyn.yt.ui.utils.DeleteTaskAction;
import com.jetbrains.mylyn.yt.ui.utils.EditAction;
import com.jetbrains.mylyn.yt.ui.utils.RevertAction;
import com.jetbrains.mylyn.yt.ui.utils.SubmitAction;
import com.jetbrains.mylyn.yt.ui.utils.SynchronizeEditorAction;
import com.jetbrains.mylyn.yt.ui.utils.UpdateProjectAction;
import com.jetbrains.mylyn.yt.ui.utils.WebViewAction;

public class YouTrackTaskEditorPage extends AbstractTaskEditorPage {

  public final static String ID_NEW_COMMENTS_PART = "ID_NEW_COMMENTS_PART";

  private boolean refreshed = false;

  /**
   * submit task by Ctrl+Enter
   * 
   * open command dialog by CTRL+ALT+J
   */
  private class UserKeyPressedListener implements Listener {

    @Override
    public void handleEvent(Event e) {
      if (((e.stateMask & SWT.CTRL) == SWT.CTRL) && (e.keyCode == 13)) {
        doSubmit();
      } else if (e.stateMask == 327680 && e.keyCode == 106) {
        // press CTRL+ALT+J

        String issueId =
            YouTrackRepositoryConnector.getYoutrackIssueId(
                ((AbstractTaskEditorPage) ((TaskEditor) PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getActivePage().getActiveEditor())
                    .getActivePageInstance()).getTask().getTaskId());

        if (issueId != null && issueId.contains("-")) {
          YouTrackSummaryPart.openCommandWizard(getEditorComposite().getShell(), null, true,
              (AbstractTaskEditorPage) ((TaskEditor) PlatformUI.getWorkbench()
                  .getActiveWorkbenchWindow().getActivePage().getActiveEditor())
                  .getActivePageInstance());
        }
      }
    }
  }

  private static UserKeyPressedListener userKeyPressedListener;

  public YouTrackTaskEditorPage(TaskEditor editor) {
    super(editor, YouTrackCorePlugin.CONNECTOR_KIND);
    setNeedsPrivateSection(false);
    setNeedsSubmitButton(true);

    if (userKeyPressedListener == null) {
      userKeyPressedListener = new UserKeyPressedListener();
      PlatformUI.getWorkbench().getDisplay().addFilter(SWT.KeyDown, userKeyPressedListener);
    }
  }

  @Override
  protected Set<TaskEditorPartDescriptor> createPartDescriptors() {
    Set<TaskEditorPartDescriptor> descriptors = super.createPartDescriptors();

    for (Iterator<TaskEditorPartDescriptor> it = descriptors.iterator(); it.hasNext();) {
      TaskEditorPartDescriptor taskEditorPartDescriptor = it.next();
      if (taskEditorPartDescriptor.getId().equals(ID_PART_PEOPLE)
          || taskEditorPartDescriptor.getId().equals(ID_PART_ATTRIBUTES)
          || taskEditorPartDescriptor.getId().equals(ID_PART_COMMENTS)
          || taskEditorPartDescriptor.getId().equals(ID_PART_ACTIONS)
          || taskEditorPartDescriptor.getId().equals(ID_PART_SUMMARY)
          || taskEditorPartDescriptor.getId().equals(ID_PART_DESCRIPTION)) {
        it.remove();
      }
    }

    descriptors.add(new TaskEditorPartDescriptor(ID_PART_SUMMARY) {
      @Override
      public AbstractTaskEditorPart createPart() {
        return new YouTrackSummaryPart();
      }
    }.setPath(PATH_HEADER));

    descriptors.add(new TaskEditorPartDescriptor(ID_PART_ATTRIBUTES) {
      @Override
      public AbstractTaskEditorPart createPart() {
        YouTrackAttributesPart part = new YouTrackAttributesPart();
        part.setExpandVertically(true);
        return new YouTrackAttributesPart();
      }
    }.setPath(PATH_ATTRIBUTES));

    descriptors.add(new TaskEditorPartDescriptor(ID_PART_DESCRIPTION) {
      @Override
      public AbstractTaskEditorPart createPart() {
        YouTrackDescriptionPart part = new YouTrackDescriptionPart();
        return part;
      }
    }.setPath(PATH_ATTRIBUTES));

    descriptors.add(new TaskEditorPartDescriptor(ID_PART_COMMENTS) {
      @Override
      public AbstractTaskEditorPart createPart() {
        AbstractTaskEditorPart part = new YouTrackTaskEditorCommentsPart();
        return part;
      }
    }.setPath(PATH_COMMENTS));

    descriptors.add(new TaskEditorPartDescriptor(ID_NEW_COMMENTS_PART) {
      @Override
      public AbstractTaskEditorPart createPart() {
        AbstractTaskEditorPart part = new YouTrackNewCommentPart();
        return part;
      }
    }.setPath(PATH_COMMENTS));


    return descriptors;
  }

  @Override
  public void fillToolBar(final IToolBarManager toolBarManager) {

    Action submitAction = new SubmitAction(this);
    toolBarManager.add(submitAction);

    Action synchronizeEditorAction = new SynchronizeEditorAction();
    ((BaseSelectionListenerAction) synchronizeEditorAction)
        .selectionChanged(new StructuredSelection(getTaskEditor()));
    toolBarManager.add(synchronizeEditorAction);

    Action editAction = new EditAction(this);
    toolBarManager.add(editAction);

    Action revertAction =
        new RevertAction(Collections.singletonList((IRepositoryElement) getTask()));
    ((RevertAction) revertAction).setTaskEditorPage(this);
    toolBarManager.add(revertAction);

    Action webViewAction = new WebViewAction(this);
    toolBarManager.add(webViewAction);

    Action updateProjectSettings = new UpdateProjectAction(this);
    toolBarManager.add(updateProjectSettings);

    DeleteTaskAction deleteAction =
        new DeleteTaskAction(getTask(), getModel().getTaskData().isNew(), getModel()
            .getTaskRepository(), this);
    toolBarManager.add(deleteAction);
  }

  public void doEdit() {
    YouTrackTaskDataHandler.setEnableEditMode(true);
    getEditor().refreshPages();
    YouTrackTaskDataHandler.setEnableEditMode(false);
  }

  @Override
  protected AttributeEditorFactory createAttributeEditorFactory() {
    return new YouTrackAttributeEditorFactory(getModel(), getTaskRepository(), getEditorSite());
  }

  @Override
  public void appendTextToNewComment(String text) {
    AbstractTaskEditorPart newCommentPart = getPart(ID_NEW_COMMENTS_PART);
    if (newCommentPart instanceof TaskEditorRichTextPart) {
      ((TaskEditorRichTextPart) newCommentPart).appendText(text);
      newCommentPart.setFocus();
    }
  }

  @Override
  protected void createFormContent(final IManagedForm managedForm) {
    super.createFormContent(managedForm);

    // TODO: fix here, need refresh because Layout of Description and
    // Attributes parts not resizes properly
    PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
      public void run() {
        YouTrackTaskEditorPage.this.refresh();
      }
    });
  }

  @Override
  protected void createParts() {
    List<TaskEditorPartDescriptor> descriptors =
        new LinkedList<TaskEditorPartDescriptor>(createPartDescriptors());
    // single column
    createParts(PATH_HEADER, getEditorComposite(), descriptors);
    // two column
    // Composite bottomComposite =
    // getManagedForm().getToolkit().createComposite(getEditorComposite());
    // bottomComposite.setLayout(GridLayoutFactory.fillDefaults().numColumns(2).create());
    // GridDataFactory.fillDefaults().grab(true, false).applyTo(bottomComposite);
    // createParts(PATH_ACTIONS, bottomComposite, descriptors);
    // createParts(PATH_PEOPLE, bottomComposite, descriptors);
    // bottomComposite.pack(true);
    createParts(PATH_ATTRIBUTES, getEditorComposite(), descriptors);
    createParts(PATH_COMMENTS, getEditorComposite(), descriptors);
  }

  private void createParts(String path, final Composite parent,
      final Collection<TaskEditorPartDescriptor> descriptors) {
    for (Iterator<TaskEditorPartDescriptor> it = descriptors.iterator(); it.hasNext();) {
      final TaskEditorPartDescriptor descriptor = it.next();
      if (path == null || path.equals(descriptor.getPath())) {
        SafeRunner.run(new ISafeRunnable() {
          public void handleException(Throwable e) {}

          public void run() throws Exception {
            AbstractTaskEditorPart part = descriptor.createPart();
            if (part instanceof YouTrackDescriptionPart) {
              ((YouTrackDescriptionPart) part).setPartId(descriptor.getId());
            } else if (part instanceof YouTrackAttributesPart) {
              ((YouTrackAttributesPart) part).setPartId(descriptor.getId());
            } else if (part instanceof YouTrackNewCommentPart) {
              ((YouTrackNewCommentPart) part).setPartId(descriptor.getId());
            } else if (part instanceof YouTrackSummaryPart) {
              ((YouTrackSummaryPart) part).setPartId(descriptor.getId());
            }
            initializePart(parent, part, descriptors);
          }
        });
        it.remove();
      }
    }
  }

  private void initializePart(Composite parent, AbstractTaskEditorPart part,
      Collection<TaskEditorPartDescriptor> descriptors) {
    getManagedForm().addPart(part);
    part.initialize(this);
    if (parent != null) {
      part.createControl(parent, getManagedForm().getToolkit());
      if (part.getControl() != null) {
        if (ID_PART_ACTIONS.equals(part.getPartId())) {
          // do not expand horizontally
          GridDataFactory.fillDefaults().align(SWT.FILL, SWT.FILL).grab(false, false)
              .applyTo(part.getControl());
        } else {
          if (part.getExpandVertically()) {
            GridDataFactory.fillDefaults().align(SWT.FILL, SWT.FILL).grab(true, true)
                .applyTo(part.getControl());
          } else {
            GridDataFactory.fillDefaults().align(SWT.FILL, SWT.TOP).grab(true, false)
                .applyTo(part.getControl());
          }
        }
        // for outline
        if (ID_PART_COMMENTS.equals(part.getPartId())) {
          EditorUtil.setMarker(part.getControl(), TaskEditorOutlineNode.LABEL_COMMENTS);
        } else if (ID_PART_ATTACHMENTS.equals(part.getPartId())) {
          EditorUtil.setMarker(part.getControl(), TaskEditorOutlineNode.LABEL_ATTACHMENTS);
        }
      }
    }
  }

  @Override
  public void doSubmit() {
    super.doSubmit();
  }
}
