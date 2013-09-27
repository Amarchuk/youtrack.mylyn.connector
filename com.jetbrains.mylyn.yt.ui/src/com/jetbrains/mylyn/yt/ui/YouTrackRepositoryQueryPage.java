/*
* @author: amarch
* @author Steffen Pingel
* @author Frank Becker
* @since 3.7
*/

package com.jetbrains.mylyn.yt.ui;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalListener;
import org.eclipse.jface.fieldassist.SimpleContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.ProgressMonitorPart;
import org.eclipse.mylyn.commons.ui.CommonUiUtil;
import org.eclipse.mylyn.commons.ui.ProgressContainer;
import org.eclipse.mylyn.commons.workbench.forms.SectionComposite;
import org.eclipse.mylyn.internal.tasks.core.RepositoryQuery;
import org.eclipse.mylyn.internal.tasks.ui.wizards.Messages;
import org.eclipse.mylyn.internal.tasks.ui.wizards.QueryWizardDialog;
import org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylyn.tasks.core.IRepositoryQuery;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.mylyn.tasks.ui.wizards.AbstractRepositoryQueryPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressService;

import com.jetbrains.mylyn.yt.core.YouTrackConnector;
import com.jetbrains.mylyn.yt.core.YouTrackCorePlugin;
import com.jetbrains.youtrack.javarest.client.YouTrackClient;
import com.jetbrains.youtrack.javarest.utils.IntellisenseItem;
import com.jetbrains.youtrack.javarest.utils.IntellisenseSearchValues;
import com.jetbrains.youtrack.javarest.utils.SavedSearch;
import com.jetbrains.youtrack.javarest.utils.UserSavedSearch;

public class YouTrackRepositoryQueryPage extends AbstractRepositoryQueryPage{
	
	private TaskRepository repository;
	
	private List<SavedSearch> searches;
		
	private Combo savedSearchesCombo;
	
	private Text numberOfIssues1;
	
	private Text numberOfIssues2;
	
	private Button customizeQueryCheckbox;
	
	private Text searchBoxText;
	
	private final String KEY_PRESS = "Ctrl+Space";

	private LinkedList<IntellisenseItem> items;
	
	private Map<String, IntellisenseItem> itemByNameMap = new HashMap<String, IntellisenseItem>();
	
	private IntellisenseSearchValues intellisense;
	
	private SimpleContentProposalProvider scp;
	
	private Group fastQueryComposite;

	private Group customQueryComposite;
	
	private final String defaultMessage = "Choose saved search or customize query.";
	
	private Button cancelButton;

	private final AbstractRepositoryConnector connector;

	private boolean firstTime = true;

	private SectionComposite innerComposite;

	/**
	 * Determines whether a 'Clear Fields' button is shown on the page.
	 */
	private boolean needsClear;

	/**
	 * Determines whether a 'Refresh' button is shown on the page.
	 */
	private boolean needsRefresh = true;

	private ProgressContainer progressContainer;

	private Button refreshButton;

	private Text titleText;
	
	public YouTrackRepositoryQueryPage(String pageName, TaskRepository repository, IRepositoryQuery query) {
		super("youtrack.repository.query.page", repository, query);
		this.connector = TasksUi.getRepositoryConnector(getTaskRepository().getConnectorKind());
		this.repository = repository;
		setTitle("YouTrack Repository Query");
	}

	protected void doRefreshControls(){
		doFullRefresh();
	}
	
	protected boolean hasRepositoryConfiguration() {
		return true;
	}
	
	protected boolean restoreState(IRepositoryQuery query) {
		String filter = query.getAttribute(YouTrackCorePlugin.QUERY_KEY_FILTER);
		if (filter != null) {
			if(customizeQueryCheckbox.getSelection()){
				searchBoxText.setText(filter);
			} else {
				for(SavedSearch savedSearch : searches){
					if(savedSearch.getSearchText().equals(filter)){
						savedSearchesCombo.setText(savedSearch.getName());
						break;
					}
				}
			}
		}
		return true;
	}

