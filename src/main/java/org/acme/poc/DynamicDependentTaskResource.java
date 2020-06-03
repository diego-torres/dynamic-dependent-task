package org.acme.poc;

import static org.jbpm.process.svg.processor.SVGProcessor.ACTIVE_BORDER_COLOR;
import static org.jbpm.process.svg.processor.SVGProcessor.COMPLETED_BORDER_COLOR;
import static org.jbpm.process.svg.processor.SVGProcessor.COMPLETED_COLOR;
import static org.kie.server.api.rest.RestURI.CONTAINER_ID;
import static org.kie.server.api.rest.RestURI.PROCESS_INST_ID;
import static org.kie.server.api.rest.RestURI.SVG_NODE_ACTIVE_COLOR;
import static org.kie.server.api.rest.RestURI.SVG_NODE_COMPLETED_BORDER_COLOR;
import static org.kie.server.api.rest.RestURI.SVG_NODE_COMPLETED_COLOR;
import static org.kie.server.remote.rest.common.util.RestUtils.buildConversationIdHeader;
import static org.kie.server.remote.rest.common.util.RestUtils.createResponse;
import static org.kie.server.remote.rest.common.util.RestUtils.errorMessage;
import static org.kie.server.remote.rest.common.util.RestUtils.getVariant;
import static org.kie.server.remote.rest.common.util.RestUtils.internalServerError;
import static org.kie.server.remote.rest.common.util.RestUtils.notFound;

import java.net.URLDecoder;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Variant;

