/**
 * @author: amarch
 */

package com.jetbrains.mylyn.yt.core;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.tasks.core.ITaskMapping;
import org.eclipse.mylyn.tasks.core.RepositoryResponse;
import org.eclipse.mylyn.tasks.core.RepositoryResponse.ResponseKind;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.AbstractTaskDataHandler;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskAttributeMapper;
import org.eclipse.mylyn.tasks.core.data.TaskCommentMapper;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.core.data.TaskDataCollector;
import org.eclipse.osgi.util.NLS;

import util.CastCheck;

import com.jetbrains.youtrack.javarest.client.IssueLink;
import com.jetbrains.youtrack.javarest.client.IssueTag;
import com.jetbrains.youtrack.javarest.client.YouTrackClient;
import com.jetbrains.youtrack.javarest.client.YouTrackComment;
import com.jetbrains.youtrack.javarest.client.YouTrackCustomField;
import com.jetbrains.youtrack.javarest.client.YouTrackCustomField.YouTrackCustomFieldType;
import com.jetbrains.youtrack.javarest.client.YouTrackIssue;
import com.jetbrains.youtrack.javarest.client.YouTrackProject;
import com.jetbrains.youtrack.javarest.utils.EnumerationBundleValues;
import com.jetbrains.youtrack.javarest.utils.EnumerationValue;
import com.jetbrains.youtrack.javarest.utils.StateBundleValues;
import com.jetbrains.youtrack.javarest.utils.StateValue;
import com.jetbrains.youtrack.javarest.utils.UserBundleValues;
import com.jetbrains.youtrack.javarest.utils.UserValue;

public class YouTrackTaskDataHandler extends AbstractTaskDataHandler {

  private final YouTrackRepositoryConnector connector;
  // TODO: Rename?
  private static boolean enableEditMode = false;

  private static boolean postNewCommentMode = false;

  public static final String COMMENT_NEW = "TaskAttribute.COMMENT_NEW";

  public static final String USER_UPDATER = "TaskAttribute.USER_UPDATER";

  public static final String TYPE_PERIOD = "TaskAttribute.TYPE_PERIOD";

  public static final String WIKIFY_DESCRIPTION = "TaskAttribute.WIKIFY_DESCRIPTION";

  public static final String TYPE_HTML = "TaskAttribute.TYPE_HTML";

  public static final String LINK_PREFIX = "TaskAttribute.LINK_";

  public static final String TAG_PREFIX = "TaskAttribute.TAG_";

  public static final String NOT_WIKI_COMMENT_PREFIX = "TaskAttribute.NOT_WIKI_COMMENT_";

  public static final String CUSTOM_FIELD_KIND = "TaslAttributeKind.CUSTOM_FIELD_KIND";

  public static final String SINGLE_FIELD_KIND = "TaslAttributeKind.ORDINARY_FIELD_KIND";

  public static final SimpleDateFormat YOUTRACK_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

  public YouTrackTaskDataHandler(YouTrackRepositoryConnector connector) {
    this.connector = connector;
  }

  public static String getNameFromLabel(TaskAttribute attribute) {
    if (attribute == null || attribute.getMetaData() == null
        || attribute.getMetaData().getLabel() == null) {
      return null;
    }
    String label = attribute.getMetaData().getLabel();
    return label.substring(0, label.length() - 1);
  }

  private String labelFromName(String name) {
    return name + ":";
  }

