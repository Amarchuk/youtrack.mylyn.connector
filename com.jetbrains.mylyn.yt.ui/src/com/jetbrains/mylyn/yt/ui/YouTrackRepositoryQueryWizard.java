package com.jetbrains.mylyn.yt.ui;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.wizards.RepositoryQueryWizard;
import org.eclipse.osgi.util.NLS;

import com.jetbrains.mylyn.yt.core.YouTrackRepositoryConnector;

public class YouTrackRepositoryQueryWizard extends RepositoryQueryWizard {

  private TaskRepository repository;

  int queryIssuesAmount;

  public YouTrackRepositoryQueryWizard(TaskRepository repository) {
    super(repository);
    this.repository = repository;
  }

  @Override
  public boolean performFinish() {

    IWizardPage currentPage = getContainer().getCurrentPage();
    if (!(currentPage instanceof YouTrackRepositoryQueryPage)) {
      throw new AssertionError(NLS.bind(
          "Current wizard page ''{0}'' does not extends YouTrackRepositoryQueryPage",
          currentPage.getClass()));
    }

    YouTrackRepositoryQueryPage thisPage = (YouTrackRepositoryQueryPage) currentPage;
    queryIssuesAmount = thisPage.getQueryIssuesAmount();
    String lastCountForFilterString = thisPage.getCountForFilterString();

    if (thisPage.isCustomQuery()
        && thisPage.getSearchBoxText().getText() != lastCountForFilterString) {

      final String queryFilter = thisPage.getSearchBoxText().getText();
      System.out.println("called performFinish(" + queryFilter + ")");
      queryIssuesAmount =
          YouTrackRepositoryConnector.getClient(repository).getNumberOfIssues(queryFilter);
    }

    String messageDialogText;
    if (queryIssuesAmount == -1) {
      messageDialogText = "Can't get number of issues for this query. Continue?";
    } else if (queryIssuesAmount == 1) {
      messageDialogText = "Query contains 1 issue. Continue?";
    } else {
      messageDialogText = "Query contains " + queryIssuesAmount + " issues. Continue?";
    }

    boolean confirm = MessageDialog.openConfirm(null, "Confirm query", messageDialogText);
    if (confirm) {
      return super.performFinish();
    } else {
      return false;
    }
  }

}