	@Override
	public void applyTo(IRepositoryQuery query) {
		if (getQueryTitle() != null) {
			query.setSummary(getQueryTitle());
		}
		if(savedSearchesCombo.getText() != null && !customizeQueryCheckbox.getSelection()){
			query.setAttribute(YouTrackCorePlugin.QUERY_KEY_FILTER, 
					searches.get(savedSearchesCombo.getSelectionIndex()).getSearchText());
		}
		if(searchBoxText.getText() != null && customizeQueryCheckbox.getSelection()){
			query.setAttribute(YouTrackCorePlugin.QUERY_KEY_FILTER, searchBoxText.getText());
		}
		setMessage(defaultMessage);
	}

	protected void createPageContent(SectionComposite parent) {
		createFastQueryCompositeContent(parent);
		createCustomizeQueryContent(parent);
	}
	
	protected void createFastQueryCompositeContent(SectionComposite parent){
		
		customizeQueryCheckbox = new Button(parent.getContent(), SWT.RADIO);
		customizeQueryCheckbox.setText("Saved search");
		customizeQueryCheckbox.addSelectionListener(new SelectionListener() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				
				Button button = (Button) e.widget;
				doPartialRefresh();
				
		        if(button.getSelection()){
		        	setMessage("Choose saved search.");
					recursiveSetEnabled(fastQueryComposite, true);
					recursiveSetEnabled(customQueryComposite, false);
					numberOfIssues1.setEnabled(false);
		        } else {
		        	setMessage("Enter query into search box (press Ctrl+Space for query completion).");
		        	recursiveSetEnabled(fastQueryComposite, false);
					recursiveSetEnabled(customQueryComposite, true);
					numberOfIssues2.setEnabled(false);
					setQueryTitle("");
		        }
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		customizeQueryCheckbox.setSelection(true);
		
		fastQueryComposite = new Group(parent.getContent(), SWT.NONE);
		fastQueryComposite.setLayout(new GridLayout(1, false));
		
		GridData gd = new GridData(SWT.FILL);
		gd.grabExcessHorizontalSpace = true;
		gd.horizontalAlignment = SWT.FILL;
		fastQueryComposite.setLayoutData(gd);
		
		savedSearchesCombo = new Combo(fastQueryComposite, SWT.FILL);
		savedSearchesCombo.setLayoutData(gd);
		
		fillSearches();
		
		Composite numberOfIssuesComposite = new Composite(fastQueryComposite, SWT.NONE);
		numberOfIssuesComposite.setLayout(new GridLayout(2, false));
		
		Label labelIssues = new Label(numberOfIssuesComposite, SWT.NONE);
		
		numberOfIssues1 = new Text(numberOfIssuesComposite, SWT.SINGLE);
		numberOfIssues1.setEnabled(false);
		
		savedSearchesCombo.addListener(SWT.Selection, new Listener() {
	        @Override
	        public void handleEvent(Event arg0) {
	        	
	        	if(getQueryTitle() == null || getQueryTitle().length() == 0){
	        		setQueryTitle(savedSearchesCombo.getText());
	        	}

	        	int queryIssuesAmount;
				try {
					queryIssuesAmount = getClient().getNumberOfIssues(searches.get(savedSearchesCombo.getSelectionIndex()).getSearchText());
					if(queryIssuesAmount == -1){
						numberOfIssues1.setText("Can't get number of issues. Please retry to select.");
					} else if(queryIssuesAmount == 1){
						numberOfIssues1.setText("1 issue");
					} else {
						numberOfIssues1.setText(queryIssuesAmount + " issues");
					}
				} catch (CoreException e) {
					numberOfIssues1.setText("");
					throw new RuntimeException(e.getMessage());
				}
	        }
	    });
	}
	
	protected void createCustomizeQueryContent(SectionComposite parent){
	
		customizeQueryCheckbox = new Button(parent.getContent(), SWT.RADIO);
		customizeQueryCheckbox.setText("Custom query");
		
		customQueryComposite = new Group(parent.getContent(), SWT.NONE);
		customQueryComposite.setLayout(new GridLayout(1, false));
		
		GridData gd = new GridData(SWT.FILL);
		gd.grabExcessHorizontalSpace = true;
		gd.horizontalAlignment = SWT.FILL;
		customQueryComposite.setLayoutData(gd);

		Listener countSuitableIssuesListener = new Listener() {
	        @Override
	        public void handleEvent(Event arg0) {
	        	countIssuesInListeners();
	        }
	    };
	    
	    Label searchBoxLabel = new Label(customQueryComposite, SWT.NONE);
	    searchBoxLabel.setText("Search Box:");
	    
		searchBoxText = new Text(customQueryComposite, SWT.SINGLE | SWT.FILL);
		searchBoxText.setLayoutData(gd);
		searchBoxText.addListener(SWT.CHANGED, countSuitableIssuesListener);
		
		try {
			intellisense = getClient().intellisenseSearchValues(searchBoxText.getText());   
			scp = new SimpleContentProposalProvider(intellisense.getOptions());
            KeyStroke ks = KeyStroke.getInstance(KEY_PRESS);
            ContentProposalAdapter adapter = new ContentProposalAdapter(searchBoxText, new TextContentAdapter(), scp, ks, null);
            adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_IGNORE);
            adapter.addContentProposalListener(new IContentProposalListener() {
				
				@Override
				public void proposalAccepted(IContentProposal proposal) {
					insertAcceptedProposal(proposal);
				}
			});
        }
        catch(Exception e)
        {
           throw new RuntimeException(e);
        }
		
		searchBoxText.addKeyListener( new KeyAdapter()
        {
            public void keyReleased(KeyEvent ke)
            {	
            	try {
            		intellisense = getClient().intellisenseSearchValues(searchBoxText.getText(), searchBoxText.getCaretPosition());
            		items  = intellisense.getIntellisenseItems();
            		for(int ind = 0; ind < items.size(); ind++){
            			itemByNameMap.put(items.get(ind).getFullOption(), items.get(ind));
            		}
					scp.setProposals(intellisense.getOptions()); 
				} catch (CoreException e) {
					throw new RuntimeException(e);
				}
            }
        }
        );
		
		Composite numberOfIssuesComposite = new Composite(customQueryComposite, SWT.NONE);
		numberOfIssuesComposite.setLayout(new GridLayout(2, false));
		
		Label labelIssues = new Label(numberOfIssuesComposite, SWT.NONE);
		numberOfIssues2 = new Text(numberOfIssuesComposite, SWT.SINGLE);
		numberOfIssues2.setEnabled(false);
		
		createTitleGroup(customQueryComposite);

		recursiveSetEnabled(customQueryComposite, false);
	}
	