  @Override
  public RepositoryResponse postTaskData(TaskRepository repository, TaskData taskData,
      Set<TaskAttribute> oldAttributes, IProgressMonitor monitor) throws CoreException {

    YouTrackClient client = YouTrackRepositoryConnector.getClient(repository);
    YouTrackIssue issue = new YouTrackIssue();


    if (postNewCommentMode) {
      // not using now
      String newComment = getNewComment(taskData);
      if (newComment != null && newComment.length() > 0) {
        client.addComment(taskData.getRoot().getAttribute(TaskAttribute.TASK_KEY).getValue(),
            newComment);
        taskData.getRoot().getMappedAttribute(COMMENT_NEW).clearValues();
        setPostNewCommentMode(false);
        return new RepositoryResponse(ResponseKind.TASK_UPDATED, taskData.getTaskId());
      }
    }

    try {
      issue = buildIssue(repository, taskData);

      if (taskData.isNew()) {
        String uploadIssueId = client.putNewIssue(issue);
        issue.setId(uploadIssueId);
        client.updateIssue(uploadIssueId, issue);
        return new RepositoryResponse(ResponseKind.TASK_CREATED, uploadIssueId);
      } else {
        // upload new comments
        String newComment = getNewComment(taskData);
        if (newComment != null && newComment.length() > 0) {
          client.addComment(taskData.getRoot().getAttribute(TaskAttribute.TASK_KEY).getValue(),
              newComment);
          taskData.getRoot().getMappedAttribute(COMMENT_NEW).clearValues();
        }

        client.updateIssue(taskData.getRoot().getAttribute(TaskAttribute.TASK_KEY).getValue(),
            issue);
        setEnableEditMode(false);
        return new RepositoryResponse(taskData.isNew()
            ? ResponseKind.TASK_CREATED
            : ResponseKind.TASK_UPDATED, taskData.getTaskId());
      }
    } catch (CoreException e) {
      if (issue.getId() != null) {
        client.deleteIssue(issue.getId());
      }
      throw new CoreException(new org.eclipse.core.runtime.Status(IStatus.ERROR,
          YouTrackCorePlugin.ID_PLUGIN, IStatus.OK, "Couldn't upload new issue: \n"
              + e.getStatus().getMessage(), e));
    }
  }

  public TaskAttribute createAttribute(TaskData data, YouTrackAttribute attribute) {
    TaskAttribute taskAttribute = data.getRoot().createAttribute(attribute.getName());
    taskAttribute.getMetaData().setReadOnly(attribute.isReadOnly()).setType(attribute.getType())
        .setLabel(attribute.getLabel()).setKind(attribute.getKind());
    return taskAttribute;
  }

  public TaskAttribute createAttribute(TaskData data, YouTrackCustomField field) {
    return createAttribute(data, field, true);
  }

  public TaskAttribute createAttribute(TaskData data, YouTrackCustomField field, boolean readOnly) {
    TaskAttribute attribute = data.getRoot().createAttribute(field.getName());
    attribute.getMetaData().setReadOnly(readOnly).setLabel(labelFromName(field.getName()))
        .setKind(CUSTOM_FIELD_KIND);

    if (YouTrackCustomFieldType.getTypeByName(field.getType()) != null && YouTrackCustomFieldType.getTypeByName(field.getType()).isSimple()) {
      if (YouTrackCustomFieldType.getTypeByName(field.getType()).equals(
          YouTrackCustomFieldType.DATE)) {
        attribute.getMetaData().setType(TaskAttribute.TYPE_DATE);
      } else {
        attribute.getMetaData().setType(TaskAttribute.TYPE_SHORT_TEXT);
      }
    } else {
      if (YouTrackCustomFieldType.getTypeByName(field.getType()) != null && YouTrackCustomFieldType.getTypeByName(field.getType()).singleField()) {
        if (YouTrackCustomFieldType.getTypeByName(field.getType()).equals(
            YouTrackCustomFieldType.USER_SINGLE)) {
          attribute.getMetaData().setType(TaskAttribute.TYPE_SINGLE_SELECT);
        } else {
          attribute.getMetaData().setType(TaskAttribute.TYPE_SINGLE_SELECT);
        }
      } else {
        attribute.getMetaData().setType(TaskAttribute.TYPE_MULTI_SELECT);
      }
    }

    if (field.getDefaultValues() != null) {
      attribute.setValues(field.getDefaultValues());
    } else {
      if (field.isCanBeEmpty()) {
        attribute.setValue(field.getEmptyText());
      }
    }
    return attribute;
  }

