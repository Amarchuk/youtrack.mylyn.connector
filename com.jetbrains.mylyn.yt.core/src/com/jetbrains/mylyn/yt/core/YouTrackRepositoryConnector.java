/**
 * @author: amarch
 */

package com.jetbrains.mylyn.yt.core;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.mylyn.internal.tasks.core.RepositoryQuery;
import org.eclipse.mylyn.internal.tasks.core.TaskList;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylyn.tasks.core.IRepositoryQuery;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.AbstractTaskDataHandler;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.core.data.TaskDataCollector;
import org.eclipse.mylyn.tasks.core.data.TaskMapper;
import org.eclipse.mylyn.tasks.core.data.TaskRelation;
import org.eclipse.mylyn.tasks.core.sync.ISynchronizationSession;

import com.jetbrains.youtrack.javarest.client.YouTrackClient;
import com.jetbrains.youtrack.javarest.client.YouTrackClientFactory;
import com.jetbrains.youtrack.javarest.client.YouTrackIssue;
import com.jetbrains.youtrack.javarest.client.YouTrackProject;
import com.jetbrains.youtrack.javarest.utils.MyRunnable;
import com.jetbrains.youtrack.javarest.utils.YouTrackTimeSettings;

public class YouTrackRepositoryConnector extends AbstractRepositoryConnector {

  private static final long REPOSITORY_CONFIGURATION_UPDATE_INTERVAL = 2 * 60 * 60 * 1000;

  private static Map<TaskRepository, YouTrackTimeSettings> timeSettingsByRepository =
      new HashMap<TaskRepository, YouTrackTimeSettings>();

  private static Map<TaskRepository, YouTrackClient> clientByRepository =
      new HashMap<TaskRepository, YouTrackClient>();

  private static Map<TaskRepository, HashSet<YouTrackProject>> projectsByRepository =
      new HashMap<TaskRepository, HashSet<YouTrackProject>>();
  
  private static Map<String, String> mylyn2youtrackIssueId =
      new HashMap<String, String>();

  private final YouTrackTaskDataHandler taskDataHandler;

  private static final int MAX_ISSUES_PER_ONE_QUERY = 500;

  public final static String ISSUE_URL_PREFIX = "/issue/";

  private static YouTrackClientFactory clientFactory;

  public YouTrackRepositoryConnector() {
    taskDataHandler = new YouTrackTaskDataHandler(this);
    clientFactory = new YouTrackClientFactory();
  }

  public static YouTrackProject getProject(TaskRepository repository, String projectname) {
    if (!projectsByRepository.keySet().contains(repository)) {
      YouTrackClient client = getClient(repository);
      List<YouTrackProject> projects = client.getProjects();
      if (projects.size() == 0) {
        boolean loged = client.login(repository.getUserName(), repository.getPassword());
        return loged ? getProject(repository, projectname) : null;
      }
      projectsByRepository.put(repository, new HashSet<YouTrackProject>(projects));
    }

    HashSet<YouTrackProject> projects = projectsByRepository.get(repository);

    for (YouTrackProject project : projects) {
      if (project.getProjectShortName().equals(projectname)) {
        return project;
      }
    }
    return null;
  }

  public static void updateProjectCustomFields(TaskRepository repository, String projectname) {

    final YouTrackProject project = YouTrackRepositoryConnector.getProject(repository, projectname);

    final YouTrackClient client = getClient(repository);

    if (client != null) {
      timeSettingsByRepository.put(repository, client.getTimeTrackingSettings());
    }

    if (project != null) {
      if (!project.isCustomFieldsUpdated()) {
        project.updateCustomFields(client);
      }
    } else {
      for (YouTrackProject repoProject : projectsByRepository.get(repository)) {
        if (!repoProject.isCustomFieldsUpdated()) {
          repoProject.updateCustomFields(client);
        }
      }
    }
  }

  public static void forceUpdateProjectCustomFields(TaskRepository repository, String projectname) {

    final YouTrackProject project = YouTrackRepositoryConnector.getProject(repository, projectname);

    final YouTrackClient client = getClient(repository);

    if (client != null) {
      timeSettingsByRepository.put(repository, client.getTimeTrackingSettings());
    }

    if (project != null) {
      project.updateCustomFields(client);
    }
  }

  public static synchronized YouTrackClient getClient(TaskRepository repository) {
    YouTrackClient client = clientByRepository.get(repository);
    if (client == null) {
      client = clientFactory.getClient(repository.getRepositoryUrl());
      clientByRepository.put(repository, client);
      client.login(repository.getUserName(), repository.getPassword());
    }
    return client;
  }

  public static synchronized YouTrackTimeSettings getTimeSettings(TaskRepository repository) {
    YouTrackTimeSettings timeSettings = timeSettingsByRepository.get(repository);
    if (timeSettings == null) {
      timeSettings = getClient(repository).getTimeTrackingSettings();
      timeSettingsByRepository.put(repository, timeSettings);
    }
    return timeSettings;
  }

  @Override
  public boolean canCreateNewTask(TaskRepository repository) {
    return true;
  }

  @Override
  public boolean canCreateTaskFromKey(TaskRepository repository) {
    return true;
  }

  @Override
  public String getConnectorKind() {
    return YouTrackCorePlugin.CONNECTOR_KIND;
  }

  @Override
  public String getLabel() {
    return "YouTrack Repository";
  }

  @Override
  public String getRepositoryUrlFromTaskUrl(String taskUrl) {
    if (taskUrl == null) {
      return null;
    }
    int index = taskUrl.indexOf(ISSUE_URL_PREFIX);
    return (index != -1) ? taskUrl.substring(0, index) : null;
  }