import org.acme.poc.model.DynamicDependentTaskRequest;
import org.drools.persistence.api.TransactionManager;
import org.drools.persistence.api.TransactionManagerFactory;
import org.jbpm.process.core.impl.XmlProcessDumper;
import org.jbpm.process.core.impl.XmlProcessDumperFactory;
import org.jbpm.process.instance.impl.ProcessInstanceImpl;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.services.api.ProcessInstanceNotFoundException;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.service.ServiceRegistry;
import org.jbpm.workflow.core.WorkflowProcess;
import org.jbpm.workflow.core.impl.ConnectionImpl;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.jbpm.workflow.core.node.HumanTaskNode;
import org.kie.api.KieBase;
import org.kie.api.definition.process.Connection;
import org.kie.server.remote.rest.common.Header;
import org.kie.server.services.api.KieServerRegistry;
import org.kie.server.services.impl.marshal.MarshallerHelper;
import org.kie.server.services.jbpm.ui.ImageServiceBase;
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

    private KieBase kbase;
    private KieServerRegistry context;
	private ProcessService processService;
	private ImageServiceBase imageService;

	private MarshallerHelper marshallerHelper;

	public DynamicDependentTaskResource(ProcessService processService, ImageServiceBase imageService,
			KieServerRegistry context) {

		this.processService = processService;
		this.imageService = imageService;
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

        TransactionManager txm = null;
        boolean transactionOwner = false;

        try {
            txm = TransactionManagerFactory.get().newTransactionManager();
            transactionOwner = txm.begin();

            if (processService == null) {
                ServiceRegistry registry = ServiceRegistry.get();
                processService = (ProcessService) registry.service(ServiceRegistry.PROCESS_SERVICE);
            }

            // Retrieve the process instance to be modified (casted to ProcessInstanceImpl,
            // which has the methods to update the process)
            ProcessInstanceImpl processInstance = (ProcessInstanceImpl) processService
                    .getProcessInstance(processInstanceId);

            if (processInstance == null) {
                throw new IllegalArgumentException("Process Instance id not found: " + processInstanceId);
            }

            // Because I am not sure that processInstance.getProcess() retrieved the
            // instance process xml or the kbase xml, then I rather
            // create a copy of the process xml and make sure to don't modify a process
            // retrieved from the kbase. This way I ensure that
            // I am modifying a copy of the process and assigning that copy to the process
            // instance, avoiding kbase process modificaiton by mistake.
            XmlProcessDumper dumper = XmlProcessDumperFactory.newXmlProcessDumperFactory();
            String processXml = dumper.dumpProcess(processInstance.getProcess());
            RuleFlowProcess process = (RuleFlowProcess) dumper.readProcess(processXml);
            insertNodeInBetween(process, request.getBeforeTask(), request.getAfterTask(), request.getTaskName());

            processInstance.updateProcess(process);

            processService.setProcessVariable(processInstanceId, "ModifiedSVG", new Boolean(true));

            txm.commit(transactionOwner);
        } catch (Throwable t) {
            if (txm != null) {
                txm.rollback(transactionOwner);
            }
            logger.error("Unable to insert dynamic task", t);
            return "NOK";
        }

        return "OK";
    }

    @ApiOperation(value = "Returns an annotated SVG image file of a specified process instance diagram.", response = String.class, code = 200)
	@ApiResponses(value = { @ApiResponse(code = 500, message = "Unexpected error"),
			@ApiResponse(code = 404, message = "Process instance, image or Container Id not found") })
	@GET
	@Path("image/{containerId}/{processInstanceId}")
	@Produces({ MediaType.APPLICATION_SVG_XML })
	public Response getProcessInstanceImage(@javax.ws.rs.core.Context HttpHeaders headers,
			@ApiParam(value = "container id that process instance belongs to", required = true, example = "evaluation_1.0.0-SNAPSHOT") @PathParam(CONTAINER_ID) String containerId,
			@ApiParam(value = "identifier of the process instance that image should be loaded for", required = true, example = "123") @PathParam(PROCESS_INST_ID) Long procInstId,
			@ApiParam(value = "svg completed node color", required = false, example = COMPLETED_COLOR) @QueryParam(SVG_NODE_COMPLETED_COLOR) @DefaultValue(COMPLETED_COLOR) String svgNodeCompletedColor,
			@ApiParam(value = "svg completed node border color", required = false, example = COMPLETED_BORDER_COLOR) @QueryParam(SVG_NODE_COMPLETED_BORDER_COLOR) @DefaultValue(COMPLETED_BORDER_COLOR) String svgNodeCompletedBorderColor,
			@ApiParam(value = "svg active node border color", required = false, example = ACTIVE_BORDER_COLOR) @QueryParam(SVG_NODE_ACTIVE_COLOR) @DefaultValue(ACTIVE_BORDER_COLOR) String svgActiveNodeBorderColor) {
		Variant v = getVariant(headers);
		Header conversationIdHeader = buildConversationIdHeader(containerId, context, headers);
		try {
			String svgString = imageService.getActiveProcessImage(containerId, procInstId,
					(COMPLETED_COLOR.equals(svgNodeCompletedColor) ? COMPLETED_COLOR
							: URLDecoder.decode(svgNodeCompletedColor, "UTF-8")),
					(COMPLETED_BORDER_COLOR.equals(svgNodeCompletedBorderColor) ? COMPLETED_BORDER_COLOR
							: URLDecoder.decode(svgNodeCompletedBorderColor, "UTF-8")),
					(ACTIVE_BORDER_COLOR.equals(svgActiveNodeBorderColor) ? ACTIVE_BORDER_COLOR
							: URLDecoder.decode(svgActiveNodeBorderColor, "UTF-8")));

			if (processService == null) {
				ServiceRegistry serviceRegistry = ServiceRegistry.get();
				processService = (ProcessService) serviceRegistry.service(ServiceRegistry.PROCESS_SERVICE);
			}

			Boolean modifiedSvg = processService.getProcessInstanceVariable(procInstId, "ModifiedSvg") == null ? false
					: (Boolean) processService.getProcessInstanceVariable(procInstId, "ModifiedSvg");

			if (modifiedSvg) {
				// TODO: Locate where the dynamic task was added and modify the SVG
				String modifiedSvgText = "<text x=\"523\" y=\"228\" font-size=\"12pt\"  text-decoration=\"normal\" text-anchor=\"middle\" fill=\"red\" font-family=\"Open Sans\" dominant-baseline=\"alphabetic\" font-style=\"normal\" stroke=\"none\" font-weight=\"normal\" >Modified SVG by Dynamic Task A.1</text></svg>";
				
				svgString = svgString.substring(0, svgString.length()-6);
				svgString = svgString + modifiedSvgText;
			}

			logger.debug("Returning OK response with content '{}'", svgString);
			return createResponse(svgString, v, Response.Status.OK, conversationIdHeader);
		} catch (ProcessInstanceNotFoundException e) {
			return notFound(MessageFormat.format("Not found", e.getMessage()), v, conversationIdHeader);
		} catch (IllegalArgumentException e) {
			return notFound("Image for process instance id " + procInstId + " not found", v, conversationIdHeader);
		} catch (Exception e) {
			logger.error("Unexpected error during processing {}", e.getMessage(), e);
			return internalServerError(errorMessage(e), v, conversationIdHeader);
		}
	}

    private void insertNodeInBetween(RuleFlowProcess process, String beforeNodeName, String afterNodeName,
            String newNodeName) {
        // TODO: Use ids instead of names?
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
        // TODO: Ensure uniqueness
        node.setId(process.getNodes().length + 1);

        Map<String, Object> dataInputs = new HashMap<>();
        dataInputs.put("Skippable", "false");
        dataInputs.put("TaskName", "TaskA1");
        dataInputs.put("GroupId", "users");

        // TODO: Ensure uniqueness
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
        // TODO: Provide a method getNodeById
        return (NodeImpl) Arrays.asList(process.getNodes()).stream().filter(n -> nodeName.equals(n.getName())).findAny()
                .orElse(null);
    }

    /**
     * @return KieServerRegistry return the context
     */
    public KieServerRegistry getContext() {
        return context;
    }

    /**
     * @param context the context to set
     */
    public void setContext(KieServerRegistry context) {
        this.context = context;
    }

    /**
     * @return ProcessService return the processService
     */
    public ProcessService getProcessService() {
        return processService;
    }

    /**
     * @param processService the processService to set
     */
    public void setProcessService(ProcessService processService) {
        this.processService = processService;
    }

    /**
     * @return KieBase return the kbase
     */
    public KieBase getKbase() {
        return kbase;
    }

    /**
     * @param kbase the kbase to set
     */
    public void setKbase(KieBase kbase) {
        this.kbase = kbase;
    }

    /**
     * @return MarshallerHelper return the marshallerHelper
     */
    public MarshallerHelper getMarshallerHelper() {
        return marshallerHelper;
    }

    /**
     * @param marshallerHelper the marshallerHelper to set
     */
    public void setMarshallerHelper(MarshallerHelper marshallerHelper) {
        this.marshallerHelper = marshallerHelper;
    }

}