  @Override
  public boolean initializeTaskData(TaskRepository repository, TaskData data,
      ITaskMapping initializationData, IProgressMonitor monitor) throws CoreException {

    createAttribute(data, YouTrackAttribute.SUMMARY);
    createAttribute(data, YouTrackAttribute.DATE_CREATION);
    createAttribute(data, YouTrackAttribute.TASK_KEY);
    createAttribute(data, YouTrackAttribute.UPDATED_DATE);
    createAttribute(data, YouTrackAttribute.DESCRIPTION);
    createAttribute(data, YouTrackAttribute.WIKIFY_DESCRIPTION);
    createAttribute(data, YouTrackAttribute.USER_REPORTER);
    createAttribute(data, YouTrackAttribute.USER_UPDATER);
    createAttribute(data, YouTrackAttribute.USER_ASSIGNED);
    createAttribute(data, YouTrackAttribute.PRIORITY_LEVEL);
    createAttribute(data, YouTrackAttribute.RESOLVED);
    createAttribute(data, YouTrackAttribute.PROJECT);

    if (!data.isNew()) {
      createAttribute(data, YouTrackAttribute.COMMENT_NEW);
    }

    if (data.isNew()) {
      if (initializationData == null) {
        return false;
      }

      String product = initializationData.getProduct();
      if (product == null) {
        return false;
      }

      YouTrackProject project = YouTrackRepositoryConnector.getProject(repository, product);
      if (project == null) {
        return false;
      }

      if (!project.isCustomFieldsUpdated()) {
        project.updateCustomFields(YouTrackRepositoryConnector.getClient(repository));
      }

      for (YouTrackCustomField field : project.getCustomFields()) {
        createAttribute(data, field, false);
      }

      TaskAttribute attribute = data.getRoot().getMappedAttribute(TaskAttribute.PRODUCT);
      attribute.setValue(product);
      attribute.getMetaData().setReadOnly(true);
    }

    return true;
  }

  @Override
  public TaskAttributeMapper getAttributeMapper(TaskRepository repository) {
    return new TaskAttributeMapper(repository);
  }

  public TaskData readTaskData(TaskRepository repository, YouTrackIssue issue,
      IProgressMonitor monitor) throws CoreException {
    try {
      TaskData taskData = parseIssue(repository, issue, monitor);
      return taskData;
    } catch (Exception e) {
    	e.printStackTrace();
      throw new CoreException(new Status(IStatus.ERROR, YouTrackCorePlugin.ID_PLUGIN, NLS.bind(
          "Error parsing task {0}\n" + e.getMessage(), issue.getId()), e));
    }
  }

