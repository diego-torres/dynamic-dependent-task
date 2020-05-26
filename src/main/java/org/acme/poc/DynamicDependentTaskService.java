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

public class DynamicDependentTaskService implements KieServerApplicationComponentsService {
    private final String OWNER_EXTENSION = JbpmKieServerExtension.EXTENSION_NAME;

    private ProcessService processService;
    private KieServerRegistry context;

    @Override
    public Collection<Object> getAppComponents(String extension, SupportedTransports type, Object... services) {
        if(!OWNER_EXTENSION.equals(extension)) {
            return Collections.emptyList();
        }

        for(Object o : services) {
            if(ProcessService.class.isAssignableFrom(o.getClass())) {
                processService = (ProcessService) o;
                continue;
            }

            if( KieServerRegistry.class.isAssignableFrom(o.getClass()) ) {
                context = (KieServerRegistry) o;
                continue;
            }
        }

        List<Object> components = new ArrayList<Object>(1);
        if(SupportedTransports.REST.equals(type)) {
            components.add(new DynamicDependentTaskResource(processService, context));
        }

        return components;
    }
    
}