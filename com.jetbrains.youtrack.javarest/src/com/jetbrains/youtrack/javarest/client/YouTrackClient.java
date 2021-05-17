package com.jetbrains.youtrack.javarest.client;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

import com.jetbrains.youtrack.javarest.client.YouTrackCustomField.YouTrackCustomFieldType;
import com.jetbrains.youtrack.javarest.utils.BuildBundleValues;
import com.jetbrains.youtrack.javarest.utils.EnumerationBundleValues;
import com.jetbrains.youtrack.javarest.utils.GroupUsersList;
import com.jetbrains.youtrack.javarest.utils.IntellisenseItem;
import com.jetbrains.youtrack.javarest.utils.IntellisenseValues;
import com.jetbrains.youtrack.javarest.utils.OwnedFieldBundleValues;
import com.jetbrains.youtrack.javarest.utils.SavedSearch;
import com.jetbrains.youtrack.javarest.utils.SavedSearches;
import com.jetbrains.youtrack.javarest.utils.StateBundleValues;
import com.jetbrains.youtrack.javarest.utils.StateValue;
import com.jetbrains.youtrack.javarest.utils.UserBundleValues;
import com.jetbrains.youtrack.javarest.utils.UserGroupValue;
import com.jetbrains.youtrack.javarest.utils.UserSavedSearch;
import com.jetbrains.youtrack.javarest.utils.UserSavedSearches;
import com.jetbrains.youtrack.javarest.utils.UserValue;
import com.jetbrains.youtrack.javarest.utils.VersionBundleValues;
import com.jetbrains.youtrack.javarest.utils.YouTrackTimeSettings;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.representation.Form;

/**
 * @author Alexander Marchuk
 * 
 */
public class YouTrackClient {

  private String username = null;

  private String password = null;

  private WebResource service;

  public YouTrackClient(WebResource resource) {
    this.service = resource;
  }

  public static ClientResponse checkClientResponse(ClientResponse response, int code, String message) {
    if (response.getStatus() != code) {
      String responseBody = response.getEntity(String.class);
      if (responseBody != null)
        throw new RuntimeException(message + "\nRESPONSE CODE: " + response.getStatus() + " "
            + response.getClientResponseStatus() + "\nRESPONSE DATA:" + responseBody);
    }
    return response;
  }

  public boolean login(final String username, final String password) {
    if (username == null || password == null || "".equals(username) || "".equals(password)) {
      throw new RuntimeException("Failed : NULL username or password ");
    } else {
      checkClientResponse(
          service.path("/user/login").queryParam("login", username)
              .queryParam("password", password).post(ClientResponse.class), 200, "Failed to login");
    }

    this.setPassword(password);
    this.setUsername(username);
    return true;
  }

  public boolean loginWithCredentials() {
    return login(getUsername(), getPassword());
  }

  public boolean issueExist(String issueId) {
    return service.path("/issue/").path(issueId).path("/exists").get(ClientResponse.class)
        .getStatus() == 200;
  }

  public YouTrackIssue getIssue(String id) {
    return getIssue(id, false);
  }

  public YouTrackIssue getIssue(String id, boolean wikifyDescription) {
    if (id == null) {
      throw new RuntimeException("Null issue id");
    } else {
      YouTrackIssue issue =
          service.path("/issue/").path(id)
              .queryParam("wikifyDescription", String.valueOf(wikifyDescription))
              .accept("application/xml").get(YouTrackIssue.class);
      issue.mapFields();
      return issue;
    }
  }

  public String getIssueWikifyDescription(String id) {
    YouTrackIssue wikifyIssue = getIssue(id, true);
    wikifyIssue.mapFields();
    for (IssueSchemaField field : wikifyIssue.getFields()) {
      if (field.getName().equals("description")) {
        return field.getValues().getFirst().getValue();
      }
    }
    return "";
  }

