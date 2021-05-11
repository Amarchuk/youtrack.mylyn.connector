/**
@author: amarch
*/

package com.jetbrains.youtrack.javarest.client;

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "projectShorts")
public class YouTrackProjectsList {
	 
    private List<YouTrackProject> projects = new LinkedList<YouTrackProject>();
	 
    @XmlElement(name = "projectShorts", type = YouTrackProject.class)
    public List<YouTrackProject> getProjects() {
        return projects;
    }
 
    public void setProjects(List<YouTrackProject> projects) {
        this.projects = projects;
    }
	
}
