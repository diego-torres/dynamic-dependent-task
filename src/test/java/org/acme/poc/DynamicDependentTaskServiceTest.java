/**
 * 
 */
package org.acme.poc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.acme.poc.model.DynamicDependentTaskRequest;
import org.jbpm.services.api.model.DeploymentUnit;
import org.jbpm.services.api.model.NodeInstanceDesc;
import org.jbpm.test.services.AbstractKieServicesTest;
import org.junit.Test;
import org.kie.api.runtime.query.QueryContext;
import org.kie.server.services.api.KieServerRegistry;
import org.kie.server.services.impl.KieServerRegistryImpl;
import org.kie.server.services.impl.marshal.MarshallerHelper;

/**
 * 
 * @author dtorresf
 */
public class DynamicDependentTaskServiceTest extends AbstractKieServicesTest {

    protected static final String ARTIFACT_ID = "test-module";
    protected static final String GROUP_ID = "org.acme.kie";
    protected static final String VERSION = "1.0.0";

    private DynamicDependentTaskResource dynamicTaskResource;
    private KieServerRegistry context;

    @Override
    protected List<String> getProcessDefinitionFiles() {
        return Arrays.asList("two-ht-process.bpmn");
    }

    @Override
    protected DeploymentUnit prepareDeploymentUnit() throws Exception {
        return createAndDeployUnit(GROUP_ID, ARTIFACT_ID, VERSION);
    }

    @Override
    protected void configureServices() {
        super.configureServices();
        context = new KieServerRegistryImpl();
        this.dynamicTaskResource = new DynamicDependentTaskResource(processService, context);
    }

    @Test
    public void testDynamicTaskBetween() {
        Long processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "two-ht-process",
                new HashMap<String, Object>());

        assertNotNull(processInstanceId);

        // Assert wait state for process instance
        Collection<NodeInstanceDesc> activeNodes = runtimeDataService.getProcessInstanceHistoryActive(processInstanceId,
                new QueryContext());

        assertNotNull(activeNodes);
        assertEquals(1, activeNodes.size());
        assertEquals("Task A", activeNodes.iterator().next().getName());

        // Modify the process
        DynamicDependentTaskRequest request = new DynamicDependentTaskRequest();
        request.setAfterTask("Task A");
        request.setBeforeTask("Task B");
        request.setTaskName("Task A.1");

        MarshallerHelper marshaller = new MarshallerHelper(context);
        String requestString = marshaller.marshal("application/json", request);
        dynamicTaskResource.createTaskBetween(deploymentUnit.getIdentifier(), processInstanceId, requestString);

        // Claim and complete Task A
        userTaskService.completeAutoProgress(
                runtimeDataService.getTaskByWorkItemId(activeNodes.iterator().next().getWorkItemId()).getTaskId(),
                "Administrator", new HashMap<String, Object>());

        // assert dynamic task is now the next task to execute
        activeNodes = runtimeDataService.getProcessInstanceHistoryActive(processInstanceId, new QueryContext());

        assertNotNull(activeNodes);
        assertEquals(1, activeNodes.size());
        assertEquals("Task A.1", activeNodes.iterator().next().getName());

        processService.abortProcessInstance(processInstanceId);
    }

}