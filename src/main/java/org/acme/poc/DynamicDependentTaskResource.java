package org.acme.poc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.acme.poc.model.DynamicDependentTaskRequest;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.jbpm.process.instance.impl.ProcessInstanceImpl;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.services.api.ProcessService;
import org.jbpm.workflow.core.WorkflowProcess;
import org.jbpm.workflow.core.impl.ConnectionImpl;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.jbpm.workflow.core.node.HumanTaskNode;
import org.kie.api.definition.process.Connection;
import org.kie.server.services.api.KieServerRegistry;
import org.kie.server.services.impl.marshal.MarshallerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Example;
import io.swagger.annotations.ExampleProperty;

@Api(value = "Dynamic Dependent Task")
@Path("acme/dynamic-task")
public class DynamicDependentTaskResource {
    private static final Logger logger = LoggerFactory.getLogger(DynamicDependentTaskResource.class);

    private KieServerRegistry context;
    private ProcessService processService;

    private MarshallerHelper marshallerHelper;

    public DynamicDependentTaskResource(ProcessService processService, KieServerRegistry context) {
        this.processService = processService;
        this.context = context;
        this.marshallerHelper = new MarshallerHelper(context);
    }

    @ApiOperation(value = "Inserts a task node between two other task nodes", response = String.class, code = 200)
    @ApiResponses(value = { @ApiResponse(code = 500, message = "Unexpected error"),
            @ApiResponse(code = 404, message = "Process Instance, Container id, anchor tasks or node connectors not found"),
            @ApiResponse(code = 200, message = "OK", examples = @Example(value = {
                    @ExampleProperty(mediaType = "text/plain", value = "OK") })) })
    @POST
    @Path("insert-between/{containerId}/{processInstanceId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.TEXT_PLAIN })
    public String createTaskBetween(
            @ApiParam(value = "container id that the process instance belongs to", required = true, example = "kjar_1.0.0") @PathParam("containerId") String containerId,
            @ApiParam(value = "identifier of the process instance to be modified", required = true, example = "1234") @PathParam("processInstanceId") Long processInstanceId,
            @ApiParam(value = "Custom Dynamic Task Request", required = true, examples = @Example(value = {
                    @ExampleProperty(mediaType = "application/json", value = "{\n\t\"after-task-name\":\"Task A\",\n\t\"before-task-name\":\"Task B\",\n\t\"task-name\":\"TaskA1\",\n}") })) String dependentTaskRequest) {

        DynamicDependentTaskRequest request = marshallerHelper.unmarshal(dependentTaskRequest, "application/json",
                DynamicDependentTaskRequest.class);

        // Retrieve the process instance to be modified (casted to ProcessInstanceImpl,
        // which has the methods to update the process)
        ProcessInstanceImpl processInstance = (ProcessInstanceImpl) processService
                .getProcessInstance(processInstanceId);

        if (processInstance == null) {
            // TODO: Form a 404 response (process instance not found)
            logger.error("Process Instance id not found: {}", processInstanceId);
            return "NOK";
        }

        // Retrieve the process definition from the kbase.
        // Note that the ProcessInstanceImpl also has a getProcess method, but the
        // method also references the kbase and retrieves the process id from the given
        // kbase resources.
        RuleFlowProcess process = (RuleFlowProcess) context.getContainer(containerId).getKieContainer().getKieBase()
                .getProcess(processInstance.getProcessId());

        if (process == null) {
            // TODO: Form a 404 response (process definition not found)
            logger.error("Process definition not found", processInstance.getProcessId());
            return "NOK";
        }

        try {
            // FIXME: This modifies the process definition, all process instances that
            // belong to that instance are affected.
            insertNodeInBetween(process, request.getBeforeTask(), request.getAfterTask(), request.getTaskName());
            processInstance.setKnowledgeRuntime(
                    (InternalKnowledgeRuntime) context.getContainer(containerId).getKieContainer().getKieSession());

            processInstance.updateProcess(process);
            processInstance.reconnect();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Unable to insert dynamic task", e);
            return "NOK";
        }

        return "OK";
    }

    private void insertNodeInBetween(RuleFlowProcess process, String beforeNodeName, String afterNodeName,
            String newNodeName) {
        NodeImpl beforeNode = getNodeByName(process, beforeNodeName);
        NodeImpl afterNode = getNodeByName(process, afterNodeName);

        if (beforeNode == null || afterNode == null) {
            throw new IllegalArgumentException("Unable to find before or after node");
        }

        Connection target = afterNode.getDefaultOutgoingConnections().stream()
                .filter(c -> beforeNode.getId() == c.getTo().getId()).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Connection to node " + beforeNodeName
                        + " not found in process " + process.getId() + " From Node " + afterNodeName));

        // TODO: Define different node types from received parameters
        HumanTaskNode node = new HumanTaskNode();
        node.setName(newNodeName);
        node.setId(process.getNodes().length + 1);

        Map<String, Object> dataInputs = new HashMap<>();
        dataInputs.put("Skippable", "false");
        dataInputs.put("TaskName", "TaskA1");
        dataInputs.put("GroupId", "users");

        node.getMetaData().put("UniqueId", "_" + UUID.randomUUID().toString());
        node.getMetaData().put("elementname", newNodeName);
        node.getMetaData().put("DataInputs", dataInputs);
        node.getMetaData().put("DataOutputs", new HashMap<String, Object>());

        node.setWaitForCompletion(true);
        node.getWork().setParameter("TaskName", "TaskA1");
        node.getWork().setParameter("GroupId", "users");
        node.getWork().setParameter("Skippable", "false");
        node.getWork().setParameter("NodeName", newNodeName);

        process.addNode(node);
        ((ConnectionImpl) target).terminate();
        new ConnectionImpl(afterNode, NodeImpl.CONNECTION_DEFAULT_TYPE, node, NodeImpl.CONNECTION_DEFAULT_TYPE);
        new ConnectionImpl(node, NodeImpl.CONNECTION_DEFAULT_TYPE, beforeNode, NodeImpl.CONNECTION_DEFAULT_TYPE);
    }

    private NodeImpl getNodeByName(WorkflowProcess process, String nodeName) {
        return (NodeImpl) Arrays.asList(process.getNodes()).stream().filter(n -> nodeName.equals(n.getName())).findAny()
                .orElse(null);
    }
}
