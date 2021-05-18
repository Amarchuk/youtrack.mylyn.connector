package com.jetbrains.mylyn.yt.ui;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.jetbrains.mylyn.yt.core.YouTrackRepositoryConnector;
import com.jetbrains.youtrack.javarest.client.YouTrackClient;

public class YouTrackCommandWindowWizard extends Wizard {

  public YouTrackCommandDialogPage commandDialogPage = new YouTrackCommandDialogPage(
      "Apply Command");

  private TaskData taskData;

  private TaskRepository taskRepository;

  private TaskEditor editor;

  private static final String CANT_SUBMIT = "Can't submit incorrect command.";

  private String importedCommand;

  public class YouTrackCommandDialogPage extends WizardPage {

    private Text commandBoxText;

    protected YouTrackCommandDialogPage(String pageName) {
      super(pageName);
      setTitle(pageName);
      setPageComplete(false);
    }

    @Override
    public void createControl(Composite parent) {

      Composite composite = new Composite(parent, SWT.NONE);
      GridDataFactory.fillDefaults().align(SWT.FILL, SWT.FILL).grab(true, true).applyTo(composite);
      GridLayout layout = new GridLayout(1, false);
      composite.setLayout(layout);

      GridData gd = new GridData(SWT.FILL);
      gd.grabExcessHorizontalSpace = true;
      gd.horizontalAlignment = SWT.FILL;
      composite.setLayoutData(gd);

      Label commandBoxLabel = new Label(composite, SWT.FILL);
      String issueId =
          YouTrackRepositoryConnector.getYoutrackIssueId(getTaskData().getTaskId());
      commandBoxLabel.setText("Command for " + issueId + ": "
          + getTaskData().getRoot().getMappedAttribute(TaskAttribute.SUMMARY).getValue());

      commandBoxText = new Text(composite, SWT.SINGLE | SWT.FILL);
      if (importedCommand != null) {
        commandBoxText.setText(importedCommand);
        commandBoxText.setSelection(commandBoxText.getCharCount());
      }
      getCommandBoxText().setLayoutData(gd);

      if (issueId != null) {
        getCommandBoxText().addFocusListener(
            new IntellisenseFocusAdapter(getClient(), false, null, issueId));
      }

      setControl(composite);
    }

    @Override
    public boolean isPageComplete() {
      return true;
    }

    public Text getCommandBoxText() {
      return commandBoxText;
    }

    public void setCommandBoxText(String text) {
      if (commandBoxText == null) {
        importedCommand = text;
      } else {
        commandBoxText.setText(text);
      }
    }
  }

  public YouTrackCommandWindowWizard(TaskData data, TaskRepository repository, TaskEditor editor) {
    super();
    setWindowTitle("Command Dialog");
    setTaskData(data);
    setTaskRepository(repository);
    setEditor(editor);
  }

  @Override
  public boolean performFinish() {
    return true;
  }

  public boolean finishPressed() {
    if (getCommandDialogPage().getCommandBoxText().getText().trim().length() == 0) {
      getCommandDialogPage().setErrorMessage(CANT_SUBMIT);
      return false;
    } else {
      try {
        getClient().applyCommand(
            YouTrackRepositoryConnector.getYoutrackIssueId(getTaskData().getTaskId()),
            getCommandDialogPage().getCommandBoxText().getText());
        return true;
      } catch (RuntimeException e) {
        getCommandDialogPage().setErrorMessage(CANT_SUBMIT);
        return false;
      }
    }
  }

  @Override
  public void addPages() {
    addPage(getCommandDialogPage());
  }

  public TaskData getTaskData() {
    return taskData;
  }

  public void setTaskData(TaskData taskData) {
    this.taskData = taskData;
  }

  public TaskRepository getTaskRepository() {
    return taskRepository;
  }

  public void setTaskRepository(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  private YouTrackClient getClient() {
    return YouTrackRepositoryConnector.getClient(getTaskRepository());
  }

  public TaskEditor getEditor() {
    return editor;
  }

  public void setEditor(TaskEditor editor) {
    this.editor = editor;
  }

  YouTrackCommandDialogPage getCommandDialogPage() {
    return commandDialogPage;
  }

  public void setCommandDialogPage(YouTrackCommandDialogPage commandDialogPage) {
    this.commandDialogPage = commandDialogPage;
  }

}
