package com.jetbrains.mylyn.yt.ui;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalListener;
import org.eclipse.jface.fieldassist.IContentProposalListener2;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.fieldassist.IControlContentAdapter;
import org.eclipse.jface.fieldassist.SimpleContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.jetbrains.youtrack.javarest.client.YouTrackClient;
import com.jetbrains.youtrack.javarest.utils.IntellisenseItem;
import com.jetbrains.youtrack.javarest.utils.IntellisenseValues;

public class IntellisenseFocusAdapter implements FocusListener {

  private Job autocomletionJob;

  private final String KEY_PRESS = "Ctrl+Space";

  private LinkedList<IntellisenseItem> items;

  private Map<String, IntellisenseItem> itemByNameMap = new HashMap<String, IntellisenseItem>();

  private IntellisenseValues intellisense;

  private SimpleContentProposalProvider scp;

  private ContentProposalAdapterOpenable adapter;

  private Timer timer;

  private Text widgetText;

  public Text issuesCountText;

  public int queryIssuesAmount;

  public boolean isCountIssuses = false;

  public YouTrackClient client;

  // in milliseconds
  private int updateTime = 200;

  private int showDelay = 500;

  private String lastSeenSearchSequence;

  private long lastTryTime = 0;

  private Job countIssuesJob;

  private Shell popupShell = null;

  private boolean isCommand;

  private final String issueId;

  public IntellisenseFocusAdapter(YouTrackClient client, boolean isCountIssues, Text issuesCountText) {
    this.client = client;
    this.isCountIssuses = isCountIssues;
    this.issuesCountText = issuesCountText;
    this.isCommand = false;
    this.issueId = "";
  }

  public IntellisenseFocusAdapter(YouTrackClient client, boolean isCountIssues,
      Text issuesCountText, String issueId) {
    this.client = client;
    this.isCountIssuses = isCountIssues;
    this.issuesCountText = issuesCountText;
    this.isCommand = true;
    this.issueId = issueId;
  }

  @Override
  public void focusLost(FocusEvent e) {
    if (autocomletionJob != null) {
      autocomletionJob.cancel();
      timer.cancel();
    }
  }

  private void syncWithUi(final boolean needOpen) {
    Display.getDefault().asyncExec(new Runnable() {
      @Override
      public void run() {

        if (needOpen) {
          scp.setProposals(intellisense.getFullOptions());
          openPopupProposals();
        } else {
          adapter.closeProposalPopup();
          if (isCountIssuses && issuesCountText != null && !issuesCountText.isDisposed()) {
            issuesCountText.setText("");
          }
        }

        if (isCountIssuses && issuesCountText != null && !issuesCountText.isDisposed()) {
          if (queryIssuesAmount == -1) {
            issuesCountText.setText("Can't get number of issues. Please try another query.");
          } else if (queryIssuesAmount == 1) {
            issuesCountText.setText("1 issue");
          } else {
            issuesCountText.setText(queryIssuesAmount + " issues");
          }
        }
      }
    });
  }

  public class ContentProposalAdapterOpenable extends ContentProposalAdapter {

    public ContentProposalAdapterOpenable(Control control,
        IControlContentAdapter controlContentAdapter, IContentProposalProvider proposalProvider,
        KeyStroke keyStroke, char[] autoActivationCharacters) {
      super(control, controlContentAdapter, proposalProvider, keyStroke, autoActivationCharacters);
    }

    public void openProposalPopup() {
      super.openProposalPopup();
    }

    public void closeProposalPopup() {
      super.closeProposalPopup();
    }

  }

  class CheckModification extends TimerTask {

    public Text widgetText;

    public String newSearchSequence;

    public int caret;

    public CheckModification(Text widgetText2) {
      this.widgetText = widgetText2;
      newSearchSequence = lastSeenSearchSequence;
    }

    public void run() {
      if (widgetText.isDisposed()) {
        autocomletionJob.cancel();
        if (countIssuesJob != null) {
          countIssuesJob.cancel();
        }
        timer.cancel();
        return;
      } else {

        Display.getDefault().syncExec(new Runnable() {
          public void run() {
            if (!widgetText.isDisposed()
                && (lastSeenSearchSequence == null || !lastSeenSearchSequence.equals(widgetText
                    .getText()))) {
              newSearchSequence = widgetText.getText();
              caret = widgetText.getCaretPosition();
              if (countIssuesJob != null) {
                countIssuesJob.cancel();
              }
            }
          }
        });

        if (lastSeenSearchSequence == null || !lastSeenSearchSequence.equals(newSearchSequence)) {
          lastSeenSearchSequence = newSearchSequence;
          lastTryTime = System.currentTimeMillis();

          if (newSearchSequence != null) {
            if (isCommand) {
              intellisense =
                  getClient().intellisenseCommandValues(newSearchSequence, caret, issueId);
            } else {
              intellisense = getClient().intellisenseSearchValues(newSearchSequence, caret);
            }
          } else {
            this.cancel();
          }

          if (isCountIssuses && issuesCountText != null) {
            final String filterText = newSearchSequence;
            countIssuesJob = new Job("count.issues.job") {
              @Override
              protected IStatus run(IProgressMonitor monitor) {
                if (isCountIssuses) {
                	System.out.println("called count.issues.job(" + filterText + ")");
                  queryIssuesAmount = getClient().getNumberOfIssues(filterText);
                }

                Display.getDefault().asyncExec(new Runnable() {
                  @Override
                  public void run() {
                    syncWithUi(true);
                  }
                });

                return Status.OK_STATUS;
              }
            };
            countIssuesJob.setUser(true);
            countIssuesJob.schedule();
          }

          items = intellisense.getIntellisenseItems();
          for (int ind = 0; ind < items.size(); ind++) {
            itemByNameMap.put(items.get(ind).getFullOption(), items.get(ind));
          }
          syncWithUi(false);
          return;
        } else {
          if (lastTryTime > 0 && lastTryTime + showDelay > System.currentTimeMillis()) {
            syncWithUi(true);
          }
        }
      }
    }
  }