  public List<YouTrackIssue> getIssuesInProject(String projectname, String filter, int after,
      int max, long updatedAfter) {
    try {
      return service.path("/issue/byproject/").path(projectname).queryParam("filter", filter)
          .queryParam("after", Integer.toString(after)).queryParam("max", Integer.toString(max))
          .queryParam("updatedAfter", Long.toString(updatedAfter)).accept("application/xml")
          .get(YouTrackIssuesList.class).getIssues();
    } catch (RuntimeException e) {
      throw new RuntimeException("Exception while get list of issues in project :\n"
          + e.getMessage(), e);
    }
  }

  public List<YouTrackIssue> getIssuesInProject(String projectname, int max) {
    return getIssuesInProject(projectname, "", 0, max, 0);
  }

  /*
   * returns 10 or less issues
   */
  public List<YouTrackIssue> getIssuesInProject(String projectname) {
    return getIssuesInProject(projectname, "", 0, 10, 0);
  }

  public String getPassword() {
    if (password == null) {
      throw new RuntimeException("Attempt to get null password.");
    } else {
      return password;
    }
  }

  public void setPassword(String password) {
    this.password = password;
    if (username != null) {
      this.service.addFilter(new HTTPBasicAuthFilter(username, password));
    }
  }

  public String getUsername() {
    if (username == null) {
      throw new RuntimeException("Attemp to get null username");
    } else {
      return username;
    }
  }

  public void setUsername(String username) {
    this.username = username;
    if (password != null) {
      this.service.addFilter(new HTTPBasicAuthFilter(username, password));
    }
  }

  public List<YouTrackProject> getProjects() {
    try {
      return service.path("/project/all").accept("application/xml").get(YouTrackProjectsList.class)
          .getProjects();
    } catch (Exception e) {
      throw new RuntimeException("Exception while get list of projects\n" + e.getMessage());
    }
  }

  public YouTrackProject getProject(String projectId) {
    try {
      YouTrackProject project =
          service.path("/admin/project/").path(projectId).accept("application/xml")
              .get(YouTrackProject.class);
      project.setProjectShortName(projectId);
      return project;
    } catch (Exception e) {
      throw new RuntimeException("Exception while get project by id\n" + e.getMessage());
    }
  }

  /**
   * @param issue
   * @return new issue id from tracker, if successfully uploaded
   */
  public String putNewIssue(final YouTrackIssue issue) {
    if (issue != null && issue.getProjectName() != null && issue.getSummary() != null) {
      WebResource resource =
          service.path("/issue").queryParam("project", issue.getProjectName())
              .queryParam("summary", issue.getSummary());
      if (issue.getDescription() != null) {
        resource = resource.queryParam("description", issue.getDescription());
      }
      ClientResponse response =
          checkClientResponse(resource.put(ClientResponse.class, ""), 201, "Failed put new issue");
      return YouTrackIssue.getIdFromResponse(response);
    } else {
      throw new RuntimeException("Issue's project and summary can't be null.");
    }
  }

  public void deleteIssue(final String issueId) {
    if (issueId != null) {
      WebResource resource = service.path("/issue/").path(issueId);
      checkClientResponse(resource.delete(ClientResponse.class), 200, "Failed delete issue "
          + issueId);
    } else {
      throw new RuntimeException("Null issue id");
    }
  }

  public void applyCommand(final String issueId, final String command) {
    if (issueId != null && command != null) {
      WebResource resource = service.path("/issue/").path(issueId).path("/execute");
      Form form = new Form();
      form.add("command", command);
      checkClientResponse(
          resource.type(MediaType.APPLICATION_FORM_URLENCODED).post(ClientResponse.class, form),
          200, "Failed apply command " + command + " to issue " + issueId);
    } else {
      throw new RuntimeException("Null issue id or command while apply command.");
    }
  }

