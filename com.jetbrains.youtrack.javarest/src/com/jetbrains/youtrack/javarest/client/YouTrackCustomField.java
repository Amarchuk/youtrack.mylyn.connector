/**
 * @author: amarch
 */

package com.jetbrains.youtrack.javarest.client;

import java.net.URL;
import java.util.Date;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "projectCustomField")
public class YouTrackCustomField {

  private String name;

  private String type;

  private URL fullURL;

  private String emptyText;

  private boolean canBeEmpty;

  private LinkedList<String> defaultValues;

  public static enum YouTrackCustomFieldType {
    BUILD_SINGLE("build[1]", false, true, String.class),
    BUILD_MULTI("build[*]", false, false, String.class),
    ENUM_SINGLE("enum[1]", false, true, String.class),
    ENUM_MULTI("enum[*]", false, false, String.class),
    USER_SINGLE("user[1]", false, true, String.class),
    USER_MULTI("user[*]", false, false, String.class),
    GROUP_SINGLE("group[1]", false, true, String.class),
    GROUP_MULTI("group[*]", false, false, String.class),
    OWNED_SINGLE("ownedField[1]", false, true, String.class),
    OWNED_MULTI("ownedField[*]", false, false, String.class),
    STATE("state[1]", false, true, String.class),
    VERSION_SINGLE("version[1]", false, true, String.class),
    VERSION_MULTI("version[*]", false, false, String.class),
    DATE("date", true, true, Date.class),
    DATE_AND_TIME("date and time", true, true, Date.class),
    INTEGER("integer", true, true, Integer.class),
    STRING("string", true, true, String.class),
    TEXT("text", true, true, String.class),
    FLOAT("float", true, true, Float.class),
    PERIOD("period", true, true, String.class);

    private String name;

    private boolean isSimple;

    private boolean singleField;

    private Class fieldValuesClass;

    YouTrackCustomFieldType(String name, boolean isSimple, boolean singleField, Class fieldVObject) {
      this.name = name;
      this.isSimple = isSimple;
      this.singleField = singleField;
      this.fieldValuesClass = fieldVObject;
    }

    public String getName() {
      return name;
    }

    public boolean isSimple() {
      return isSimple;
    }

    public boolean singleField() {
      return singleField;
    }

    public static YouTrackCustomFieldType getTypeByName(String name) {
      for (YouTrackCustomFieldType type : values()) {
        if (type.getName().equals(name)) {
          return type;
        }
      }
      return null;
    }

    public Class getFieldValuesClass() {
      return fieldValuesClass;
    }

    public void setFieldValuesClass(Class fieldValuesClass) {
      this.fieldValuesClass = fieldValuesClass;
    }

    public String toString() {
      return name;
    }

  }

  @XmlAttribute(name = "name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @XmlAttribute(name = "type")
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @XmlAttribute(name = "url")
  public URL getFullURL() {
    return fullURL;
  }

  public void setFullURL(URL fullURL) {
    this.fullURL = fullURL;
  }

  @XmlElement(name = "param")
  public LinkedList<CustomFieldParam> params;

  @XmlAttribute(name = "emptyText")
  public String getEmptyText() {
    return emptyText;
  }

  public void setEmptyText(String emptyText) {
    this.emptyText = emptyText;
  }

  private YouTrackCustomFieldBundle bundle;

  public YouTrackCustomFieldBundle findBundle() throws NoSuchElementException {
    for (CustomFieldParam param : params) {
      if ("bundle".equals(param.getName())) {
        this.setBundle(new YouTrackCustomFieldBundle(param.getValue()));
        bundle.setType(this.type);
        return bundle;
      }
    }
    throw new NoSuchElementException("No found bundles.");
  }

  public YouTrackCustomFieldBundle getBundle() {
    return bundle;
  }

  public void setBundle(YouTrackCustomFieldBundle bundle) {
    this.bundle = bundle;
  }

  public boolean isSingle() {
    return YouTrackCustomFieldType.getTypeByName(type).singleField();
  }

  @XmlAttribute(name = "canBeEmpty")
  public boolean isCanBeEmpty() {
    return canBeEmpty;
  }

  public void setCanBeEmpty(boolean canBeEmpty) {
    this.canBeEmpty = canBeEmpty;
  }

  @XmlElement(name = "defaultValue")
  public LinkedList<String> getDefaultValues() {
    return defaultValues;
  }

  public void setDefaultValues(LinkedList<String> defaultValues) {
    this.defaultValues = defaultValues;
  }

}
