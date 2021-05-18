/**
 * @author: amarch
 */

package com.jetbrains.mylyn.yt.ui;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.mylyn.internal.tasks.ui.editors.EditorUtil;
import org.eclipse.mylyn.internal.tasks.ui.editors.RichTextAttributeEditor;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorExtensions;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorSummaryPart;
import org.eclipse.mylyn.internal.tasks.ui.editors.UserAttributeEditor;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.ui.editors.AbstractAttributeEditor;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.forms.widgets.FormToolkit;

import com.jetbrains.mylyn.yt.core.YouTrackRepositoryConnector;
import com.jetbrains.mylyn.yt.core.YouTrackTaskDataHandler;


public class YouTrackSummaryPart extends TaskEditorSummaryPart {

  private AbstractAttributeEditor summaryEditor;

  private String partId;

  private static final String ADD_TAG_TEXT = "Add tag";

  private static final String ADD_NEW_TAG_PROPOSAL = "Add tag with command...";

  private static final String ADD_LINK_TEXT = "Add link";

  private static final String ADD_SUMMARY_TEXT = "write issue summary here";

  private static String[] linkTypeSentences;

  private static final String[] defaultLinkTypeSentences = {"relates to", "is required for",
      "depends on", "is duplicated by", "duplicates", "parent for", "subtask of"};

  private CCombo addTagCombo;

  private CCombo addLinkCombo;

  private Composite secondLineComposite;

  private void addAttribute(Composite composite, FormToolkit toolkit, TaskAttribute attribute,
      boolean shouldInitializeGridData) {
    AbstractAttributeEditor editor = createAttributeEditor(attribute);
    if (editor != null) {
      editor.setReadOnly(true);
      editor.setDecorationEnabled(false);

      editor.createLabelControl(composite, toolkit);
      if (shouldInitializeGridData) {
        GridDataFactory.defaultsFor(editor.getLabelControl())
            .indent(EditorUtil.HEADER_COLUMN_MARGIN, 0).applyTo(editor.getLabelControl());
      }

      editor.createControl(composite, toolkit);
      getTaskEditorPage().getAttributeEditorToolkit().adapt(editor);
    }
  }

  @Override
  protected Composite createHeaderLayout(Composite parent, FormToolkit toolkit) {
    Composite composite = toolkit.createComposite(parent);
    GridLayout layout = new GridLayout(1, false);
    layout.verticalSpacing = 1;
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    composite.setLayout(layout);
    GridDataFactory.fillDefaults().grab(true, false).applyTo(composite);

    // Reporter name
    TaskAttribute attribute =
        getTaskData().getRoot().getMappedAttribute(TaskAttribute.USER_REPORTER);
    if (attribute != null) {
      addAttribute(composite, toolkit, attribute, true);
    }

    // Date of submission
    attribute = getTaskData().getRoot().getMappedAttribute(TaskAttribute.DATE_CREATION);
    if (attribute != null) {
      addAttribute(composite, toolkit, attribute, true);
    }


    // Composite composite2 = new Composite(composite, SWT.NONE);
    // // final RowLayout rowLayout1 = new RowLayout();
    // // rowLayout1.center = true;
    // // rowLayout1.marginLeft = 0;
    // // composite2.setLayout(rowLayout1);
    // // rowLayout1.spacing = 8;
    // // GridDataFactory.fillDefaults().grab(true, false).applyTo(composite2);
    // composite2.setLayout(layout);
    // GridDataFactory.fillDefaults().grab(true, false).applyTo(composite2);

    // Updater name
    attribute = getTaskData().getRoot().getMappedAttribute(YouTrackTaskDataHandler.USER_UPDATER);
    if (attribute != null) {
      addAttribute(composite, toolkit, attribute, true);
    }

    // Date of update
    attribute = getTaskData().getRoot().getMappedAttribute(TaskAttribute.DATE_MODIFICATION);
    if (attribute != null) {
      addAttribute(composite, toolkit, attribute, true);
    }



    if (layout instanceof GridLayout) {
      GridLayout gl = (GridLayout) layout;
      gl.numColumns = composite.getChildren().length;
      if (gl.numColumns == 0) {
        gl.numColumns = 4;
      }
    }

    if (layout instanceof GridLayout) {
      GridLayout gLayout = (GridLayout) layout;

      secondLineComposite = new Composite(composite, SWT.NONE);
      final RowLayout rowLayout = new RowLayout();
      rowLayout.center = true;
      rowLayout.marginLeft = 0;
      secondLineComposite.setLayout(rowLayout);
      rowLayout.spacing = 8;
      GridDataFactory.fillDefaults().span(gLayout.numColumns, 1).applyTo(secondLineComposite);
      toolkit.adapt(secondLineComposite);


      putAddLinkCombo(secondLineComposite);
      putAddTagCombo(secondLineComposite);
      putOpenCommandDialogItem(secondLineComposite);
    }

    return composite;
  }