  /**
   * @param filterQuery
   * @return number of relevant issues or all issues, if filter string is null return -1 if reach
   *         max number of attempts
   */
  public int getNumberOfIssues(String filterQuery) {
    WebResource resource = service.path("/issue/count");
    if (filterQuery != null) {
      resource = resource.queryParam("filter", filterQuery);
    } else {
      resource = resource.queryParam("filter", "");
    }

    int number;
    int attemptCount = 0;
    while ((number =
        resource.accept("application/xml").get(XmlNumberOfIssuesParser.class).getNumber()) == -1
        && attemptCount++ < 10) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        break;
      }
    }
    return number;
  }

  @XmlRootElement(name = "int")
  private static class XmlNumberOfIssuesParser {

    /*
     * TODO: fix, too many strings of code for a simple action: get number from <int>1</int>
     */

    @XmlValue
    private int number;

    private int getNumber() {
      return this.number;
    }

  }

  public List<YouTrackIssue> getIssuesByFilter(String filterQuery, int max) {
    WebResource resource;
    if (filterQuery != null) {
      resource = service.path("/issue").queryParam("filter", filterQuery);
    } else {
      resource = service.path("/issue").queryParam("filter", "");
    }

    if (max != -1) {
      resource = resource.queryParam("max", Integer.toString(max));
    }

    return resource.accept("application/xml").get(IssueCompactsList.class).getIssues();
  }

  public List<YouTrackIssue> getIssuesByFilter(String filterQuery) {
    return getIssuesByFilter(filterQuery, -1);
  }

  public LinkedList<YouTrackCustomField> getProjectCustomFields(String projectname) {
    if (projectname != null) {
      try {
        return service.path("/admin/project/").path(projectname).path("/customfield")
            .accept("application/xml").get(YouTrackCustomFieldsList.class).getCustomFields();
      } catch (Exception e) {
        throw new RuntimeException("Exception while get project custom fields:\n" + e.getMessage());
      }
    } else {
      throw new RuntimeException("Null projectname while get project custom fields.");
    }
  }

  public YouTrackCustomField getProjectCustomField(String projectname, String fieldname) {
    if (projectname != null && fieldname != null) {
      try {
        return service.path("/admin/project/").path(projectname).path("/customfield/")
            .path(fieldname).accept("application/xml").get(YouTrackCustomField.class);
      } catch (Exception e) {
        throw new RuntimeException("Exception while get project custom field:\n" + e.getMessage());
      }
    } else {
      throw new RuntimeException("Null projectname or fieldname while get project custom field.");
    }
  }

  public Set<String> getProjectCustomFieldNames(String projectname) {
    LinkedList<YouTrackCustomField> cfs = getProjectCustomFields(projectname);
    Set<String> cfNames = new HashSet<String>();

    for (YouTrackCustomField cf : cfs) {
      cfNames.add(cf.getName());
    }
    return cfNames;
  }

  public EnumerationBundleValues getEnumerationBundleValues(String bundlename) {
    return service.path("/admin/customfield/bundle/").path(bundlename).accept("application/xml")
        .get(EnumerationBundleValues.class);
  }

  public OwnedFieldBundleValues getOwnedFieldBundleValues(String bundlename) {
    return service.path("/admin/customfield/ownedFieldBundle/").path(bundlename)
        .accept("application/xml").get(OwnedFieldBundleValues.class);
  }

  public BuildBundleValues getBuildBundleValues(String bundlename) {
    return service.path("/admin/customfield/buildBundle/").path(bundlename)
        .accept("application/xml").get(BuildBundleValues.class);
  }

  public StateBundleValues getStateBundleValues(String bundlename) {
    return service.path("/admin/customfield/stateBundle/").path(bundlename)
        .accept("application/xml").get(StateBundleValues.class);
  }

  public boolean isStateResolved(String bundlename, String state) {
    return service.path("/admin/customfield/stateBundle/").path(bundlename).path(state)
        .accept("application/xml").get(StateValue.class).isResolved();
  }

  public VersionBundleValues getVersionBundleValues(String bundlename) {
    return service.path("/admin/customfield/versionBundle/").path(bundlename)
        .accept("application/xml").get(VersionBundleValues.class);
  }

  public UserBundleValues getUserBundleValues(String bundlename) {
    return service.path("/admin/customfield/userBundle/").path(bundlename)
        .accept("application/xml").get(UserBundleValues.class);
  }

  /**
   * Add all unique users from userGroups and add them to bundle values
   */
  public UserBundleValues getAllUserBundleValues(String bundlename) {
    UserBundleValues userBundleValues = getUserBundleValues(bundlename);
    if (userBundleValues.getUserGroupValues() != null) {
      for (UserGroupValue groupValue : userBundleValues.getUserGroupValues()) {
        try {
          int start = 0;
          LinkedList<UserValue> users =
              getUsersListInGroup(groupValue.getValue(), start).getUsers();
          while (users != null) {
            userBundleValues.addUsersFromGroup(users);
            start += 10;
            users = getUsersListInGroup(groupValue.getValue(), start).getUsers();
          }
        } catch (UniformInterfaceException e) {
          // You do not have permissions to read user,
          // supress for possibility create issue.
        }
      }
    }
    if (userBundleValues.getAllUsers() != null) {
      for (UserValue user : userBundleValues.getAllUsers()) {
        userBundleValues.addFullUser(getUser(user.getValue()));
      }
    }
    return userBundleValues;
  }

  public UserValue getUser(String login) {
    return service.path("/admin/user/").path(login).accept("application/xml").get(UserValue.class);
  }

  public GroupUsersList getUsersListInGroup(String groupname, int start) {
    return service.path("/admin/user").queryParam("group", groupname)
        .queryParam("start", String.valueOf(start)).accept("application/xml")
        .get(GroupUsersList.class);
  }

  public void addComment(final String issueId, final String comment) {
    if (issueId != null && comment != null) {
      Form form = new Form();
      form.add("comment", comment);
      checkClientResponse(
          service.path("/issue/").path(issueId).path("/execute")
              .type(MediaType.APPLICATION_FORM_URLENCODED).post(ClientResponse.class, form), 200,
          "Failed to login");
    } else {
      throw new RuntimeException("Null issue id or comment body.");
    }
  }

  public String[] intellisenseFullOptions(String filter) {
    return intellisenseFullOptions(filter, filter.length());
  }

  public String[] intellisenseOptions(String filter) {
    return intellisenseOptions(filter, filter.length());
  }

  public LinkedList<IntellisenseItem> intellisenseItems(String filter) {
    return intellisenseItems(filter, filter.length());
  }

  public String[] intellisenseFullOptions(String filter, int caret) {
    return service.path("/issue/intellisense").queryParam("filter", filter)
        .queryParam("caret", String.valueOf(caret)).accept("application/xml")
        .get(IntellisenseValues.class).getFullOptions();
  }

  public String[] intellisenseOptions(String filter, int caret) {
    return service.path("/issue/intellisense").queryParam("filter", filter)
        .queryParam("caret", String.valueOf(caret)).accept("application/xml")
        .get(IntellisenseValues.class).getOptions();
  }

  public LinkedList<IntellisenseItem> intellisenseItems(String filter, int caret) {
    return service.path("/issue/intellisense").queryParam("filter", filter)
        .queryParam("caret", String.valueOf(caret)).accept("application/xml")
        .get(IntellisenseValues.class).getIntellisenseItems();
  }

  public IntellisenseValues intellisenseSearchValues(String filter, int caret) {
    return service.path("/issue/intellisense").queryParam("filter", filter)
        .queryParam("caret", String.valueOf(caret)).accept("application/xml")
        .get(IntellisenseValues.class).getIntellisenseValues();
  }

  public IntellisenseValues intellisenseSearchValues(String filter) {
    return intellisenseSearchValues(filter, filter.length());
  }

  public IntellisenseValues intellisenseCommandValues(String command, int caret, String issueId) {
    return service.path("/issue/").path(issueId).path("/execute/intellisense")
        .queryParam("command", command).queryParam("caret", String.valueOf(caret))
        .accept("application/xml").get(IntellisenseValues.class).getIntellisenseValues();
  }

  public String[] intellisenseCommandFullOptions(String filter, String issueId) {
    return intellisenseCommandFullOptions(filter, filter.length(), issueId);
  }

  public String[] intellisenseCommandOptions(String filter, String issueId) {
    return intellisenseCommandOptions(filter, filter.length(), issueId);
  }

  public LinkedList<IntellisenseItem> intellisenseCommandItems(String filter, String issueId) {
    return intellisenseCommandItems(filter, filter.length(), issueId);
  }

  public String[] intellisenseCommandFullOptions(String command, int caret, String issueId) {
    return service.path("/issue/").path(issueId).path("/execute/intellisense")
        .queryParam("command", command).queryParam("caret", String.valueOf(caret))
        .accept("application/xml").get(IntellisenseValues.class).getFullOptions();
  }

  public String[] intellisenseCommandOptions(String command, int caret, String issueId) {
    return service.path("/issue/").path(issueId).path("/execute/intellisense")
        .queryParam("command", command).queryParam("caret", String.valueOf(caret))
        .accept("application/xml").get(IntellisenseValues.class).getOptions();
  }

  public LinkedList<IntellisenseItem> intellisenseCommandItems(String command, int caret,
      String issueId) {
    return service.path("/issue/").path(issueId).path("/execute/intellisense")
        .queryParam("command", command).queryParam("caret", String.valueOf(caret))
        .accept("application/xml").get(IntellisenseValues.class).getIntellisenseItems();
  }

  public IntellisenseValues intellisenseCommandValues(String filter, String issueId) {
    return intellisenseCommandValues(filter, filter.length(), issueId);
  }

  public LinkedList<String> getSavedSearchesNames() {
    return service.path("/user/search").accept("application/xml").get(SavedSearches.class)
        .getSearchNames();
  }

  public LinkedList<SavedSearch> getSavedSearches() {
    return service.path("/user/search").accept("application/xml").get(SavedSearches.class)
        .getSearches();
  }

  public SavedSearch getSavedSearch(String searchname) {
    if (searchname != null) {
      return service.path("/user/search/").path(searchname).accept("application/xml")
          .get(SavedSearch.class);
    } else {
      throw new RuntimeException("Null saved search name.");
    }
  }

  public LinkedList<UserSavedSearch> getSavedSearchesForUser(String username) {
    if (username != null) {
      return service.path("/user/").path(username).path("/filter").accept("application/xml")
          .get(UserSavedSearches.class).getUserSearches();
    } else {
      throw new RuntimeException("Can't get saved searches for null username.");
    }
  }

  public LinkedList<String> getSavedSearchesNamesForUser(String username) {
    if (username != null) {
      return service.path("/user/").path(username).path("/filter").accept("application/xml")
          .get(UserSavedSearches.class).getUserSearchesNames();
    } else {
      throw new RuntimeException("Can't get saved searches for null username.");
    }
  }

  public void addNewTag(String issueId, String tagName) {
    if (tagName != null && tagName.length() > 0) {
      applyCommand(issueId, "tag " + tagName);
    }
  }

  public void removeTag(String issueId, String tagName) {
    if (tagName != null && tagName.length() > 0) {
      applyCommand(issueId, "remove tag " + tagName);
    }
  }

  public String[] getUserTags() {
    return service.path("/user/tag").accept("application/xml").get(UserTagList.class).getOptions();
  }

  public String[] getAllLinkTypeCommands() {
    return service.path("/admin/issueLinkType").accept("application/xml")
        .get(IssueLinkTypesList.class).getAllLinkTypeCommands();
  }

  /**
   * summary can't be empty by rest restriction
   * 
   * @param issueId
   * @param newSummary
   * @param newDescription new description if null, not changed
   */
  public void updateIssueSummaryAndDescription(final String issueId, final String newSummary,
      final String newDescription) {
    Form form = new Form();
    WebResource resource = service.path("/issue/").path(issueId);
    if (newSummary != null && newSummary.length() > 0) {
      form.add("summary", newSummary);
      if (newDescription != null) {
        form.add("description", newDescription);
      }
    } else {
      throw new RuntimeException("Failed to update issue: summary cant be empty");
    }

    checkClientResponse(
        resource.type(MediaType.APPLICATION_FORM_URLENCODED).post(ClientResponse.class, form), 200,
        "Failed to update issue description and summary ");
  }

  public boolean needUpdateSummary(YouTrackIssue oldIssue, YouTrackIssue newIssue) {
    return newIssue.getSummary() != null && newIssue.getSummary().length() > 0
        && !oldIssue.getSummary().equals(newIssue.getSummary());
  }

  public boolean needUpdateDescription(YouTrackIssue oldIssue, YouTrackIssue newIssue) {
    return newIssue.getDescription() != null
        && (oldIssue.getDescription() == null || !oldIssue.getDescription().equals(
            newIssue.getDescription()));
  }

  public boolean needUpdateCustomField(YouTrackIssue oldIssue, YouTrackIssue newIssue,
      String customFieldName) {
    YouTrackCustomField customFieldInfo = newIssue.getCustomFieldInfo(customFieldName);
    if (customFieldInfo.isSingle()) {
      return newIssue.getSingleCustomFieldValue(customFieldName) != null
          && (oldIssue.getSingleCustomFieldValue(customFieldName) == null || !oldIssue
              .getSingleCustomFieldValue(customFieldName).equals(
                  newIssue.getSingleCustomFieldValue(customFieldName)));
    } else {

      boolean equalSize;

      LinkedList<String> newValues = new LinkedList<String>();
      if (!newIssue.getCustomFieldsValues().containsKey(customFieldName)
          || newIssue.getCustomFieldValue(customFieldName) == null) {
        return false;
      } else {
        newValues = newIssue.getCustomFieldValue(customFieldName);
      }

      LinkedList<String> oldValues = new LinkedList<String>();
      if (!oldIssue.getCustomFieldsValues().containsKey(customFieldName)
          || oldIssue.getCustomFieldValue(customFieldName) == null) {
        return true;
      } else {
        oldValues = oldIssue.getCustomFieldValue(customFieldName);
      }

      equalSize = newValues.size() == oldValues.size();
      oldValues.removeAll(newValues);

      return !equalSize || oldValues.size() > 0;
    }
  }

  /**
   * If issue not update fully, make incomplete update
   */
  public void updateIssue(String oldIssueId, YouTrackIssue newIssue) {

    if (oldIssueId != null) {

      YouTrackIssue oldIssue = this.getIssue(oldIssueId);

      if (needUpdateSummary(oldIssue, newIssue) || needUpdateDescription(oldIssue, newIssue)) {
        updateIssueSummaryAndDescription(oldIssueId, newIssue.getSummary(),
            newIssue.getDescription());
      }

      StringBuilder addCFCommand = new StringBuilder();

      for (String customFieldName : newIssue.getCustomFieldsValues().keySet()) {

        if (!newIssue.isCustomFieldsDataConsistent(customFieldName)) {
          return;
        }
        YouTrackCustomField customFieldInfo = newIssue.getCustomFieldInfo(customFieldName);

        if (YouTrackCustomFieldType.getTypeByName(customFieldInfo.getType()).equals(
            YouTrackCustomFieldType.USER_MULTI)) {
          LinkedList<String> values = oldIssue.getCustomFieldValue(customFieldName);
          LinkedList<String> newValues = new LinkedList<String>();
          if (values != null) {
            for (String value : values) {
              newValues.add(YouTrackIssue.getLoginFromMultiuserValue(value));
            }
            oldIssue.addCustomFieldValue(customFieldName, newValues);
          }
        }

        if (needUpdateCustomField(oldIssue, newIssue, customFieldName)) {
          if (customFieldInfo.isSingle()) {
            if (customFieldInfo.getType().equals(YouTrackCustomFieldType.STRING.getName())) {
              this.applyCommand(oldIssueId,
                  customFieldName + ": " + newIssue.getSingleCustomFieldValue(customFieldName));
            } else {

              if (YouTrackCustomFieldType.getTypeByName(customFieldInfo.getType()).equals(
                  YouTrackCustomFieldType.USER_SINGLE)) {
                addCFCommand.append(customFieldName + ": "
                    + newIssue.getSingleCustomFieldValue(customFieldName) + " ");
              } else {
                addCFCommand.append(customFieldName + ": "
                    + newIssue.getSingleCustomFieldValue(customFieldName) + " ");
              }
            }
          } else {

            LinkedList<String> selectedValues = new LinkedList<String>();
            if (newIssue.getCustomFieldValue(customFieldName) != null) {
              selectedValues = newIssue.getCustomFieldValue(customFieldName);
            }

            LinkedList<String> oldValues = new LinkedList<String>();
            if (oldIssue.getCustomFieldValue(customFieldName) != null) {
              oldValues = oldIssue.getCustomFieldValue(customFieldName);
            }

            LinkedList<String> newValues = new LinkedList<String>(selectedValues);
            newValues.removeAll(oldValues);
            LinkedList<String> removeValues = new LinkedList<String>(oldValues);
            removeValues.removeAll(selectedValues);

            if (removeValues.size() > 0) {
              StringBuilder removeCommand = new StringBuilder();
              removeCommand.append("Remove " + customFieldName + " ");
              for (String value : removeValues) {
                removeCommand.append(value + " ");
              }
              applyCommand(oldIssueId, removeCommand.toString());
            }

            if (newValues.size() > 0) {
              addCFCommand.append("add " + customFieldName + " ");
              for (String value : newValues) {
                addCFCommand.append(value + " ");
              }
            }
          }
        }
      }
      if (addCFCommand.toString() != null) {
        this.applyCommand(oldIssueId, addCFCommand.toString());
      }

      LinkedList<String> selectedTags = new LinkedList<String>();
      if (newIssue.getTags() != null && newIssue.getTags().size() > 0) {
        selectedTags = newIssue.getStringTags();
      }
      LinkedList<String> oldTags = new LinkedList<String>();
      if (oldIssue.getTags() != null) {
        oldTags = oldIssue.getStringTags();
      }
      LinkedList<String> newTags = new LinkedList<String>(selectedTags);
      newTags.remove(oldTags);
      LinkedList<String> removeTags = new LinkedList<String>(oldTags);
      removeTags.removeAll(selectedTags);

      StringBuilder modifyTagsCommand = new StringBuilder();
      for (String newTag : newTags) {
        this.applyCommand(oldIssueId, " add tag " + newTag.replace("\n", ""));
      }

      for (String tagToRemove : removeTags) {
        modifyTagsCommand.append(" remove tag " + tagToRemove.replace("\n", ""));
      }

      if (modifyTagsCommand.toString() != null) {
        this.applyCommand(oldIssueId, modifyTagsCommand.toString());
      }

    } else {
      throw new RuntimeException("Null target issue id while update issue.");
    }
  }

  public YouTrackTimeSettings getTimeTrackingSettings() {
    return service.path("/admin/timetracking").accept("application/xml")
        .get(YouTrackTimeSettings.class);
  }
}