	private YouTrackClient getClient() throws CoreException {
		return YouTrackConnector.getClient(getTaskRepository());
	}
	
	protected void doPartialRefresh() {
		savedSearchesCombo.removeAll();
		numberOfIssues1.setText("");
		fillSearches();
		for(SavedSearch search : this.searches){
			savedSearchesCombo.add(search.getName());
		}
		
		searchBoxText.setText("");
		numberOfIssues2.setText("");
	}
	
	protected void doFullRefresh() {
		doPartialRefresh();
		setQueryTitle("");
		setMessage(defaultMessage);
	}
	
	protected void fillSearches(){
		try{
			if(repository.getUserName() != null){
					searches = new LinkedList<>();
					LinkedList<UserSavedSearch> userSearches = getClient().getSavedSearchesForUser(repository.getUserName());
					for(UserSavedSearch userSearch : userSearches){
						searches.add(userSearch.convertIntoSavedSearch());
					}
			} else {
				searches = getClient().getSavedSearches();
			}
		} catch (CoreException e) {
			throw new RuntimeException("Exception while refreshing" + e.toString());
		} 
	}
	
	@Override
	public boolean canFlipToNextPage(){
		return false;
	}
	
	public void recursiveSetEnabled(Control ctrl, boolean enabled) {
		if (ctrl instanceof Composite) {
	      Composite comp = (Composite) ctrl;
	      if(comp.getChildren().length == 0){
	    	 comp.setEnabled(enabled);
	      } else {
	    	  for (Control control : comp.getChildren()){
	    		  recursiveSetEnabled(control, enabled);
	    	  }
	      }
		} else {
	      ctrl.setEnabled(enabled);
		}
	}	
	