  public TaskData parseIssue(TaskRepository repository, YouTrackIssue issue,
      IProgressMonitor monitor) throws CoreException {

    issue.mapFields();

    /*
     * because valid id not contain '-': public static boolean isValidTaskId(String taskId) { return
     * !taskId.contains(HANDLE_DELIM); } see also createTaskData() in JiraTaskDataHandler
     */
    String issueId = issue.getId();
    TaskData taskData =
        new TaskData(getAttributeMapper(repository), repository.getConnectorKind(),
            repository.getRepositoryUrl(), issueId.replace("-", "_"));
    initializeTaskData(repository, taskData, null, monitor);

    TaskAttribute attribute = taskData.getRoot().getAttribute(TaskAttribute.SUMMARY);
    attribute.setValue(issue.getSummary());

    attribute = taskData.getRoot().getAttribute(TaskAttribute.DESCRIPTION);
    attribute.setValue(issue.getDescription());

    YouTrackIssue wikifyIssue = connector.getClient(repository).getIssue(issueId, true);
    attribute = taskData.getRoot().getAttribute(WIKIFY_DESCRIPTION);
    attribute.setValue(wikifyIssue.getDescription());

    attribute = taskData.getRoot().getAttribute(TaskAttribute.TASK_KEY);
    attribute.setValue(issueId);

    attribute = taskData.getRoot().getAttribute(TaskAttribute.DATE_CREATION);
    taskData.getAttributeMapper().setDateValue(attribute,
        new Date(Long.parseLong(issue.getSingleField("created"))));

    attribute = taskData.getRoot().getAttribute(TaskAttribute.DATE_MODIFICATION);
    taskData.getAttributeMapper().setDateValue(attribute,
        new Date(Long.parseLong(issue.getSingleField("updated"))));

    attribute = taskData.getRoot().getAttribute(TaskAttribute.USER_REPORTER);
    attribute.setValue(issue.getSingleField("reporterFullName"));

    attribute = taskData.getRoot().getAttribute(USER_UPDATER);
    attribute.setValue(issue.getSingleField("updaterFullName"));

    attribute = taskData.getRoot().getAttribute(TaskAttribute.PRODUCT);
    attribute.setValue(issue.getProjectName());
    YouTrackProject project =
        connector.getProject(repository, taskData.getRoot().getAttribute(TaskAttribute.PRODUCT)
            .getValue());

    if (!project.isCustomFieldsUpdated()) {
      project.updateCustomFields(connector.getClient(repository));
    }

    if (issue.getSingleCustomFieldValue("Assignee") != null) {
      attribute = taskData.getRoot().getAttribute(TaskAttribute.USER_ASSIGNED);
      attribute.setValue(issue.getSingleCustomFieldValue("Assignee"));
    }

    // Duplicate field from custom fileds
    // because 'priority' icon needs color index
    if (issue.getCustomFieldsValues().containsKey("Priority")) {
      attribute = taskData.getRoot().getAttribute(TaskAttribute.PRIORITY);
      if (!project.isCustomFieldsUpdated()) {
        project.updateCustomFields(connector.getClient(repository));
      }
      if (project.isCustomFieldsUpdated()
          // NPE
          && project.getCustomFieldsMap().get("Priority").getBundle() != null
          && project.getCustomFieldsMap().get("Priority").getBundle().getBundleValues() != null) {
        LinkedList<EnumerationValue> priorities =
            ((EnumerationBundleValues) project.getCustomFieldsMap().get("Priority").getBundle()
                .getBundleValues()).getEnumerationValues();
        for (EnumerationValue value : priorities) {
          if (value.getValue().equals(issue.getCustomFieldValue("Priority").get(0))) {
            attribute.setValue(new Integer(value.getColorIndex()).toString());
          }
        }
      }
    }

    for (IssueLink link : issue.getLinks()) {
      String role = link.getRole();
      if (!taskData.getRoot().getAttributes().containsKey(attributeNameFromLinkRole(role))) {
        attribute = taskData.getRoot().createAttribute(attributeNameFromLinkRole(role));
        attribute.getMetaData().setReadOnly(true).setType(TaskAttribute.TYPE_TASK_DEPENDENCY)
            .setLabel(capitalize(role) + ":");
        attribute.addValue(link.getValue());
      } else {
        attribute = taskData.getRoot().getMappedAttribute(attributeNameFromLinkRole(role));
        attribute.addValue("\n" + link.getValue());
      }
    }

    attribute = taskData.getRoot().createAttribute(TAG_PREFIX);
    attribute.getMetaData().setReadOnly(true).setType(TaskAttribute.TYPE_MULTI_SELECT)
        .setLabel("Tags:");
    if (issue.getTags() != null && issue.getTags().size() > 0) {
      int count = 0;
      for (IssueTag tag : issue.getTags()) {
        TaskAttribute attr = taskData.getRoot().createAttribute(TAG_PREFIX + count);
        attr.getMetaData().setReadOnly(true).setType(TaskAttribute.TYPE_SHORT_TEXT);
        attr.addValue(tag.getText());
        attribute.putOption(attr.getValue(), attr.getValue());
        attribute.addValue(attr.getValue());
        count++;
      }
    }

    if (wikifyIssue.getComments() != null && wikifyIssue.getComments().size() > 0) {

      TaskCommentMapper mapper = new TaskCommentMapper();
      int count = 0;
      for (YouTrackComment comment : wikifyIssue.getComments()) {

        mapper.setAuthor(repository.createPerson(comment.getAuthorName()));
        mapper.setCreationDate(comment.getCreationDate());
        mapper.setText(comment.getText());
        mapper.setNumber(count+1);

        TaskAttribute commentAttribute =
            taskData.getRoot().createAttribute(TaskAttribute.PREFIX_COMMENT + count);
        commentAttribute.getMetaData().setType(TaskAttribute.TYPE_COMMENT);
        mapper.applyTo(commentAttribute);
        TaskAttribute nonWikiComment =
            taskData.getRoot().createAttribute(NOT_WIKI_COMMENT_PREFIX + count);
        nonWikiComment.setValue(issue.getComments().get(count).getText());
        count++;
      }
    }

    for (YouTrackCustomField field : project.getCustomFields()) {

      TaskAttribute customFieldAttribute = createAttribute(taskData, field, true);

      if (issue.getCustomFieldsValues() != null
          && issue.getCustomFieldsValues().containsKey(field.getName())) {

        // check if issue complete
        if (YouTrackCustomFieldType.STATE.equals(YouTrackCustomFieldType.getTypeByName(field.getType()))) {
          LinkedList<StateValue> states =
              ((StateBundleValues) field.getBundle().getBundleValues()).getStateValues();
          for (StateValue state : states) {
            if (state.getValue().equals(issue.getSingleCustomFieldValue("State"))) {
              if (state.isResolved()) {
                attribute = taskData.getRoot().createAttribute(TaskAttribute.DATE_COMPLETION);
                attribute.getMetaData().setReadOnly(true).setType(TaskAttribute.TYPE_DATETIME)
                    .setLabel("Completed date:");
                taskData.getAttributeMapper().setDateValue(
                    attribute,
                    taskData.getAttributeMapper().getDateValue(
                        taskData.getRoot().getAttribute(TaskAttribute.DATE_MODIFICATION)));
                attribute = taskData.getRoot().getAttribute(TaskAttribute.STATUS);
                attribute.setValue("true");
              } else {
                break;
              }
            }
          }
          customFieldAttribute.setValue(issue.getCustomFieldValue(field.getName()).getFirst());
        } else if (customFieldAttribute.getMetaData().getType().equals(TaskAttribute.TYPE_DATE)) {
          customFieldAttribute.setValue(issue.getCustomFieldValue(field.getName()).getFirst());
        } else if (YouTrackCustomFieldType.PERIOD.equals(YouTrackCustomFieldType.getTypeByName(field.getType()))) {
          customFieldAttribute.getMetaData().setType(TYPE_PERIOD);
          customFieldAttribute.setValue(issue.getCustomFieldValue(field.getName()).getFirst());
        } else if (YouTrackCustomFieldType.USER_SINGLE.equals(YouTrackCustomFieldType.getTypeByName(field.getType()))) {
          customFieldAttribute.setValue(issue.getCustomFieldValue(field.getName()).getFirst());
          customFieldAttribute.putOption(issue.getCustomFieldValue(field.getName()).getFirst(),
              issue.getCustomFieldValue(field.getName()).getFirst());
        } else if (YouTrackCustomFieldType.USER_MULTI.equals(YouTrackCustomFieldType.getTypeByName(field.getType()))) {
          customFieldAttribute.setValues(issue.getCustomFieldValue(field.getName()));
          for (String value : issue.getCustomFieldValue(field.getName())) {
            customFieldAttribute.putOption(value, value);
          }
        } else {
          customFieldAttribute.setValues(issue.getCustomFieldValue(field.getName()));
        }
      } else {
        if (project.getCustomFieldsMap().get(field.getName()).isCanBeEmpty()) {
          customFieldAttribute.setValue(project.getCustomFieldsMap().get(field.getName())
              .getEmptyText());
        }
      }
    }

    return taskData;
  }