  @Override
  public TaskData getTaskData(TaskRepository taskRepository, String taskId, IProgressMonitor monitor)
      throws CoreException {

    YouTrackIssue issue =
        getClient(taskRepository).getIssue(getYoutrackIssueId(taskId));
    // stall a while to allow the UI to update
    monitor.worked(10);
    return taskDataHandler.readTaskData(taskRepository, issue, monitor);
  }

  @Override
  public String getTaskIdFromTaskUrl(String taskUrl) {
    if (taskUrl == null) {
      return null;
    }
    int index = taskUrl.indexOf(ISSUE_URL_PREFIX);
    if (index != -1) {
      String taskId = taskUrl.substring(index + ISSUE_URL_PREFIX.length());
      if (taskId.contains("-")) {
        return taskId;
      }
    }
    return null;
  }

  @Override
  public String getTaskIdPrefix() {
    return "issue";
  }

  @Override
  public Collection<TaskRelation> getTaskRelations(TaskData taskData) {
    return null;
  }

  @Override
  public String getShortLabel() {
    return "YouTrack";
  }

  @Override
  public String getTaskUrl(String repositoryUrl, String taskId) {
    return repositoryUrl + ISSUE_URL_PREFIX + taskId;
  }

  @Override
  public boolean hasTaskChanged(TaskRepository taskRepository, ITask task, TaskData taskData) {
    // ignore
    return false;
  }

  @Override
  public IStatus performQuery(final TaskRepository repository, final IRepositoryQuery query,
      final TaskDataCollector collector, ISynchronizationSession session,
      final IProgressMonitor monitor) {

    return new MyRunnable() {
      @Override
      public void run() throws Exception {
        List<YouTrackIssue> issues = null;
        String projectname = query.getAttribute(YouTrackCorePlugin.QUERY_KEY_PROJECT);
        String filter = query.getAttribute(YouTrackCorePlugin.QUERY_KEY_FILTER);

        // ad ten because of state may update slowly and actual issues
        // count may be greater than rest returned
        int issuesCount = queryIssuesAmount(projectname, filter, repository);
        issues =
            getClient(repository).getIssuesByFilter(getFilter(projectname, filter, repository),
                issuesCount + 10);

        for (YouTrackIssue issue : issues) {
          TaskData taskData = taskDataHandler.readTaskData(repository, issue, monitor);
          collector.accept(taskData);
        }
      }
    }.execute("Query failed", YouTrackCorePlugin.ID_PLUGIN);

  }

  public String getFilter(String projectname, String filter, TaskRepository repository) {
    String returnFilter = new String(filter);
    if (filter != null) {
      if (projectname != null && !projectname.equals("")) {
        returnFilter += "project: " + projectname;
      }
    } else {
      returnFilter = "project: " + projectname;
    }
    return returnFilter;
  }

  public int queryIssuesAmount(String projectname, String filter, TaskRepository repository)
      throws CoreException {
	  String str = getFilter(projectname, filter, repository);
	  System.out.println("called queryIssuesAmount(" + str + ")");
    return getClient(repository).getNumberOfIssues(str);
  }

  @Override
  public TaskMapper getTaskMapping(TaskData taskData) {
    return new TaskMapper(taskData);
  }

  @Override
  public void updateTaskFromTaskData(TaskRepository repository, ITask task, TaskData taskData) {
    getTaskMapping(taskData).applyTo(task);
  }

  @Override
  public AbstractTaskDataHandler getTaskDataHandler() {
    return taskDataHandler;
  }

  public static String getYoutrackIssueId(String mylynIssueId) {
    if (mylyn2youtrackIssueId.get(mylynIssueId) != null) {
      return mylyn2youtrackIssueId.get(mylynIssueId);
    } else {
      return mylynIssueId;
    }
  }
  
  public static String getMylynIssueId(String youtrackIssueId) {
    if (youtrackIssueId.contains("-")) {
      String mylynIssueId = youtrackIssueId.replace("-", "_");
      mylyn2youtrackIssueId.put(mylynIssueId, youtrackIssueId);
      return mylynIssueId;
    } else {
      return youtrackIssueId;
    }
  }

  @Override
  public boolean canDeleteTask(TaskRepository repository, ITask task) {
    return true;
  }

  @Override
  public boolean isRepositoryConfigurationStale(TaskRepository repository, IProgressMonitor monitor)
      throws CoreException {
    Date configDate = repository.getConfigurationDate();
    if (configDate != null) {
      return (new Date().getTime() - configDate.getTime()) > REPOSITORY_CONFIGURATION_UPDATE_INTERVAL;
    }
    return true;
  }

  /*
   * update projects data for every existed project in every query associated with repository
   */
  @Override
  public void updateRepositoryConfiguration(TaskRepository taskRepository, IProgressMonitor monitor)
      throws CoreException {

    Set<String> projects = new HashSet<String>();
    TaskList taskList = TasksUiPlugin.getTaskList();
    for (RepositoryQuery query : taskList.getRepositoryQueries(taskRepository.getRepositoryUrl())) {
      for (ITask task : query.getChildren()) {
        projects.add(getProjectNameFromId(task.getTaskKey()));
      }
    }

    for (String projectname : projects) {
      forceUpdateProjectCustomFields(taskRepository, projectname);
    }
  }

  public String getProjectNameFromId(String taskId) {
    if (taskId != null && taskId.contains("-")) {
      return taskId.substring(0, taskId.indexOf("-"));
    } else {
      return null;
    }
  }

  @Override
  public void updateRepositoryConfiguration(TaskRepository taskRepository, ITask task,
      IProgressMonitor monitor) throws CoreException {
    forceUpdateProjectCustomFields(taskRepository, getProjectNameFromId(task.getTaskKey()));
  }
}