	private void insertAcceptedProposal(IContentProposal proposal){
		IntellisenseItem item = itemByNameMap.get((proposal.getContent()));
		String beforeInsertion = searchBoxText.getText();
		String afterInsertion = beforeInsertion.substring(0, item.getCompletionPositions().getStart()) + 
				proposal.getContent() + beforeInsertion.substring(item.getCompletionPositions().getEnd());
		searchBoxText.setText(afterInsertion);
		searchBoxText.setSelection(Integer.parseInt(item.getCaret()));
	}
	
	private void countIssuesInListeners(){
		try {
			int queryIssuesAmount = 
					((YouTrackConnector) getConnector()).queryIssuesAmount(null, searchBoxText.getText(), getTaskRepository());
			
			if(queryIssuesAmount == -1){
				numberOfIssues2.setText("Can't get number of issues. Please retry to select.");
			} else if(queryIssuesAmount == 1){
				numberOfIssues2.setText("1 issue");
			} else {
				numberOfIssues2.setText(queryIssuesAmount + " issues");
			}
		} catch (CoreException e) {
			numberOfIssues2.setText("");
			throw new RuntimeException(e.getMessage());
		}
	}
	
	/*
	 * Code below is code from AbstractRepositoryQueryPage2
	 * 
	 */

	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.FILL).grab(true, true).applyTo(composite);
		GridLayout layout = new GridLayout(2, false);
		if (inSearchContainer()) {
			layout.marginWidth = 0;
			layout.marginHeight = 0;
		}
		composite.setLayout(layout);

		innerComposite = new SectionComposite(composite, SWT.NONE);
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.FILL).grab(true, true).span(2, 1).applyTo(innerComposite);
		createPageContent(innerComposite);

		createButtonGroup(composite);

		if (!needsRefresh) {
			setDescription(Messages.AbstractRepositoryQueryPage2_Create_a_Query_Page_Description);
		}

		Dialog.applyDialogFont(composite);
		setControl(composite);
	}

	@Override
	public String getQueryTitle() {
		return (titleText != null) ? titleText.getText() : null;
	}

	public boolean handleExtraButtonPressed(int buttonId) {
		if (buttonId == QueryWizardDialog.REFRESH_BUTTON_ID) {
			if (getTaskRepository() != null) {
				refreshConfiguration(true);
			} else {
				MessageDialog.openInformation(
						Display.getCurrent().getActiveShell(),
						Messages.AbstractRepositoryQueryPage2_Update_Attributes_Failed,
						Messages.AbstractRepositoryQueryPage2_No_repository_available_please_add_one_using_the_Task_Repositories_view);
			}
			return true;
		} else if (buttonId == QueryWizardDialog.CLEAR_BUTTON_ID) {
			doClearControls();
			return true;
		}
		return false;
	}

	@Override
	public boolean isPageComplete() {
		if (titleText != null && titleText.getText().length() > 0) {
			return true;
		}
		setMessage(defaultMessage);
		return false;
	}

	public boolean needsClear() {
		return needsClear;
	}

	public boolean needsRefresh() {
		return needsRefresh;
	}

	@Override
	public boolean performSearch() {
		if (inSearchContainer()) {
			saveState();
		}
		return super.performSearch();
	}

	@Override
	public void saveState() {
		if (inSearchContainer()) {
			RepositoryQuery query = new RepositoryQuery(getTaskRepository().getConnectorKind(), "handle"); //$NON-NLS-1$
			applyTo(query);

			IDialogSettings settings = getDialogSettings();
			if (settings != null) {
				settings.put(getSavedStateSettingKey(), query.getUrl());
			}
		}
	}

	public void setExtraButtonState(Button button) {
		Integer obj = (Integer) button.getData();
		if (obj == QueryWizardDialog.REFRESH_BUTTON_ID) {
			if (needsRefresh) {
				if (!button.isVisible()) {
					button.setVisible(true);
				}
				button.setEnabled(true);
			} else {
				if (button != null && button.isVisible()) {
					button.setVisible(false);
				}
			}
		} else if (obj == QueryWizardDialog.CLEAR_BUTTON_ID) {
			if (!button.isVisible()) {
				button.setVisible(true);
			}
			button.setEnabled(true);
		}

	}

	public void setNeedsClear(boolean needsClearButton) {
		this.needsClear = needsClearButton;
	}

	public void setNeedsRefresh(boolean needsRefresh) {
		this.needsRefresh = needsRefresh;
	}

	public void setQueryTitle(String text) {
		if (titleText != null) {
			titleText.setText(text);
		}
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);

		if (getSearchContainer() != null) {
			getSearchContainer().setPerformActionEnabled(true);
		}

		if (visible && firstTime) {
			firstTime = false;
			if (!hasRepositoryConfiguration() && needsRefresh) {
				// delay the execution so the dialog's progress bar is visible
				// when the attributes are updated
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						if (getControl() != null && !getControl().isDisposed()) {
							initializePage();
						}
					}
				});
			} else {
				// no remote connection is needed to get attributes therefore do
				// not use delayed execution to avoid flickering
				initializePage();
			}
		}
	}

	private void createButtonGroup(Composite parent) {
		Composite buttonComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		buttonComposite.setLayout(layout);
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.TOP).grab(true, false).span(2, 1).applyTo(buttonComposite);
		createButtons(buttonComposite);
		if (buttonComposite.getChildren().length > 0) {
			layout.numColumns = buttonComposite.getChildren().length;
		} else {
			// remove composite to avoid spacing
			buttonComposite.dispose();
		}
	}

	private void createTitleGroup(Composite control) {
		if (inSearchContainer()) {
			return;
		}

		Label titleLabel = new Label(control, SWT.NONE);
		titleLabel.setText(Messages.AbstractRepositoryQueryPage2__Title_);

		titleText = new Text(control, SWT.BORDER);
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.TOP).grab(true, false).applyTo(titleText);
		titleText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				getContainer().updateButtons();
			}
		});
	}

	private void initializePage() {
		if (needsRefresh) {
			boolean refreshed = refreshConfiguration(false);
			if (!refreshed) {
				// always do a refresh when page is initially shown
				if (!innerComposite.isDisposed()) {
					doRefreshControls();
				}
			}
		}
		boolean restored = false;
		if (getQuery() != null) {
			titleText.setText(getQuery().getSummary());
			restored |= restoreState(getQuery());
		} else if (inSearchContainer()) {
			restored |= restoreSavedState();
		}
		if (!restored) {
			// initialize with default values
			if (!innerComposite.isDisposed()) {
				doClearControls();
			}
		}
	}

	protected boolean refreshConfiguration(final boolean force) {
		if (force || !hasRepositoryConfiguration()) {
			setErrorMessage(null);
			try {
				doRefreshConfiguration();
				if (!innerComposite.isDisposed()) {
					doRefreshControls();
				}
				return true;
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof CoreException) {
					setErrorMessage(((CoreException) e.getCause()).getStatus().getMessage());
				} else {
					setErrorMessage(e.getCause().getMessage());
				}
			} catch (InterruptedException e) {
				// canceled
			}
		}
		return false;
	}

	private void doRefreshConfiguration() throws InvocationTargetException, InterruptedException {
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				monitor = SubMonitor.convert(monitor);
				monitor.beginTask(Messages.AbstractRepositoryQueryPage2_Refresh_Configuration_Button_Label,
						IProgressMonitor.UNKNOWN);
				try {
					connector.updateRepositoryConfiguration(getTaskRepository(), monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} catch (OperationCanceledException e) {
					throw new InterruptedException();
				} finally {
					monitor.done();
				}
			}
		};
		if (getContainer() != null) {
			getContainer().run(true, true, runnable);
		} else if (progressContainer != null) {
			progressContainer.run(true, true, runnable);
		} else if (getSearchContainer() != null) {
			getSearchContainer().getRunnableContext().run(true, true, runnable);
		} else {
			IProgressService service = PlatformUI.getWorkbench().getProgressService();
			service.busyCursorWhile(runnable);
		}
	}

	protected void createButtons(final Composite composite) {
		if (getContainer() instanceof QueryWizardDialog) {
			// refresh and clear buttons are provided by the dialog
			return;
		}
		if (needsRefresh) {
			refreshButton = new Button(composite, SWT.PUSH);
			refreshButton.setText(Messages.AbstractRepositoryQueryPage2__Refresh_From_Repository);
			refreshButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (getTaskRepository() != null) {
						refreshConfiguration(true);
					} else {
						MessageDialog.openInformation(
								Display.getCurrent().getActiveShell(),
								Messages.AbstractRepositoryQueryPage2_Update_Attributes_Failed,
								Messages.AbstractRepositoryQueryPage2_No_repository_available_please_add_one_using_the_Task_Repositories_view);
					}
				}
			});
		}
		if (needsClear) {
			Button clearButton = new Button(composite, SWT.PUSH);
			clearButton.setText(Messages.AbstractRepositoryQueryPage2_Clear_Fields);
			clearButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					doClearControls();
				}
			});
		}
		final ProgressMonitorPart progressMonitorPart = new ProgressMonitorPart(composite, null);
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false).applyTo(progressMonitorPart);
		progressMonitorPart.setVisible(false);
		progressContainer = new ProgressContainer(composite.getShell(), progressMonitorPart) {
			@Override
			protected void restoreUiState(java.util.Map<Object, Object> state) {
				cancelButton.setVisible(false);
				CommonUiUtil.setEnabled(innerComposite, true);
				for (Control control : composite.getChildren()) {
					if (control instanceof ProgressMonitorPart) {
						break;
					}
					control.setEnabled(true);
				}
			}

			@Override
			protected void saveUiState(java.util.Map<Object, Object> savedState) {
				CommonUiUtil.setEnabled(innerComposite, false);
				for (Control control : composite.getChildren()) {
					if (control instanceof ProgressMonitorPart) {
						break;
					}
					control.setEnabled(false);
				}
				cancelButton.setEnabled(true);
				cancelButton.setVisible(true);
			}
		};

		cancelButton = new Button(composite, SWT.PUSH);
		cancelButton.setText(IDialogConstants.CANCEL_LABEL);
		cancelButton.setVisible(false);
		progressContainer.setCancelButton(cancelButton);
	}

	protected void doClearControls() {
	}

	protected AbstractRepositoryConnector getConnector() {
		return connector;
	}

	protected String getSavedStateSettingKey() {
		return getName() + "." + getTaskRepository().getRepositoryUrl(); //$NON-NLS-1$
	}

	protected boolean restoreSavedState() {
		IDialogSettings settings = getDialogSettings();
		if (settings != null) {
			String queryUrl = settings.get(getSavedStateSettingKey());
			if (queryUrl != null) {
				RepositoryQuery query = new RepositoryQuery(getTaskRepository().getConnectorKind(), "handle"); //$NON-NLS-1$
				query.setUrl(queryUrl);
				return restoreState(query);
			}
		}
		return false;
	}
}