  private void putAddLinkCombo(Composite composite) {

    addLinkCombo = new CCombo(composite, SWT.DOWN | SWT.ARROW | SWT.BORDER);
    addLinkCombo.setEditable(false);
    addLinkCombo.setText(ADD_LINK_TEXT);


    addLinkCombo.addListener(SWT.DROP_DOWN, new Listener() {
      @Override
      public void handleEvent(Event event) {
        CCombo combo = (CCombo) event.widget;
        combo.setItems(YouTrackRepositoryConnector.getClient(
            getTaskEditorPage().getModel().getTaskRepository()).getUserTags());
        TaskRepository repository = getTaskEditorPage().getModel().getTaskRepository();
        try {
          linkTypeSentences =
              YouTrackRepositoryConnector.getClient(repository).getAllLinkTypeCommands();
        } catch (RuntimeException e) {
          linkTypeSentences = defaultLinkTypeSentences;
        }
        addLinkCombo.setItems(linkTypeSentences);
        combo.setText(ADD_LINK_TEXT);
        combo.setVisibleItemCount(combo.getItemCount() - 1);
      }
    });



    addLinkCombo.addSelectionListener(new SelectionListener() {

      @Override
      public void widgetSelected(SelectionEvent e) {
        CCombo combo = (CCombo) e.widget;
        String selectedText = combo.getItem(combo.getSelectionIndex());
        openCommandWizard(secondLineComposite.getShell(), capitalize(selectedText) + ": ", true,
            getTaskEditorPage());
        combo.setText(ADD_LINK_TEXT);
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {}
    });
  }

  private void putAddTagCombo(Composite composite) {

    addTagCombo = new CCombo(composite, SWT.DOWN | SWT.ARROW | SWT.BORDER);
    addTagCombo.setText(ADD_TAG_TEXT);
    addTagCombo.setEditable(false);

    addTagCombo.addListener(SWT.DROP_DOWN, new Listener() {
      @Override
      public void handleEvent(Event event) {
        CCombo combo = (CCombo) event.widget;
        combo.setItems(YouTrackRepositoryConnector.getClient(
            getTaskEditorPage().getModel().getTaskRepository()).getUserTags());
        combo.add(ADD_NEW_TAG_PROPOSAL, 0);
        combo.setText(ADD_TAG_TEXT);
        combo.setVisibleItemCount(combo.getItemCount() - 1);
      }
    });

    addTagCombo.addSelectionListener(new SelectionListener() {

      @Override
      public void widgetSelected(SelectionEvent e) {

        CCombo combo = (CCombo) e.widget;
        String selectedText = combo.getItem(combo.getSelectionIndex());

        if (selectedText.equals(ADD_NEW_TAG_PROPOSAL)) {
          openCommandWizard(secondLineComposite.getShell(), ADD_TAG_TEXT.toLowerCase() + ": ",
              true, getTaskEditorPage());
        } else {
          TaskRepository repository = getTaskEditorPage().getModel().getTaskRepository();
          YouTrackRepositoryConnector.getClient(repository).addNewTag(
              YouTrackRepositoryConnector.getYoutrackIssueId(getTaskData().getTaskId()),
              selectedText);

          TaskAttribute attribute =
              getTaskData().getRoot().getMappedAttribute(YouTrackTaskDataHandler.TAG_PREFIX);
          attribute.putOption(selectedText, selectedText);
          attribute.addValue(selectedText);
          YouTrackTaskEditorPageFactory.synchronizeTaskUi(getTaskEditorPage().getTaskEditor());
        }
        combo.setText(ADD_TAG_TEXT);
        combo.setVisibleItemCount(combo.getItemCount() - 1);
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
      }
    });
  }


  private void putOpenCommandDialogItem(final Composite composite) {

    ToolBar toolBar = new ToolBar(composite, SWT.NONE);
    ToolItem commandDialog = new ToolItem(toolBar, SWT.PUSH | SWT.BORDER);
    commandDialog.setText("Command Dialog");
    commandDialog.addSelectionListener(new SelectionListener() {

      @Override
      public void widgetSelected(SelectionEvent e) {
        openCommandWizard(composite.getShell(), null, true, getTaskEditorPage());
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {}
    });

  }

  public static void openCommandWizard(Shell shell, String initialCommand,
      boolean needUISynchronization, AbstractTaskEditorPage page) {
    YouTrackCommandWindowWizard commandWizard =
        new YouTrackCommandWindowWizard(page.getModel().getTaskData(), page.getModel()
            .getTaskRepository(), page.getTaskEditor());

    if (initialCommand != null) {
      commandWizard.getCommandDialogPage().setCommandBoxText(initialCommand);
    }

    YouTrackCommandWindowDialog dialog = new YouTrackCommandWindowDialog(shell, commandWizard);
    if (dialog.open() == Window.OK && needUISynchronization) {
      YouTrackTaskEditorPageFactory.synchronizeTaskUi(page.getTaskEditor());
    }
  }

  private String capitalize(String line) {
    return Character.toUpperCase(line.charAt(0)) + line.substring(1);
  }

  public void setPartId(String partId) {
    this.partId = partId;
  }

  public String getPartId() {
    return partId;
  }

  private boolean isAttribute(TaskAttribute attribute, String id) {
    return attribute.getId().equals(
        attribute.getTaskData().getAttributeMapper()
            .mapToRepositoryKey(attribute.getParentAttribute(), id));
  }

  private void addSummaryText(Composite composite, final FormToolkit toolkit) {
    TaskAttribute summaryAttrib = getTaskData().getRoot().getMappedAttribute(TaskAttribute.SUMMARY);
    summaryEditor = createAttributeEditor(summaryAttrib);
    if (summaryEditor != null) {
      if (summaryAttrib.getMetaData().isReadOnly()) {
        summaryEditor.setReadOnly(true);
      }
      if (summaryEditor instanceof RichTextAttributeEditor) {
        // create composite to hold rounded border
        Composite roundedBorder =
            EditorUtil.createBorder(composite, toolkit, !summaryEditor.isReadOnly());
        summaryEditor.createControl(roundedBorder, toolkit);
        EditorUtil.setHeaderFontSizeAndStyle(summaryEditor.getControl());
      } else {
        final Composite border = toolkit.createComposite(composite);
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING)
            .hint(EditorUtil.MAXIMUM_WIDTH, SWT.DEFAULT).grab(true, false).applyTo(border);
        // leave some padding for the border of the attribute editor
        border.setLayout(GridLayoutFactory.fillDefaults().margins(1, 4).create());
        summaryEditor.createControl(border, toolkit);
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false)
            .applyTo(summaryEditor.getControl());
        toolkit.paintBordersFor(border);
      }
      getTaskEditorPage().getAttributeEditorToolkit().adapt(summaryEditor);
      for (Control control : summaryEditor.getControl().getParent().getChildren()) {
        if (control instanceof StyledText) {
          StyledText text = (StyledText) control;
          if (text.getText().length() == 0) {
            text.setToolTipText(ADD_SUMMARY_TEXT);
          }
        }
      }
    }
  }

