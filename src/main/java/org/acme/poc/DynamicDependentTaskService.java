package org.acme.poc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jbpm.services.api.ProcessService;
import org.kie.server.services.api.KieServerApplicationComponentsService;
import org.kie.server.services.api.KieServerRegistry;
import org.kie.server.services.api.SupportedTransports;
import org.kie.server.services.jbpm.JbpmKieServerExtension;
import org.kie.server.services.jbpm.ui.ImageServiceBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicDependentTaskService implements KieServerApplicationComponentsService {
    private static final Logger logger = LoggerFactory.getLogger(DynamicDependentTaskService.class);
    private final String OWNER_EXTENSION = JbpmKieServerExtension.EXTENSION_NAME;

    @Override
    public Collection<Object> getAppComponents(String extension, SupportedTransports type, Object... services) {
        if(!OWNER_EXTENSION.equals(extension)) {
            return Collections.emptyList();
        }

        ProcessService processService = null;
		KieServerRegistry context = null;
		ImageServiceBase imageService = null;

		logger.info("***** Loading Dynamic Dependent Task Service Extension *****");
		for (Object object : services) {
			if (ProcessService.class.isAssignableFrom(object.getClass())) {
				logger.info("SETTING PROCESS SERVER");
				processService = (ProcessService) object;
				continue;
			}
			
			if (KieServerRegistry.class.isAssignableFrom(object.getClass())) {
				context = (KieServerRegistry) object;
				continue;
			}
			
			if (ImageServiceBase.class.isAssignableFrom(object.getClass())) {
				imageService = (ImageServiceBase) object;
				continue;
			}
			
		}

		List<Object> components = new ArrayList<Object>(1);
		if (SupportedTransports.REST.equals(type)) {
			logger.debug("Adding Dynamic Task service resource endpoints ...");
			components.add(new DynamicDependentTaskResource(processService, imageService, context));
		}

		return components;
    }
    
}