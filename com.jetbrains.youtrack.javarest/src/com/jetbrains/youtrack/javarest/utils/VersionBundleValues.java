/**
@author: amarch
 */

package com.jetbrains.youtrack.javarest.utils;

import java.util.LinkedList;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "versions")
public class VersionBundleValues extends BundleValues {

    @XmlElement(name = "version", type = VersionValue.class)
    private LinkedList<VersionValue> bundleValues;

    public LinkedList<VersionValue> getVersionValues() {
	return bundleValues;
    }

    @Override
    public LinkedList<String> getValues() {
	LinkedList<String> values = new LinkedList<String>();
	if (bundleValues != null) {
	    for (BundleValue value : bundleValues) {
		values.add(value.getValue());
	    }
	}
	return values;
    }

}