  @Override
  public void focusGained(FocusEvent e) {

    if (e.getSource() instanceof Text) {
      widgetText = (Text) e.getSource();
    }

    try {

      if (this.isCommand) {
        intellisense = getClient().intellisenseCommandValues(widgetText.getText(), issueId);
      } else {
        intellisense = getClient().intellisenseSearchValues(widgetText.getText());
      }
      scp = new SimpleContentProposalProvider(intellisense.getFullOptions());
      KeyStroke ks = KeyStroke.getInstance(KEY_PRESS);
      adapter =
          new ContentProposalAdapterOpenable(widgetText, new TextContentAdapter(), scp, ks, null);
      adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_IGNORE);
      adapter.addContentProposalListener(new IContentProposalListener() {

        @Override
        public void proposalAccepted(IContentProposal proposal) {
          insertAcceptedProposal(proposal);
        }
      });
      adapter.addContentProposalListener(new IContentProposalListener2() {

        @Override
        public void proposalPopupOpened(ContentProposalAdapter adapter) {

          if (popupShell == null || popupShell.isDisposed()) {
            Shell[] shells = Display.getDefault().getShells();
            for (Shell shell : shells) {
              if (shell.isVisible() && shell.isEnabled()
                  && shell.getChildren()[0] instanceof Composite
                  && ((Composite) shell.getChildren()[0]).getChildren()[0] instanceof Table) {
                popupShell = shell;
                break;
              }
            }
          }

          Table table = (Table) ((Composite) popupShell.getChildren()[0]).getChildren()[0];

          final Display display = table.getDisplay();
          final TextLayout textLayout = new TextLayout(display);
          Font proposalFont = new Font(display, table.getFont().getFontData());
          final TextStyle proposalStyleUnderlined =
              new TextStyle(proposalFont, display.getSystemColor(SWT.COLOR_BLACK), null);
          proposalStyleUnderlined.underline = true;
          final TextStyle proposalStyle =
              new TextStyle(proposalFont, display.getSystemColor(SWT.COLOR_BLACK), null);

          table.addListener(SWT.PaintItem, new Listener() {
            @Override
            public void handleEvent(Event event) {
              if (event.item instanceof TableItem) {
                TableItem[] selectedItems = ((Table) event.widget).getSelection();
                TableItem tableItem = (TableItem) event.item;
                boolean selected = false;
                for (TableItem item : selectedItems) {
                  if (item == tableItem) {
                    tableItem.setForeground(display.getSystemColor(SWT.COLOR_GRAY));
                    tableItem.setBackground(display.getSystemColor(SWT.COLOR_GRAY));
                    selected = true;
                    break;
                  }
                }
                if (!selected) {
                  tableItem.setForeground(display.getSystemColor(SWT.COLOR_LIST_BACKGROUND));
                  tableItem.setBackground(display.getSystemColor(SWT.COLOR_LIST_BACKGROUND));
                }
                textLayout.setText(tableItem.getText());
                if (itemByNameMap.containsKey(tableItem.getText())) {
                  IntellisenseItem intellisenseItem = itemByNameMap.get(tableItem.getText());
                  textLayout.setStyle(proposalStyle, 0, tableItem.getText().length());
                  int prefixLen =
                      intellisenseItem.getPrefix() == null ? 0 : intellisenseItem.getPrefix()
                          .length();
                  textLayout.setStyle(proposalStyleUnderlined, prefixLen
                      + intellisenseItem.getMatchPositions().getStart(), prefixLen
                      + intellisenseItem.getMatchPositions().getEnd() - 1);

                  event.gc.fillRectangle(0, event.y, ((Table) event.widget).getClientArea().width,
                      event.height);
                  textLayout.draw(event.gc, event.x, event.y);
                }
              }
            }
          });
        }

        @Override
        public void proposalPopupClosed(ContentProposalAdapter adapter) {}
      });
    } catch (Exception e1) {
      throw new RuntimeException(e1);
    }

    autocomletionJob = new Job("command.autocompletion.proposals.job") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        timer = new Timer();
        timer.schedule(new CheckModification(widgetText), 0, updateTime);
        return Status.OK_STATUS;
      }
    };
    autocomletionJob.setUser(true);
    autocomletionJob.schedule();
  }

  private void insertAcceptedProposal(IContentProposal proposal) {
    IntellisenseItem item = itemByNameMap.get(proposal.getContent());
    String insertedContent = this.isCommand ? item.getOption() : item.getFullOption();
    insertedContent += " ";
    String beforeInsertion = widgetText.getText();
    String afterInsertion =
        beforeInsertion.substring(0, item.getCompletionPositions().getStart())
            + insertedContent
            + beforeInsertion.substring(beforeInsertion.length() == 0 ? 0 : item
                .getCompletionPositions().getEnd());
    widgetText.setText(afterInsertion);
    widgetText.setSelection(Integer.parseInt(item.getCaret()));
  }

  private void openPopupProposals() {
    adapter.openProposalPopup();
  }

  private YouTrackClient getClient() {
    return client;
  }

  public int getQueryIssuesAmount() {
    return this.queryIssuesAmount;
  }
}