  public YouTrackIssue buildIssue(TaskRepository repository, TaskData taskData)
      throws CoreException {

    YouTrackIssue issue = new YouTrackIssue();

    if (taskData.getRoot().getAttribute(TaskAttribute.PRODUCT).toString() != null) {
      issue.addSingleField("projectShortName",
          taskData.getRoot().getAttribute(TaskAttribute.PRODUCT).getValue());
    } else {
      throw new CoreException(new org.eclipse.core.runtime.Status(IStatus.ERROR,
          YouTrackCorePlugin.ID_PLUGIN, IStatus.OK, "Wrong or null project name.", null));
    }

    if (taskData.getRoot().getAttribute(TaskAttribute.SUMMARY).toString() != null) {
      issue.addSingleField("summary", taskData.getRoot().getAttribute(TaskAttribute.SUMMARY)
          .getValue());
    } else {
      throw new CoreException(new org.eclipse.core.runtime.Status(IStatus.ERROR,
          YouTrackCorePlugin.ID_PLUGIN, IStatus.OK, "Wrong or null summary.", null));
    }

    if (taskData.getRoot().getAttribute(TaskAttribute.DESCRIPTION).toString() != null) {
      issue.addSingleField("description", taskData.getRoot()
          .getAttribute(TaskAttribute.DESCRIPTION).getValue());
    }

    YouTrackProject project =
        YouTrackRepositoryConnector.getProject(repository,
            taskData.getRoot().getAttribute(TaskAttribute.PRODUCT).getValue());

    if (!project.isCustomFieldsUpdated()) {
      project.updateCustomFields(YouTrackRepositoryConnector.getClient(repository));
    }

    for (TaskAttribute attribute : taskData.getRoot().getAttributes().values()) {
      if (project.isCustomField(attribute.getId()) && attribute.getValue() != null) {

        // TODO: fix this mess

        String emptyText =
            YouTrackRepositoryConnector
                .getProject(repository,
                    taskData.getRoot().getAttribute(TaskAttribute.PRODUCT).getValue())
                .getCustomFieldsMap().get(getNameFromLabel(attribute)).getEmptyText();

        if (attribute.getValue().toString().equals(emptyText) || attribute.getValue().equals("")) {
          issue.addCustomFieldValue(attribute.getId(), emptyText);
        } else {
          Class fieldClass =
              YouTrackCustomFieldType.getTypeByName(
                  YouTrackRepositoryConnector
                      .getProject(repository,
                          taskData.getRoot().getAttribute(TaskAttribute.PRODUCT).getValue())
                      .getCustomFieldsMap().get(getNameFromLabel(attribute)).getType())
                  .getFieldValuesClass();
          try {
            if (attribute.getMetaData().getType().equals(TaskAttribute.TYPE_MULTI_SELECT)) {
              YouTrackCustomField field =
                  YouTrackRepositoryConnector
                      .getProject(repository,
                          taskData.getRoot().getAttribute(TaskAttribute.PRODUCT).getValue())
                      .getCustomFieldsMap().get(getNameFromLabel(attribute));
              if (YouTrackCustomFieldType.getTypeByName(field.getType()).equals(
                  YouTrackCustomFieldType.USER_MULTI)) {
                LinkedList<String> logins = new LinkedList<String>();
                for (String s : attribute.getValues()) {
                  logins.add(YouTrackIssue.getLoginFromMultiuserValue(s));
                }
                issue.addCustomFieldValue(attribute.getId(), logins);
              } else {
                issue.addCustomFieldValue(attribute.getId(),
                    new LinkedList<String>(attribute.getValues()));
              }

            } else if (attribute.getMetaData().getType().equals(TaskAttribute.TYPE_DATE)) {
              issue.addCustomFieldValue(attribute.getId(),
                  getDateFormat().format(new Date(Long.parseLong(attribute.getValue()))));
            } else if (attribute.getMetaData().getType().equals(TaskAttribute.TYPE_SINGLE_SELECT)) {

              YouTrackCustomField field =
                  YouTrackRepositoryConnector
                      .getProject(repository,
                          taskData.getRoot().getAttribute(TaskAttribute.PRODUCT).getValue())
                      .getCustomFieldsMap().get(getNameFromLabel(attribute));

              if (YouTrackCustomFieldType.getTypeByName(field.getType()).equals(
                  YouTrackCustomFieldType.USER_SINGLE)) {
                issue.addCustomFieldValue(attribute.getId(), YouTrackIssue
                    .getLoginFromMultiuserValue(attribute.getOption(attribute.getValue())));
              } else {
                issue.addCustomFieldValue(attribute.getId(),
                    attribute.getOption(attribute.getValue()));
              }
            } else {
              issue.addCustomFieldValue(attribute.getId(),
                  CastCheck.toObject(fieldClass, attribute.getValue()).toString());
            }
          } catch (NumberFormatException e) {
            throw new CoreException(new org.eclipse.core.runtime.Status(IStatus.ERROR,
                YouTrackCorePlugin.ID_PLUGIN, IStatus.OK, NLS.bind(
                    "Wrong type of value in field \"{0}\".", attribute.getMetaData().getLabel()),
                null));
          }
        }
      }
    }

    issue.fillCustomFieldsFromProject(project, YouTrackRepositoryConnector.getClient(repository));
    if (taskData.getRoot().getMappedAttribute(TAG_PREFIX) != null
        && taskData.getRoot().getMappedAttribute(TAG_PREFIX).getValues() != null) {
      LinkedList<IssueTag> tags = new LinkedList<IssueTag>();
      for (String tag : taskData.getRoot().getMappedAttribute(TAG_PREFIX).getValues()) {
        tags.add(new IssueTag(tag));
      }
      if (tags.size() > 0) {
        issue.setTags(tags);
      }
    }

    return issue;
  }

