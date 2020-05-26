package org.acme.poc.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * 
 * @author dtorresf
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "dependent-dynamic-task")
public class DynamicDependentTaskRequest {

    @XmlElement(name = "after-task-name")
    private String afterTask;
    @XmlElement(name = "before-task-name")
    private String beforeTask;
    @XmlElement(name = "task-name")
    private String taskName;

    /**
     * @return String return the afterTask
     */
    public String getAfterTask() {
        return afterTask;
    }

    /**
     * @param afterTask the afterTask to set
     */
    public void setAfterTask(String afterTask) {
        this.afterTask = afterTask;
    }

    /**
     * @return String return the beforeTask
     */
    public String getBeforeTask() {
        return beforeTask;
    }

    /**
     * @param beforeTask the beforeTask to set
     */
    public void setBeforeTask(String beforeTask) {
        this.beforeTask = beforeTask;
    }

    /**
     * @return String return the taskName
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * @param taskName the taskName to set
     */
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

}