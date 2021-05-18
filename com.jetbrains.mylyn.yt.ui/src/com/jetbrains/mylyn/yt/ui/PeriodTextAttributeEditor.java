/*******************************************************************************
 * Copyright (c) 2004, 2009 Tasktop Technologies and others. All rights reserved. This program and
 * the accompanying materials are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package com.jetbrains.mylyn.yt.ui;

import org.eclipse.mylyn.commons.workbench.forms.CommonFormUtil;
import org.eclipse.mylyn.internal.tasks.ui.editors.EditorUtil;
import org.eclipse.mylyn.internal.tasks.ui.editors.TextAttributeEditor;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskDataModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;

import com.jetbrains.mylyn.yt.core.YouTrackRepositoryConnector;
import com.jetbrains.youtrack.javarest.utils.YouTrackTimeSettings;
import com.sun.jersey.api.client.UniformInterfaceException;

/**
 * @author Steffen Pingel
 * @author Alexander Marchuk
 */
public class PeriodTextAttributeEditor extends TextAttributeEditor {

  private Text text;

  private boolean suppressRefresh;

  private String emptyValue;

  private TaskRepository repository;

  public PeriodTextAttributeEditor(TaskDataModel manager, TaskAttribute taskAttribute,
      String emptyValue, TaskRepository taskRepository) {
    super(manager, taskAttribute);
    this.emptyValue = emptyValue;
    this.repository = taskRepository;
  }

  protected Text getText() {
    return text;
  }

  @Override
  public void createControl(Composite parent, FormToolkit toolkit) {
    if (isReadOnly()) {
      text = new Text(parent, SWT.FLAT | SWT.READ_ONLY);
      text.setFont(EditorUtil.TEXT_FONT);
      text.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);
      text.setToolTipText(getDescription());
      text.setText(getValue());
    } else {
      text = toolkit.createText(parent, getValue(), SWT.FLAT);
      text.setFont(EditorUtil.TEXT_FONT);
      text.setData(FormToolkit.KEY_DRAW_BORDER, FormToolkit.TREE_BORDER);
      text.setToolTipText(getDescription());
      text.addModifyListener(new ModifyListener() {
        public void modifyText(ModifyEvent e) {
          Text widget = (Text) e.widget;
          try {
            suppressRefresh = true;
            if (widget.getText().equals("")) {
              widget.setText(emptyValue == null ? "" : emptyValue);
            } else {
              setValue(text.getText());
            }
          } finally {
            suppressRefresh = false;
          }
          CommonFormUtil.ensureVisible(text);
        }
      });
    }
    toolkit.adapt(text, false, false);
    setControl(text);
  }

  public String getValue() {
    try {
      Long.parseLong(getAttributeMapper().getValue(getTaskAttribute()));
      return parsePeriodData(getAttributeMapper().getValue(getTaskAttribute()));
    } catch (NumberFormatException e) {
      return getAttributeMapper().getValue(getTaskAttribute());
    }

  }

  public void setValue(String text) {
    getAttributeMapper().setValue(getTaskAttribute(), text);
    attributeChanged();
  }

  @Override
  public void refresh() {
    if (text != null && !text.isDisposed()) {
      text.setText(getValue());
    }
  }

  @Override
  public boolean shouldAutoRefresh() {
    return !suppressRefresh;
  }

  /**
   * @param period in minutes from rest
   * @return period in format AwBdChDm (weeks:days:hours:minutes)
   */
  private String parsePeriodData(String period) {
    try {
      int minutesAmount = Integer.parseInt(period);
      YouTrackTimeSettings timeSettings = YouTrackRepositoryConnector.getTimeSettings(repository);
      if (timeSettings == null) {
        return "";
      }
      int hourPerDay = timeSettings.getHoursSettings();
      int daysPerWeek = timeSettings.getDaysSettings();
      String result = "";
      int weeks = minutesAmount / (hourPerDay * daysPerWeek * 60);
      result += weeks > 0 ? (weeks + "w") : "";
      minutesAmount -= weeks * hourPerDay * daysPerWeek * 60;
      int days = minutesAmount / (hourPerDay * 60);
      result += days > 0 ? (days + "d") : "";
      minutesAmount -= days * hourPerDay * 60;
      int hours = minutesAmount / 60;
      result += hours > 0 ? (hours + "h") : "";
      int minutes = minutesAmount - hours * 60;
      result += minutes > 0 ? (minutes + "m") : "";
      return result;
    } catch (NumberFormatException e) {
      return period;
    } catch (UniformInterfaceException e) {
      e.printStackTrace();
      return period + "m";
    }
  }

}