  public static List<String> unzipList(List<String> list) {
    if (list.size() > 0) {
      String s = list.get(0);
      if (s.startsWith("[") && s.endsWith("]")) {
        return new ArrayList<String>(Arrays.asList(s.substring(1, s.length() - 1).split(",")));
      } else {
        return list;
      }
    }
    return new ArrayList<String>();
  }

  @Override
  public void migrateTaskData(TaskRepository taskRepository, TaskData taskData) {

    if ((taskData != null && taskData.isNew()) || isEnableEditMode()) {

      if (taskData.getRoot().getMappedAttribute(TaskAttribute.PRODUCT) != null) {

        YouTrackProject project =
            YouTrackRepositoryConnector.getProject(taskRepository, taskData.getRoot()
                .getMappedAttribute(TaskAttribute.PRODUCT).getValue());

        if (isEnableEditMode() && !project.isCustomFieldsUpdated()) {
          project.updateCustomFields(YouTrackRepositoryConnector.getClient(taskRepository));
        }

        String[] tags = YouTrackRepositoryConnector.getClient(taskRepository).getUserTags();

        for (TaskAttribute attr : taskData.getRoot().getAttributes().values()) {
          if (TaskAttribute.DESCRIPTION.equals(attr.getId())) {
            attr.getMetaData().setReadOnly(false);
          } else if (TaskAttribute.SUMMARY.equals(attr.getId())) {
            attr.getMetaData().setReadOnly(false);
          } else if (TAG_PREFIX.equals(attr.getId())) {
            attr.getMetaData().setReadOnly(false);
            if (tags != null) {
              for (String tag : tags) {
                if (!tag.equals("tag")) {
                  attr.putOption(tag, tag);
                }
              }
              for (int i = 0; i < attr.getValues().size(); i++) {
                String value = attr.getValues().get(0);
                attr.removeValue(value);
                attr.addValue(value.replace("\n", ""));
              }
            }
          } else if (project.isCustomField(attr.getId())) {
            attr.getMetaData().setReadOnly(false);
            String customFieldName = getNameFromLabel(attr);
            if (project.getCustomFieldsMap().get(customFieldName) == null) {
              project.updateCustomFields(YouTrackRepositoryConnector.getClient(taskRepository));
            }
            YouTrackCustomField customField = project.getCustomFieldsMap().get(customFieldName);
            if (!YouTrackCustomFieldType.getTypeByName(customField.getType()).isSimple()) {
              LinkedList<String> values = customField.getBundle().getValues();
              if (customField.isCanBeEmpty()
                  && !attr.getOptions().containsKey(customField.getEmptyText())) {
                attr.putOption(customField.getEmptyText(), customField.getEmptyText());
              }
              if (values != null) {

                if (!YouTrackCustomFieldType.getTypeByName(customField.getType()).equals(
                    YouTrackCustomFieldType.USER_SINGLE)
                    && !YouTrackCustomFieldType.getTypeByName(customField.getType()).equals(
                        YouTrackCustomFieldType.USER_MULTI)) {
                  for (String value : values) {
                    attr.putOption(value, value);
                  }
                } else {
                  LinkedList<UserValue> users =
                      ((UserBundleValues) customField.getBundle().getBundleValues()).getFullUsers();
                  if (users != null) {
                    for (UserValue user : users) {
                      String option = user.getFullName() + " (" + user.getValue() + ")";
                      if (YouTrackCustomFieldType.getTypeByName(customField.getType()).equals(
                          YouTrackCustomFieldType.USER_SINGLE)) {
                        attr.putOption(option, option);
                      } else if (YouTrackCustomFieldType.getTypeByName(customField.getType())
                          .equals(YouTrackCustomFieldType.USER_MULTI)) {
                        attr.putOption(option, option);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    } else if (!isEnableEditMode()) {
      if (taskData.getRoot().getMappedAttribute(TaskAttribute.PRODUCT) != null) {

        YouTrackProject project =
            YouTrackRepositoryConnector.getProject(taskRepository, taskData.getRoot()
                .getMappedAttribute(TaskAttribute.PRODUCT).getValue());

        for (TaskAttribute attr : taskData.getRoot().getAttributes().values()) {
          if (TaskAttribute.DESCRIPTION.equals(attr.getId())) {
            attr.getMetaData().setReadOnly(true);
          } else if (TaskAttribute.SUMMARY.equals(attr.getId())) {
            attr.getMetaData().setReadOnly(true);
          } else if (TAG_PREFIX.equals(attr.getId())) {
            attr.getMetaData().setReadOnly(true);
          } else if (project.isCustomField(attr.getId())) {
            attr.getMetaData().setReadOnly(true);
          }
        }
      }
    }
  }

  private String getNewComment(TaskData taskData) {
    String newComment = "";
    TaskAttribute attribute = taskData.getRoot().getMappedAttribute(COMMENT_NEW);
    if (attribute != null) {
      newComment = taskData.getAttributeMapper().getValue(attribute);
    }
    return newComment;
  }

  public static boolean isEnableEditMode() {
    return enableEditMode;
  }

  public static void setEnableEditMode(boolean enableEditMode) {
    YouTrackTaskDataHandler.enableEditMode = enableEditMode;
  }

  private String attributeNameFromLinkRole(String role) {
    return LINK_PREFIX + role.replace(" ", "_").toUpperCase();
  }

  private String capitalize(String line) {
    return Character.toUpperCase(line.charAt(0)) + line.substring(1);
  }

  public static boolean isUploadNewCommentMode() {
    return postNewCommentMode;
  }

  public static void setPostNewCommentMode(boolean postNewCommentMode) {
    YouTrackTaskDataHandler.postNewCommentMode = postNewCommentMode;
  }

  public static URL getIssueURL(TaskData data, TaskRepository repository) {
    try {
      return new URL(data.getRepositoryUrl() + YouTrackRepositoryConnector.ISSUE_URL_PREFIX
          + YouTrackRepositoryConnector.getRealIssueId(data.getTaskId(), repository));
    } catch (MalformedURLException e) {
      return null;
    }
  }

  @Override
  public void getMultiTaskData(final TaskRepository repository, Set<String> taskIds,
      final TaskDataCollector collector, IProgressMonitor monitor) throws CoreException {
    try {
      monitor.beginTask("Receiving_tasks", taskIds.size());
      final YouTrackClient client = YouTrackRepositoryConnector.getClient(repository);

      for (String id : taskIds) {
        collector.accept(parseIssue(repository,
            client.getIssue(YouTrackRepositoryConnector.getRealIssueId(id, repository)), monitor));
      }
    } finally {
      monitor.done();
    }
  }

  private SimpleDateFormat getDateFormat() {
    return YOUTRACK_DATE_FORMAT;
  }
}