  @Override
  public void createControl(Composite parent, FormToolkit toolkit) {
    Composite composite = toolkit.createComposite(parent);
    GridLayout layout = EditorUtil.createSectionClientLayout();
    layout.numColumns = 1;
    layout.marginHeight = 0;
    layout.marginTop = 0;
    layout.marginWidth = 0;
    layout.verticalSpacing = 3;
    composite.setLayout(layout);

    TaskAttribute colorIndexAttribute =
        getTaskData().getRoot().getAttribute(TaskAttribute.PRIORITY);

    if (colorIndexAttribute != null && colorIndexAttribute.getValue() != null
        && colorIndexAttribute.getValue().length() > 0
        && getTaskData().getRoot().getAttribute("Priority") != null) {
      String colorIndex = getTaskData().getRoot().getAttribute(TaskAttribute.PRIORITY).getValue();
      String priority = getTaskData().getRoot().getAttribute("Priority").getValue();
      Label label = toolkit.createLabel(composite, null);
      label.setImage(YouTrackConnectorUi.getPriorityIconImage(Integer.parseInt(colorIndex),
          priority, false));
      GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.CENTER).span(1, 2).applyTo(label);
      layout.numColumns++;
    }

    addSummaryText(composite, toolkit);

    if (Boolean.parseBoolean(getModel().getTaskRepository().getProperty(
        TaskEditorExtensions.REPOSITORY_PROPERTY_AVATAR_SUPPORT))) {
      TaskAttribute userAssignedAttribute =
          getTaskData().getRoot().getMappedAttribute(TaskAttribute.USER_ASSIGNED);
      if (userAssignedAttribute != null) {
        UserAttributeEditor editor = new UserAttributeEditor(getModel(), userAssignedAttribute);
        editor.createControl(composite, toolkit);
        GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.CENTER).span(1, 2).indent(0, 2)
            .applyTo(editor.getControl());
        layout.marginRight = 1;
        layout.numColumns++;
      }
    }

    if (needsHeader()) {
      createHeaderLayout(composite, toolkit);
    }

    toolkit.paintBordersFor(composite);

    setControl(composite);
  }
}
